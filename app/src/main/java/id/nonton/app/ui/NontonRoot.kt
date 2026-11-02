package id.nonton.app.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import id.nonton.app.R
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
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
