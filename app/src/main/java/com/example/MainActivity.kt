package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.configureAppCheck
import com.example.ui.DuboraViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.screens.AiDubbingScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MoviePlayerScreen
import com.example.ui.screens.MyMoviesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DuboraTheme
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.LuminousIris
import com.example.ui.theme.ObsidianBlack
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

enum class DuboraTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    MOVIES("movies", "My Movies", Icons.Rounded.Movie, Icons.Outlined.Movie),
    DUBBING("dubbing", "AI Dubbing", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq),
    DOWNLOADS("downloads", "Downloads", Icons.Rounded.DownloadDone, Icons.Outlined.DownloadDone),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Firebase App Check wiring with launch Intent before Compose UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            DuboraTheme {
                DuboraRoot()
            }
        }
    }
}

@Composable
fun DuboraRoot() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            DuboraAuthenticatedShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "Cinema Listener",
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@Composable
fun DuboraAuthenticatedShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: DuboraViewModel = viewModel(
        key = currentUserId,
        factory = remember(context, currentUserId) {
            DuboraViewModel.Factory(
                context = context,
                databaseId = context.getString(R.string.firestore_database_id),
                currentUserId = currentUserId
            )
        }
    )

    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val downloadedDubs by viewModel.downloadedDubs.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val runtimeState by viewModel.dubbingRuntime.collectAsStateWithLifecycle()
    val cloudHistoryState by viewModel.cloudHistoryState.collectAsStateWithLifecycle()
    val activeMovie by viewModel.activeMovie.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val studioProbe by viewModel.studioProbe.collectAsStateWithLifecycle()
    val apiHealth by viewModel.apiHealth.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(DuboraTab.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importMovieUri(uri, openImmediately = true)
        }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissBanner()
        }
    }

    val openMoviePicker = {
        videoPickerLauncher.launch(
            arrayOf("video/mp4", "video/quicktime", "video/x-matroska", "video/*")
        )
    }

    val movieBeingPlayed = activeMovie
    if (movieBeingPlayed != null) {
        MoviePlayerScreen(
            movie = movieBeingPlayed,
            config = config,
            runtimeState = runtimeState,
            onAttachPlayer = viewModel::attachPlayer,
            onUpdateConfig = viewModel::updateConfig,
            onStartDubbing = {
                viewModel.startLiveDubbingForActiveMovie(
                    forceRestart = true,
                    clearPreviousCache = true
                )
            },
            onRetryDubbing = viewModel::retryLiveDubbing,
            onSwitchToFastStream = viewModel::switchToFastStreamAndRetry,
            onStopDubbing = viewModel::stopLiveDubbing,
            onSwitchAudioTrack = viewModel::switchAudioTrack,
            onSetMasterVolume = viewModel::setPlayerMasterVolume,
            onSavePosition = viewModel::savePositionOnly,
            onClosePlayer = viewModel::closePlayer
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = ObsidianBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = CharcoalSurface,
                        contentColor = InkPrimary
                    ) {
                        DuboraTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = InkPrimary,
                                    selectedTextColor = LuminousIris,
                                    indicatorColor = ElectricViolet.copy(alpha = 0.25f),
                                    unselectedIconColor = InkSecondary,
                                    unselectedTextColor = InkSecondary
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = CharcoalSurface,
                        contentColor = InkPrimary
                    ) {
                        DuboraTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = InkPrimary,
                                    selectedTextColor = LuminousIris,
                                    indicatorColor = CharcoalCard,
                                    unselectedIconColor = InkSecondary,
                                    unselectedTextColor = InkSecondary
                                )
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val contentModifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp)

                    when (currentTab) {
                        DuboraTab.HOME -> HomeScreen(
                            userName = userDisplayName.substringBefore(' '),
                            movies = movies,
                            config = config,
                            runtimeState = runtimeState,
                            cloudHistoryState = cloudHistoryState,
                            onOpenMoviePicker = openMoviePicker,
                            onPlayMovie = viewModel::openMovie,
                            onSelectTargetLanguage = { code ->
                                viewModel.updateConfig { it.copy(targetLangCode = code) }
                            },
                            onNavigateToDubbing = { currentTab = DuboraTab.DUBBING },
                            onNavigateToMovies = { currentTab = DuboraTab.MOVIES },
                            modifier = contentModifier
                        )

                        DuboraTab.MOVIES -> MyMoviesScreen(
                            movies = movies,
                            onImportMovie = openMoviePicker,
                            onPlayMovie = viewModel::openMovie,
                            onRemoveMovie = viewModel::removeMovie,
                            modifier = contentModifier
                        )

                        DuboraTab.DUBBING -> AiDubbingScreen(
                            config = config,
                            runtimeState = runtimeState,
                            bridgeStatus = viewModel.bridgeCapabilities,
                            apiHealth = apiHealth,
                            probeState = studioProbe,
                            onUpdateConfig = viewModel::updateConfig,
                            onRunProbe = viewModel::testGeminiDialogueCue,
                            onRetryProbe = viewModel::retryLastStudioProbe,
                            onRetryLiveDubbing = viewModel::retryLiveDubbing,
                            onRefreshHealthCheck = viewModel::refreshApiHealthCheck,
                            onPlayProbeAudio = viewModel::playStudioProbeAudio,
                            modifier = contentModifier
                        )

                        DuboraTab.DOWNLOADS -> DownloadsScreen(
                            downloadedDubs = downloadedDubs,
                            onPlayMovie = viewModel::openMovie,
                            onClearDubCache = viewModel::clearDownloadedDub,
                            modifier = contentModifier
                        )

                        DuboraTab.SETTINGS -> SettingsScreen(
                            userDisplayName = userDisplayName,
                            userEmail = userEmail,
                            config = config,
                            onUpdateConfig = viewModel::updateConfig,
                            onClearHistory = viewModel::clearAllHistoryAndCache,
                            onSignOut = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = contentModifier
                        )
                    }
                }
            }
        }
    }
}
