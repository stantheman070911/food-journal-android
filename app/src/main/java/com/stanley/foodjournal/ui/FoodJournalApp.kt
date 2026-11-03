package com.stanley.foodjournal.ui

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.stanley.foodjournal.ui.home.HomeScreen
import com.stanley.foodjournal.ui.meal.MealScreen
import com.stanley.foodjournal.ui.navigation.HomeRoute
import com.stanley.foodjournal.ui.navigation.MealRoute
import com.stanley.foodjournal.ui.navigation.SearchRoute

private const val SCREEN_MOTION_MILLIS = 220

/** The tabs, with the Meal screen rising over them. */
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
                    onOpenMeal = { mealId ->
                        // As dropUnlessResumed does: a second tile tapped while one opens is ignored.
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) {
                            navController.navigate(MealRoute(mealId)) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable<MealRoute> {
                MealScreen(
                    onClose = dropUnlessResumed { navController.popBackStack() },
                    onOpenPlace = { placeId -> searchPlaceId = placeId },
                )
            }
        }
    }
}
