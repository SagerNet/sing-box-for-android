package io.nekohasekai.sfa.compose.screen.profile

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun EditProfileRoute(
    profileId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (profileId == -1L) {
        LaunchedEffect(Unit) {
            onNavigateBack()
        }
        return
    }

    val navController = rememberNavController()
    val sharedViewModel: EditProfileViewModel = viewModel()

    LaunchedEffect(profileId) {
        sharedViewModel.loadProfile(profileId)
    }

    NavHost(
        navController = navController,
        startDestination = "edit_profile",
        modifier = modifier,
    ) {
        composable(
            route = "edit_profile",
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300),
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                )
            },
        ) {
            EditProfileScreen(
                profileId = profileId,
                onNavigateBack = onNavigateBack,
                onNavigateToIconSelection = { currentIconId ->
                    navController.navigate("icon_selection/${currentIconId ?: "null"}") {
                        launchSingleTop = true
                    }
                },
                onNavigateToEditContent = { profileName, isReadOnly ->
                    navController.navigate("edit_content/$profileName/$isReadOnly") {
                        launchSingleTop = true
                    }
                },
                viewModel = sharedViewModel,
            )
        }

        composable(
            route = "icon_selection/{currentIconId}",
            arguments =
                listOf(
                    navArgument("currentIconId") {
                        type = NavType.StringType
                        nullable = true
                    },
                ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300),
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                )
            },
        ) { backStackEntry ->
            val currentIconId =
                backStackEntry.arguments?.getString("currentIconId")
                    ?.takeIf { it != "null" }

            IconSelectionScreen(
                currentIconId = currentIconId,
                onIconSelected = { iconId ->
                    sharedViewModel.updateIcon(iconId)
                    navController.popBackStack("edit_profile", inclusive = false)
                },
                onNavigateBack = {
                    navController.popBackStack("edit_profile", inclusive = false)
                },
            )
        }

        composable(
            route = "edit_content/{profileName}/{isReadOnly}",
            arguments =
                listOf(
                    navArgument("profileName") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("isReadOnly") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300),
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300),
                )
            },
        ) { backStackEntry ->
            val profileName = backStackEntry.arguments?.getString("profileName") ?: ""
            val isReadOnly = backStackEntry.arguments?.getBoolean("isReadOnly") ?: false

            EditProfileContentScreen(
                profileId = profileId,
                onNavigateBack = {
                    navController.popBackStack("edit_profile", inclusive = false)
                },
                profileName = profileName,
                isReadOnly = isReadOnly,
            )
        }
    }
}
