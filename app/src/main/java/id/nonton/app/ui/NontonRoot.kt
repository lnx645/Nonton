package id.nonton.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.nonton.app.AppContainer
import id.nonton.app.NotificationPermissionEffect
import id.nonton.app.PlayerIntents
import id.nonton.app.R
import id.nonton.app.openPlayer
import id.nonton.app.playback.PlaybackService
import id.nonton.app.ui.components.MiniPlayerBar
import id.nonton.app.ui.home.HomeScreen
import id.nonton.app.ui.home.HomeViewModel
import id.nonton.app.ui.library.LibraryScreen
import id.nonton.app.ui.library.LibraryViewModel
import id.nonton.app.ui.library.PlaylistDetailScreen
import id.nonton.app.ui.library.PlaylistDetailViewModel
import id.nonton.app.ui.search.SearchScreen
import id.nonton.app.ui.search.SearchViewModel
import id.nonton.app.ui.settings.SettingsScreen
import id.nonton.app.ui.settings.SettingsViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{playlistId}"
    fun playlist(id: Long) = "playlist/$id"
}

private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
    BottomDestination(Routes.SEARCH, R.string.nav_search, Icons.Outlined.Search),
    BottomDestination(Routes.LIBRARY, R.string.nav_library, Icons.Outlined.VideoLibrary),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
)

@Composable
fun NontonRoot(container: AppContainer) {
    NotificationPermissionEffect()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || bottomDestinations.any { it.route == currentRoute }

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(container.extractor, container.settings)
    )
    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(container.extractor, container.settings)
    )
    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(container.library)
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(container.settings)
    )

    // Mini-player: pantau sesi playback global agar info lagu/video yang sedang
    // diputar tetap terlihat walau PlayerActivity sudah ditutup (mis. via PiP).
    val appContext = LocalContext.current.applicationContext
    var npController by remember { mutableStateOf<MediaController?>(null) }
    var npHasMedia by remember { mutableStateOf(false) }
    var npPlaying by remember { mutableStateOf(false) }
    var npTitle by remember { mutableStateOf("") }
    var npUploader by remember { mutableStateOf<String?>(null) }
    var npThumbnail by remember { mutableStateOf<String?>(null) }
    var npUrl by remember { mutableStateOf<String?>(null) }
    var npServiceId by remember { mutableStateOf(0) }
    var npProgress by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            { npController = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(appContext),
        )
        onDispose {
            runCatching { MediaController.releaseFuture(future) }
            npController = null
        }
    }

    LaunchedEffect(npController) {
        val mc = npController ?: return@LaunchedEffect
        while (true) {
            val item = if (mc.mediaItemCount > 0) mc.currentMediaItem else null
            val meta = item?.mediaMetadata
            val extras = meta?.extras
            npHasMedia = item != null
            npPlaying = mc.isPlaying
            npTitle = meta?.title?.toString().orEmpty()
            npUploader = meta?.artist?.toString()
                ?: extras?.getString(PlayerIntents.EXTRA_UPLOADER)
            npThumbnail = extras?.getString(PlayerIntents.EXTRA_THUMBNAIL)
            npUrl = extras?.getString(PlayerIntents.EXTRA_URL)
            npServiceId = extras?.getInt(PlayerIntents.EXTRA_SERVICE_ID, 0) ?: 0
            val duration = mc.duration.takeIf { it > 0 } ?: 0L
            npProgress = if (duration > 0) {
                (mc.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            delay(1_000)
        }
    }

    val miniPlayerVisible = npHasMedia && !npUrl.isNullOrBlank()
    val mainContext = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                if (miniPlayerVisible) {
                    MiniPlayerBar(
                        title = npTitle,
                        uploader = npUploader,
                        thumbnailUrl = npThumbnail,
                        isPlaying = npPlaying,
                        progressFraction = npProgress,
                        // Saat bottom nav aplikasi sembunyi (mis. detail playlist),
                        // beri padding agar tidak kepotong nav bar sistem HP.
                        modifier = if (showBottomBar) Modifier else Modifier.navigationBarsPadding(),
                        onOpen = {
                            val url = npUrl ?: return@MiniPlayerBar
                            mainContext.openPlayer(
                                VideoUiModel(
                                    url = url,
                                    title = npTitle.ifBlank { "Sedang diputar" },
                                    uploader = npUploader,
                                    thumbnailUrl = npThumbnail,
                                    serviceId = npServiceId,
                                    durationSeconds = 0,
                                ),
                                npController?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                            )
                        },
                        onTogglePlay = {
                            npController?.let { mc ->
                                if (mc.isPlaying) mc.pause() else mc.play()
                            }
                        },
                        onClose = {
                            npController?.let { mc ->
                                mc.stop()
                                mc.clearMediaItems()
                            }
                        },
                    )
                }
                if (showBottomBar) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                    ) {
                        bottomDestinations.forEach { destination ->
                            val selected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (!selected) {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = stringResource(destination.labelRes),
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                    }
                }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onOpenSearch = {
                        navController.navigate(Routes.SEARCH) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(viewModel = searchViewModel)
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onOpenPlaylist = { id -> navController.navigate(Routes.playlist(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(viewModel = settingsViewModel)
            }
            composable(
                route = Routes.PLAYLIST,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) { entry ->
                val playlistId = entry.arguments?.getLong("playlistId") ?: 0L
                val playlistViewModel: PlaylistDetailViewModel = viewModel(
                    key = "playlist-$playlistId",
                    factory = PlaylistDetailViewModel.factory(container.library, playlistId),
                )
                PlaylistDetailScreen(
                    viewModel = playlistViewModel,
                    onBack = { navController.popBackStack() },
                    onDeleted = { navController.popBackStack() },
                )
            }
        }
    }
}
