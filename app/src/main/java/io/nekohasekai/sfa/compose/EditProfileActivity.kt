package io.nekohasekai.sfa.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.nekohasekai.sfa.compose.screen.profile.EditProfileContentScreen
import io.nekohasekai.sfa.compose.screen.profile.EditProfileScreen
import io.nekohasekai.sfa.compose.screen.profile.EditProfileViewModel
import io.nekohasekai.sfa.compose.screen.profile.IconSelectionScreen
import io.nekohasekai.sfa.compose.theme.SFATheme

class EditProfileActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val profileId = intent.getLongExtra("profile_id", -1L)
        if (profileId == -1L) {
            finish()
            return
        }

        setContent {
            SFATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()

                    // Create a shared ViewModel at the activity level
                    val sharedViewModel: EditProfileViewModel = viewModel()

                    // Initialize the ViewModel with the profile ID
                    LaunchedEffect(profileId) {
                        sharedViewModel.loadProfile(profileId)
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "edit_profile",
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
                                onNavigateBack = { finish() },
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
                                    // Update the shared ViewModel directly
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
            }
        }
    }
}
