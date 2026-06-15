package com.rajasudhan.taskmind

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.rajasudhan.taskmind.ui.theme.TaskMindTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            TaskMindTheme {
                var isAuthenticated by remember { mutableStateOf(false) }

                // Re-lock whenever the app leaves the foreground, so auth is required on every return.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) isAuthenticated = false
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
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "App is locked.", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onUnlockClick) {
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
                    IconButton(onClick = onLock) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock app")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Inbox, contentDescription = "Inbox") },
                    label = { Text("Inbox") },
                    selected = currentRoute == "inbox",
                    onClick = {
                        navController.navigate("inbox") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Note, contentDescription = "Notes") },
                    label = { Text("Notes") },
                    selected = currentRoute == "notes",
                    onClick = {
                        navController.navigate("notes") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Source, contentDescription = "Sources") },
                    label = { Text("Sources") },
                    selected = currentRoute == "sources",
                    onClick = {
                        navController.navigate("sources") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "inbox",
            modifier = Modifier.padding(innerPadding)
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
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
