package ir.weirdnet.client.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.weirdnet.client.core.DeepLinkRepository
import ir.weirdnet.client.ui.about.AboutScreen
import ir.weirdnet.client.ui.addprofile.AddProfileScreen
import ir.weirdnet.client.ui.diagnostics.DiagnosticsScreen
import ir.weirdnet.client.ui.home.HomeScreen
import ir.weirdnet.client.ui.profiles.ProfilesScreen
import ir.weirdnet.client.ui.settings.SettingsScreen

sealed class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Destination("home", "Home", Icons.Filled.Home)
    data object Profiles : Destination("profiles", "Profiles", Icons.Filled.VpnKey)
    data object Diagnostics : Destination("diagnostics", "Diagnostics", Icons.Filled.MonitorHeart)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)
}

private val bottomNavDestinations = listOf(Destination.Home, Destination.Profiles, Destination.Diagnostics, Destination.Settings)
private const val ROUTE_ADD_PROFILE = "add_profile"
private const val ROUTE_ABOUT = "about"

@Composable
fun WeirdNetNavHost() {
    val navController = rememberNavController()

    // A tapped vless://... link (or an opened .conf file) can arrive at any time
    // -- cold start, or while some other screen is already showing -- and
    // MainActivity has no reference to this NavController to route it directly.
    // Instead it deposits the payload in DeepLinkRepository, and this single
    // observer jumps to Add Profile whenever a new one shows up; AddProfileScreen
    // itself (see that file) is what actually consumes and parses the payload.
    val pendingDeepLink by DeepLinkRepository.pending.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        if (pendingDeepLink != null) {
            navController.navigate(ROUTE_ADD_PROFILE) { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = { WeirdNetBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            composable(Destination.Home.route) {
                HomeScreen(
                    onAddProfileClick = { navController.navigate(ROUTE_ADD_PROFILE) },
                    onManageProfilesClick = { navController.navigate(Destination.Profiles.route) }
                )
            }
            composable(Destination.Profiles.route) {
                ProfilesScreen(onAddProfileClick = { navController.navigate(ROUTE_ADD_PROFILE) })
            }
            composable(Destination.Diagnostics.route) { DiagnosticsScreen() }
            composable(Destination.Settings.route) {
                SettingsScreen(onAboutClick = { navController.navigate(ROUTE_ABOUT) })
            }
            composable(ROUTE_ADD_PROFILE) {
                AddProfileScreen(
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(ROUTE_ABOUT) { AboutScreen() }
        }
    }
}

@Composable
private fun WeirdNetBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    NavigationBar {
        bottomNavDestinations.forEach { destination ->
            val selected = currentRoute?.hierarchy?.any { it.route == destination.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) }
            )
        }
    }
}
