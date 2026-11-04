package com.stanley.foodjournal.ui.home

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.stanley.foodjournal.R
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.navigation.JournalRoute
import com.stanley.foodjournal.ui.navigation.MapRoute
import com.stanley.foodjournal.ui.navigation.SearchRoute
import com.stanley.foodjournal.ui.navigation.SpendingRoute

/** The tabs in the bottom bar, in order. */
enum class TopLevelDestination(
    val route: Any,
    val icon: ImageVector,
    @param:StringRes val label: Int,
) {
    Journal(JournalRoute, FoodJournalIcons.Journal, R.string.tab_journal),
    Spending(SpendingRoute, FoodJournalIcons.Spending, R.string.tab_spending),
    Map(MapRoute, FoodJournalIcons.Map, R.string.tab_map),
    Search(SearchRoute(), FoodJournalIcons.Search, R.string.tab_search),
}
