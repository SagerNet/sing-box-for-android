package io.nekohasekai.sfa.compose.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.nekohasekai.sfa.compose.screen.configuration.NewProfileScreen
import io.nekohasekai.sfa.compose.screen.connections.ConnectionDetailsRoute
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsPage
import io.nekohasekai.sfa.compose.screen.connections.ConnectionsViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardScreen
import io.nekohasekai.sfa.compose.screen.dashboard.DashboardViewModel
import io.nekohasekai.sfa.compose.screen.dashboard.GroupsCard
import io.nekohasekai.sfa.compose.screen.dashboard.groups.GroupsViewModel
import io.nekohasekai.sfa.compose.screen.log.HookLogScreen
import io.nekohasekai.sfa.compose.screen.log.LogScreen
import io.nekohasekai.sfa.compose.screen.log.LogViewModel
import io.nekohasekai.sfa.compose.screen.privilegesettings.PrivilegeSettingsManageScreen
import io.nekohasekai.sfa.compose.screen.profile.EditProfileRoute
import io.nekohasekai.sfa.compose.screen.profileoverride.PerAppProxyScreen
import io.nekohasekai.sfa.compose.screen.settings.AppSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.CoreSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.PrivilegeSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.ProfileOverrideScreen
import io.nekohasekai.sfa.compose.screen.settings.ServiceSettingsScreen
import io.nekohasekai.sfa.compose.screen.settings.SettingsScreen
import io.nekohasekai.sfa.constant.Status

private val slideInFromRight: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
}

private val slideOutToRight: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
}

private val slideInFromLeft: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
}

private val slideOutToLeft: AnimatedContentTransitionScope<*>.() -> androidx.compose.animation.ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
}

@Composable
fun SFANavHost(
    navController: NavHostController,
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    newProfileArgs: NewProfileArgs = NewProfileArgs(),
    onClearNewProfileArgs: () -> Unit = {},
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    dashboardViewModel: DashboardViewModel? = null,
    logViewModel: LogViewModel? = null,
    groupsViewModel: GroupsViewModel? = null,
    connectionsViewModel: ConnectionsViewModel? = null,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier,
    ) {
        composable(Screen.Dashboard.route) {
            if (dashboardViewModel != null) {
                DashboardScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    onOpenNewProfile = onOpenNewProfile,
                    viewModel = dashboardViewModel,
                )
            } else {
                DashboardScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    onOpenNewProfile = onOpenNewProfile,
                )
            }
        }

        composable(Screen.Log.route) {
            if (logViewModel != null) {
                LogScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                    viewModel = logViewModel,
                )
            } else {
                LogScreen(
                    serviceStatus = serviceStatus,
                    showStartFab = showStartFab,
                    showStatusBar = showStatusBar,
                )
            }
        }

        composable(Screen.Groups.route) {
            if (groupsViewModel != null) {
                GroupsCard(
                    serviceStatus = serviceStatus,
                    viewModel = groupsViewModel,
                    showTopBar = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                GroupsCard(
                    serviceStatus = serviceStatus,
                    showTopBar = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(Screen.Connections.route) {
            if (connectionsViewModel != null) {
                ConnectionsPage(
                    serviceStatus = serviceStatus,
                    viewModel = connectionsViewModel,
                    showTitle = false,
                    showTopBar = true,
                    onConnectionClick = { connectionId ->
                        navController.navigate("connections/detail/${Uri.encode(connectionId)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ConnectionsPage(
                    serviceStatus = serviceStatus,
                    showTitle = false,
                    showTopBar = true,
                    onConnectionClick = { connectionId ->
                        navController.navigate("connections/detail/${Uri.encode(connectionId)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(ProfileRoutes.NewProfile) {
            DisposableEffect(Unit) {
                onDispose { onClearNewProfileArgs() }
            }
            NewProfileScreen(
                importName = newProfileArgs.importName,
                importUrl = newProfileArgs.importUrl,
                qrsData = newProfileArgs.qrsData,
                onNavigateBack = {
                    onClearNewProfileArgs()
                    navController.navigateUp()
                },
                onProfileCreated = { profileId ->
                    onClearNewProfileArgs()
                    navController.navigate(ProfileRoutes.editProfile(profileId)) {
                        popUpTo(ProfileRoutes.NewProfile) {
                            inclusive = true
                        }
                    }
                },
            )
        }

        composable(
            route = ProfileRoutes.EditProfile,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                },
            ),
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getLong("profileId") ?: -1L
            EditProfileRoute(
                profileId = profileId,
                onNavigateBack = { navController.navigateUp() },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable("connections/detail/{connectionId}") { backStackEntry ->
            val connectionId = backStackEntry.arguments?.getString("connectionId")
            if (connectionId != null) {
                if (connectionsViewModel != null) {
                    ConnectionDetailsRoute(
                        connectionId = connectionId,
                        serviceStatus = serviceStatus,
                        viewModel = connectionsViewModel,
                        onBack = { navController.navigateUp() },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ConnectionDetailsRoute(
                        connectionId = connectionId,
                        serviceStatus = serviceStatus,
                        onBack = { navController.navigateUp() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        // Settings subscreens with slide animations
        composable(
            route = "settings/app",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            AppSettingsScreen(navController = navController)
        }

        composable(
            route = "settings/core",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToRight,
            popEnterTransition = slideInFromRight,
            popExitTransition = slideOutToRight,
        ) {
            CoreSettingsScreen(navController = navController)
        }

        composable(
            route = "settings/service",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ServiceSettingsScreen(navController = navController)
        }

        composable(
            route = "settings/profile_override",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            ProfileOverrideScreen(navController = navController)
        }

        composable(
            route = "settings/profile_override/manage",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PerAppProxyScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "settings/privilege",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PrivilegeSettingsScreen(navController = navController, serviceStatus = serviceStatus)
        }

        composable(
            route = "settings/privilege/manage",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            PrivilegeSettingsManageScreen(onBack = { navController.navigateUp() })
        }

        composable(
            route = "settings/privilege/logs",
            enterTransition = slideInFromRight,
            exitTransition = slideOutToLeft,
            popEnterTransition = slideInFromLeft,
            popExitTransition = slideOutToRight,
        ) {
            HookLogScreen(onBack = { navController.navigateUp() })
        }
    }
}
