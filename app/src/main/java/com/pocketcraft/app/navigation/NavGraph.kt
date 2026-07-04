package com.pocketcraft.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pocketcraft.app.ui.createserver.CreateServerScreen
import com.pocketcraft.app.ui.home.HomeScreen
import com.pocketcraft.app.ui.serverdetail.ServerDetailScreen
import com.pocketcraft.app.ui.settings.AppSettingsScreen

sealed class Screen(val route: String) {
    data object Home         : Screen("home")
    data object CreateServer : Screen("create_server")
    data object AppSettings  : Screen("app_settings")
    data object ServerDetail : Screen("server/{serverId}") {
        fun createRoute(serverId: String) = "server/$serverId"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onCreateServer  = { navController.navigate(Screen.CreateServer.route) },
                onOpenServer    = { serverId -> navController.navigate(Screen.ServerDetail.createRoute(serverId)) },
                onOpenSettings  = { navController.navigate(Screen.AppSettings.route) }
            )
        }

        composable(Screen.CreateServer.route) {
            CreateServerScreen(
                onNavigateBack = { navController.popBackStack() },
                onServerCreated = { serverId ->
                    navController.popBackStack()
                    navController.navigate(Screen.ServerDetail.createRoute(serverId))
                }
            )
        }

        composable(Screen.AppSettings.route) {
            AppSettingsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.ServerDetail.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString("serverId") ?: return@composable
            ServerDetailScreen(
                serverId = serverId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
