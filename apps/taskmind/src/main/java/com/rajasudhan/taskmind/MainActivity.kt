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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.rajasudhan.taskmind.ui.guide.GuideOverlay
import com.rajasudhan.taskmind.ui.guide.GuideViewModel
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
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
            val dynamicColor by settingsManager.dynamicColorFlow.collectAsState()
            TaskMindTheme(dynamicColor = dynamicColor) {
                var isAuthenticated by remember { mutableStateOf(false) }

                // Re-lock whenever the app leaves the foreground, so auth is required on every return.
                // Exception: a deliberate SAF/document-picker round-trip (flagged via AppLock) also
                // fires ON_STOP — re-locking there would tear down the picker's result callback and
                // drop the write, so that one background is allowed to stay unlocked.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP ->
                                if (!AppLock.shouldKeepUnlockedOnStop()) isAuthenticated = false
                            Lifecycle.Event.ON_RESUME -> AppLock.reset()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                if (!isAuthenticated) {
                    LockScreen(
                        onUnlockClick = {
                            promptBiometric {
                                isAuthenticated = true
                                ContextCompat.startForegroundService(
                                    this@MainActivity,
                                    android.content.Intent(this@MainActivity, com.rajasudhan.taskmind.data.source.TaskMindForegroundService::class.java)
                                )
                            }
                        }
                    )
                    
                    // Trigger authentication immediately on launch
                    LaunchedEffect(Unit) {
                        promptBiometric {
                            isAuthenticated = true
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                android.content.Intent(this@MainActivity, com.rajasudhan.taskmind.data.source.TaskMindForegroundService::class.java)
                            )
                        }
                    }
                } else {
                    TaskMindAppContent(onLock = { isAuthenticated = false })
                }
            }
        }
    }

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
fun TaskMindAppContent(onLock: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val guideViewModel: GuideViewModel = hiltViewModel()
    val showGuide by guideViewModel.showGuide.collectAsState()

    val isNoteDetail = currentRoute?.startsWith("notes/") == true
    val screenTitle = when {
        isNoteDetail -> "Note"
        currentRoute == "notes" -> "Notes"
        currentRoute == "sources" -> "Sources"
        currentRoute == "settings" -> "Settings"
        else -> "Inbox"
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(screenTitle) },
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
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock app")
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
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val navColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                fun go(route: String) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Inbox, contentDescription = "Inbox") },
                    label = { Text("Inbox") },
                    selected = currentRoute == "inbox",
                    colors = navColors,
                    onClick = { go("inbox") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Note, contentDescription = "Notes") },
                    label = { Text("Notes") },
                    selected = currentRoute == "notes",
                    colors = navColors,
                    onClick = { go("notes") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Source, contentDescription = "Sources") },
                    label = { Text("Sources") },
                    selected = currentRoute == "sources",
                    colors = navColors,
                    onClick = { go("sources") }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    colors = navColors,
                    onClick = { go("settings") }
                )
            }
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
            composable("inbox") { com.rajasudhan.taskmind.ui.inbox.InboxScreen() }
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
