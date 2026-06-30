package com.rajasudhan.taskmind

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.rajasudhan.taskmind.ui.bold.BoldBottomNav
import com.rajasudhan.taskmind.ui.guide.GuideOverlay
import com.rajasudhan.taskmind.ui.guide.GuideViewModel
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import com.rajasudhan.taskmind.ui.theme.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import com.rajasudhan.taskmind.data.source.SettingsManager
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by settingsManager.themeModeFlow.collectAsState()
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Keep the system-bar icons legible against the chosen theme, even when it differs from
            // the OS setting (e.g. forced dark while the device itself is in light mode).
            val view = LocalView.current
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            TaskMindTheme(darkTheme = darkTheme) {
                // The app lock is optional (Settings → Security). When off, the app opens straight to
                // its content; the database stays encrypted at rest regardless.
                val lockEnabled by settingsManager.appLockEnabledFlow.collectAsState()
                var isAuthenticated by remember { mutableStateOf(false) }

                // A lock is only enforceable if the device actually has a biometric or a device
                // credential (PIN/pattern/password) enrolled. Without one, promptBiometric can never
                // succeed — so we must NOT lock, or the user would be permanently shut out of their
                // own data with the off-switch stuck behind the lock.
                //
                // Re-checked on every ON_RESUME (see the observer below) rather than frozen at launch:
                // the user can enroll or remove a credential in system settings while we're
                // backgrounded. A stale value would either leave the lock silently unenforceable
                // (credential added after launch) or strand the user on a lock screen that can never
                // succeed (credential removed after launch).
                var canLock by remember { mutableStateOf(canEnforceLock()) }

                // Re-lock whenever the app leaves the foreground, so auth is required on every return.
                // Only while the lock is enabled (and enforceable) — otherwise leave the session
                // authenticated so flipping the lock ON later doesn't instantly lock the current one.
                // Exception: a deliberate SAF/document-picker round-trip (flagged via AppLock) also
                // fires ON_STOP — re-locking there would tear down the picker's result callback and
                // drop the write, so that one background is allowed to stay unlocked.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP ->
                                if (lockEnabled && canLock && !AppLock.shouldKeepUnlockedOnStop()) isAuthenticated = false
                            Lifecycle.Event.ON_RESUME -> {
                                AppLock.reset()
                                // Pick up any credential the user enrolled or removed while away.
                                canLock = canEnforceLock()
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // "In" = authenticated, OR the lock is turned off, OR no authenticator exists to enforce it.
                val unlocked = isAuthenticated || !lockEnabled || !canLock

                // While we're in with the lock off, mark the session authenticated, so turning the
                // lock ON from Settings doesn't instantly lock the current session — it takes effect
                // on the next background/return instead.
                LaunchedEffect(unlocked, lockEnabled) {
                    if (unlocked && !lockEnabled) isAuthenticated = true
                }

                // Start the live-watcher foreground service once we're in, however we got there
                // (biometric unlock or lock disabled). Re-starting an already-running service is safe.
                LaunchedEffect(unlocked) {
                    if (unlocked) {
                        // startForegroundService throws ForegroundServiceStartNotAllowedException on
                        // Android 12+ if it lands while the app isn't in a valid foreground state.
                        // Both ways we reach this — a cold launch and a biometric unlock — are valid
                        // foreground moments, so this guard is belt-and-suspenders against a crash
                        // rather than something expected to fire.
                        runCatching {
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                android.content.Intent(this@MainActivity, com.rajasudhan.taskmind.data.source.TaskMindForegroundService::class.java)
                            )
                        }
                    }
                }

                if (!unlocked) {
                    LockScreen(onUnlockClick = { promptBiometric { isAuthenticated = true } })
                    // Trigger authentication immediately on launch.
                    LaunchedEffect(Unit) { promptBiometric { isAuthenticated = true } }
                } else {
                    // The manual-lock action only makes sense when the lock is on AND enforceable.
                    TaskMindAppContent(
                        onLock = if (lockEnabled && canLock) ({ isAuthenticated = false }) else null,
                        isDark = darkTheme,
                        // The in-screen header toggle flips between an explicit Light/Dark choice
                        // (it leaves SYSTEM behind, which is what a deliberate tap implies).
                        onToggleTheme = {
                            settingsManager.themeMode = if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                        }
                    )
                }
            }
        }
    }

    /** True only when the device has a biometric or device credential enrolled to authenticate against. */
    private fun canEnforceLock(): Boolean =
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock TaskMind")
            .setSubtitle("Authenticate to access your private data")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@Composable
fun LockScreen(onUnlockClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Gentle "breathing" pulse on the lock emblem.
    val pulse = rememberInfiniteTransition(label = "lockPulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lockScale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Soft blurred aurora blobs give a frosted, premium backdrop without exposing any real content.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-60).dp, y = (-90).dp)
                .size(260.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(scheme.primary.copy(alpha = 0.45f))
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 90.dp)
                .size(240.dp)
                .blur(90.dp)
                .clip(CircleShape)
                .background(scheme.tertiary.copy(alpha = 0.40f))
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(scheme.primaryContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = "TaskMind is locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your data stays private behind biometrics.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onUnlockClick, modifier = Modifier.height(52.dp)) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Unlock")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskMindAppContent(
    onLock: (() -> Unit)?,
    isDark: Boolean = true,
    onToggleTheme: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val guideViewModel: GuideViewModel = hiltViewModel()
    val showGuide by guideViewModel.showGuide.collectAsState()

    val isNoteDetail = currentRoute?.startsWith("notes/") == true

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                // Every main tab renders its own in-screen serif header now, so the top bar carries no
                // title there (only the note-detail sub-screen keeps a bar title alongside its back arrow).
                title = { if (isNoteDetail) Text("Note") },
                navigationIcon = {
                    if (isNoteDetail) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { guideViewModel.open() }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "How to use TaskMind")
                    }
                    if (onLock != null) {
                        IconButton(onClick = onLock) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock app")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        bottomBar = {
            BoldBottomNav(
                currentRoute = currentRoute,
                onSelect = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "inbox",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(220)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(220))
            },
            exitTransition = {
                fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(180))
            },
            popEnterTransition = {
                fadeIn(tween(220)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220))
            },
            popExitTransition = {
                fadeOut(tween(180)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(180))
            }
        ) {
            composable("inbox") {
                com.rajasudhan.taskmind.ui.inbox.InboxScreen(isDark = isDark, onToggleTheme = onToggleTheme)
            }
            composable("notes") {
                com.rajasudhan.taskmind.ui.notes.NotesScreen(
                    onNoteClick = { id -> navController.navigate("notes/$id") { launchSingleTop = true } }
                )
            }
            composable(
                route = "notes/{noteId}",
                arguments = listOf(navArgument("noteId") { type = NavType.IntType })
            ) {
                com.rajasudhan.taskmind.ui.notes.NoteDetailScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable("sources") { com.rajasudhan.taskmind.ui.sources.SourcesScreen() }
            composable("settings") { com.rajasudhan.taskmind.ui.settings.SettingsScreen() }
        }
    }

    // First-run walkthrough (and re-openable from the "?" action).
    if (showGuide) {
        GuideOverlay(onDismiss = { guideViewModel.dismiss() })
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
