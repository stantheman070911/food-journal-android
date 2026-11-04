package com.stanley.foodjournal.ui

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stanley.foodjournal.ui.home.HomeScreen
import com.stanley.foodjournal.ui.meal.MealScreen
import com.stanley.foodjournal.ui.navigation.HomeRoute
import com.stanley.foodjournal.ui.navigation.LicensesRoute
import com.stanley.foodjournal.ui.navigation.MealRoute
import com.stanley.foodjournal.ui.navigation.SearchRoute
import com.stanley.foodjournal.ui.navigation.SettingsRoute
import com.stanley.foodjournal.ui.settings.LicensesScreen
import com.stanley.foodjournal.ui.settings.SettingsScreen

private const val SCREEN_MOTION_MILLIS = 220
private const val STEP_FADE_MILLIS = 150

/** The tabs, with the Meal and Settings screens rising over them. */
@Composable
fun FoodJournalApp() {
    val navController = rememberNavController()
    // A place picked from the Meal screen's "Meals here", for the tabs to open in Search. Saved,
    // so a rotation between closing the Meal screen and opening Search can't lose it.
    var searchPlaceId by rememberSaveable { mutableStateOf<Long?>(null) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = {
                fadeIn(tween(SCREEN_MOTION_MILLIS)) +
                    slideInVertically(tween(SCREEN_MOTION_MILLIS)) { height -> height / 12 }
            },
            exitTransition = { fadeOut(tween(SCREEN_MOTION_MILLIS)) },
            popEnterTransition = { fadeIn(tween(SCREEN_MOTION_MILLIS)) },
            popExitTransition = {
                fadeOut(tween(SCREEN_MOTION_MILLIS)) +
                    slideOutVertically(tween(SCREEN_MOTION_MILLIS)) { height -> height / 12 }
            },
        ) {
            composable<HomeRoute> { entry ->
                HomeScreen(
                    searchRequest = searchPlaceId?.let { SearchRoute(placeId = it) },
                    onSearchRequestHandled = { searchPlaceId = null },
                    onAddMeal = dropUnlessResumed {
                        navController.navigate(MealRoute()) { launchSingleTop = true }
                    },
                    onOpenSettings = dropUnlessResumed {
                        navController.navigate(SettingsRoute) { launchSingleTop = true }
                    },
                    onOpenMeal = { mealId ->
                        // As dropUnlessResumed does: a second tile tapped while one opens is ignored.
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            navController.navigate(MealRoute(mealId)) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable<MealRoute>(
                // A step to another meal crossfades in place, without the usual rise.
                enterTransition = { if (initialState.destination.hasRoute<MealRoute>()) fadeIn(tween(STEP_FADE_MILLIS)) else null },
                exitTransition = {
                    if (targetState.destination.hasRoute<MealRoute>()) ExitTransition.KeepUntilTransitionsFinished else null
                },
            ) { entry ->
                MealScreen(
                    // Not dropped while the screen still opens: its ViewModel has already closed
                    // and asks only once, so a dropped request would leave it stuck.
                    onClose = {
                        if (navController.currentBackStackEntry === entry) navController.popBackStack()
                    },
                    onOpenPlace = { placeId -> searchPlaceId = placeId },
                    // In this one's place, so Back still returns to wherever the first meal opened from.
                    onStepTo = { mealId ->
                        if (navController.currentBackStackEntry === entry) {
                            navController.navigate(MealRoute(mealId)) {
                                popUpTo<MealRoute> { inclusive = true }
                            }
                        }
                    },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = dropUnlessResumed { navController.popBackStack() },
                    onOpenLicenses = dropUnlessResumed {
                        navController.navigate(LicensesRoute) { launchSingleTop = true }
                    },
                    // The tabs start afresh: a search or period kept from before could name a
                    // place that now means another.
                    onRestored = {
                        navController.navigate(HomeRoute) {
                            popUpTo<HomeRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<LicensesRoute> {
                LicensesScreen(onBack = dropUnlessResumed { navController.popBackStack() })
            }
        }
    }
}
