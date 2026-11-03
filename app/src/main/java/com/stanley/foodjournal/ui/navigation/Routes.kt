package com.stanley.foodjournal.ui.navigation

import kotlinx.serialization.Serializable

/** The three tabs, with the bottom bar and Add button. */
@Serializable
data object HomeRoute

/** The Meal screen. A null [mealId] adds a new meal. */
@Serializable
data class MealRoute(val mealId: Long? = null)

@Serializable
data object JournalRoute

@Serializable
data object SpendingRoute

@Serializable
data object SearchRoute
