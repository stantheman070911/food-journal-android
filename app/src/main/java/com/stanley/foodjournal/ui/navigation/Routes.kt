package com.stanley.foodjournal.ui.navigation

import kotlinx.serialization.Serializable

/** The three tabs, with the bottom bar and Add button. */
@Serializable
data object HomeRoute

/** The Meal screen. A null [mealId] adds a new meal. */
@Serializable
data class MealRoute(val mealId: Long? = null)

/** Settings: theme, backup and restore, and About. */
@Serializable
data object SettingsRoute

/** The open-source licenses, from Settings' About. */
@Serializable
data object LicensesRoute

@Serializable
data object JournalRoute

@Serializable
data object SpendingRoute

/**
 * The Search tab. A [placeId], and a first and last day as epoch days, open a fresh search with
 * just them; they are passed explicitly, so the tab's saved state never brings back an older
 * search in their place.
 */
@Serializable
data class SearchRoute(
    val placeId: Long? = null,
    val fromDay: Long? = null,
    val toDay: Long? = null,
)
