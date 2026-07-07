package com.rajasudhan.taskmind

import android.content.Intent
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

    // A reminder/geofence notification tap deep-links to its note. Held as state (not read once)
    // so a tap that arrives via onNewIntent — the activity was already open — also navigates, and
    // so the navigation naturally waits out the app lock: it only runs once the nav graph composes.
    private val pendingOpenNoteId = kotlinx.coroutines.flow.MutableStateFlow(-1)

    // The Inbox launcher shortcut asks to land on the Inbox tab. Held as state (like the note id above)
    // so it also works via onNewIntent — when the app is already open on another tab — and waits out the
    // app lock. A cold launch already starts on "inbox", so this only matters for an already-live app.
    private val pendingOpenInbox = kotlinx.coroutines.flow.MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch consumes the extra. On recreation (theme flip, Recents relaunch after
        // process death) the system redelivers the same intent — without this guard the app would
        // yank the user back to an already-dismissed note on every recreation.
        if (savedInstanceState == null) {
            pendingOpenNoteId.value = intent?.getIntExtra(EXTRA_OPEN_NOTE_ID, -1) ?: -1
            pendingOpenInbox.value = intent?.getBooleanExtra(EXTRA_OPEN_INBOX, false) ?: false
        }

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
                    val openNoteId by pendingOpenNoteId.collectAsState()
                    val openInbox by pendingOpenInbox.collectAsState()
                    TaskMindAppContent(
                        onLock = if (lockEnabled && canLock) ({ isAuthenticated = false }) else null,
                        isDark = darkTheme,
                        // The in-screen header toggle flips between an explicit Light/Dark choice
                        // (it leaves SYSTEM behind, which is what a deliberate tap implies).
                        onToggleTheme = {
                            settingsManager.themeMode = if (darkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                        },
                        openNoteId = openNoteId,
                        onNoteOpened = {
                            pendingOpenNoteId.value = -1
                            // Also strip the extra from the sticky intent (kept by setIntent), so a
                            // same-process recreation can't re-read it.
                            intent?.removeExtra(EXTRA_OPEN_NOTE_ID)
                        },
                        openInbox = openInbox,
                        onInboxOpened = {
                            pendingOpenInbox.value = false
                            intent?.removeExtra(EXTRA_OPEN_INBOX)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenNoteId.value = intent.getIntExtra(EXTRA_OPEN_NOTE_ID, -1)
        pendingOpenInbox.value = intent.getBooleanExtra(EXTRA_OPEN_INBOX, false)
    }

    companion object {
        /** Int extra: a note id to navigate to on open — set by reminder/geofence notification taps. */
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
        /** Boolean extra: jump to the Inbox tab on open — set by the Inbox launcher shortcut. */
        const val EXTRA_OPEN_INBOX = "open_inbox"
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
    openNoteId: Int = -1,
    onNoteOpened: () -> Unit = {},
    openInbox: Boolean = false,
    onInboxOpened: () -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Deep link from a reminder/geofence notification tap: jump straight to the note. Runs after
    // the first composition, so the NavHost below has already set its graph.
    LaunchedEffect(openNoteId) {
        if (openNoteId >= 0) {
            navController.navigate("notes/$openNoteId") { launchSingleTop = true }
            onNoteOpened()
        }
    }

    // Inbox launcher shortcut: select the Inbox tab. Harmless on a cold launch (Inbox is the start
    // destination); it matters when the app is already open on another tab.
    LaunchedEffect(openInbox) {
        if (openInbox) {
            navController.navigate("inbox") {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onInboxOpened()
        }
    }

    val guideViewModel: GuideViewModel = hiltViewModel()
    val showGuide by guideViewModel.showGuide.collectAsState()

    val isNoteDetail = currentRoute?.startsWith("notes/") == true
    val isSettingsDetail = currentRoute == "settings_all"
    val isReliability = currentRoute == "reliability"
    val isKnows = currentRoute == "privacy_knows"
    val isDetail = isNoteDetail || isSettingsDetail || isReliability || isKnows

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                // Every main tab renders its own in-screen serif header now, so the top bar carries no
                // title there (only the note-detail sub-screen keeps a bar title alongside its back arrow).
                title = {
                    if (isDetail) Text(
                        when {
                            isSettingsDetail -> "Settings"
                            isReliability -> "Reliability"
                            isKnows -> "What TaskMind knows"
                            else -> "Note"
                        }
                    )
                },
                navigationIcon = {
                    if (isDetail) {
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
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
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
            composable("ask") {
                com.rajasudhan.taskmind.ui.ask.AskScreen(
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
                    onNoteClick = { id -> navController.navigate("notes/$id") { launchSingleTop = true } }
                )
            }
            composable("sources") {
                com.rajasudhan.taskmind.ui.sources.SourcesScreen(isDark = isDark, onToggleTheme = onToggleTheme)
            }
            composable("settings") {
                com.rajasudhan.taskmind.ui.settings.PrivacyScreen(
                    isDark = isDark,
                    onToggleTheme = onToggleTheme,
                    onOpenSettings = { navController.navigate("settings_all") { launchSingleTop = true } },
                    onOpenReliability = { navController.navigate("reliability") { launchSingleTop = true } },
                    onOpenKnows = { navController.navigate("privacy_knows") { launchSingleTop = true } }
                )
            }
            composable("settings_all") { com.rajasudhan.taskmind.ui.settings.SettingsScreen() }
            composable("reliability") {
                com.rajasudhan.taskmind.ui.settings.ReliabilityScreen()
            }
            composable("privacy_knows") {
                com.rajasudhan.taskmind.ui.settings.KnowsScreen()
            }
        }
    }

    // First-run walkthrough (and re-openable from the "?" action).
    if (showGuide) {
        GuideOverlay(onDismiss = { guideViewModel.dismiss() }, isOnDevice = guideViewModel.isOnDeviceEngine())
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
