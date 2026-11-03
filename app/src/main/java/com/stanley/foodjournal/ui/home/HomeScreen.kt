package com.stanley.foodjournal.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stanley.foodjournal.R
import com.stanley.foodjournal.ui.components.UndoBar
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.journal.JournalScreen
import com.stanley.foodjournal.ui.journal.JournalUiState
import com.stanley.foodjournal.ui.navigation.JournalRoute
import com.stanley.foodjournal.ui.navigation.SearchRoute
import com.stanley.foodjournal.ui.navigation.SpendingRoute
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import com.stanley.foodjournal.ui.search.SearchScreen
import com.stanley.foodjournal.ui.spending.SpendingScreen

private const val TAB_FADE_MILLIS = 150

/**
 * The three tabs, the bottom bar, and the Add button. A [searchRequest], from a screen above the
 * tabs, opens Search with it once the tabs show again.
 */
@Composable
fun HomeScreen(
    onAddMeal: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    searchRequest: SearchRoute? = null,
    onSearchRequestHandled: () -> Unit = {},
) {
    val tabNavController = rememberNavController()
    LaunchedEffect(searchRequest) {
        if (searchRequest != null) {
            tabNavController.openSearch(searchRequest)
            onSearchRequestHandled()
        }
    }
    val backStackEntry by tabNavController.currentBackStackEntryAsState()
    val selected = TopLevelDestination.entries.firstOrNull { tab ->
        backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
    }

    HomeScaffold(
        selected = selected,
        onSelect = tabNavController::navigateToTab,
        onAddMeal = onAddMeal,
        modifier = modifier,
        undoBar = { UndoBar() },
    ) {
        NavHost(
            navController = tabNavController,
            startDestination = JournalRoute,
            enterTransition = { fadeIn(tween(TAB_FADE_MILLIS)) },
            exitTransition = { fadeOut(tween(TAB_FADE_MILLIS)) },
        ) {
            composable<JournalRoute> {
                JournalScreen(onAddMeal = onAddMeal, onOpenMeal = onOpenMeal, onOpenSettings = onOpenSettings)
            }
            composable<SpendingRoute> {
                SpendingScreen(
                    onOpenMeal = onOpenMeal,
                    onOpenPlace = { placeId, from, to ->
                        tabNavController.openSearch(SearchRoute(placeId, from.toEpochDay(), to.toEpochDay()))
                    },
                )
            }
            composable<SearchRoute> { SearchScreen(onAddMeal = onAddMeal, onOpenMeal = onOpenMeal) }
        }
    }
}

@Composable
private fun HomeScaffold(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
    undoBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FoodJournalBottomBar(selected = selected, onSelect = onSelect) },
        floatingActionButton = { AddMealButton(onClick = onAddMeal) },
        snackbarHost = undoBar,
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            content()
        }
    }
}

@Composable
private fun AddMealButton(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 2.dp,
            pressedElevation = 2.dp,
            focusedElevation = 2.dp,
            hoveredElevation = 3.dp,
        ),
    ) {
        Icon(FoodJournalIcons.Add, contentDescription = stringResource(R.string.add_meal))
    }
}

/** Switches tabs the standard way: one copy of each tab, state kept, Back returns to Journal. */
private fun NavController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Opens Search as a fresh search holding only what [route] names. The search it replaces, a
 * Search showing or one the tab saved earlier, is dropped for good; kept, it would come back on
 * the next tab switch in place of this one.
 */
private fun NavController.openSearch(route: SearchRoute) {
    val onSearch = currentDestination?.hasRoute(SearchRoute::class) == true
    clearBackStack<SearchRoute>()
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = !onSearch }
    }
}

@ThemePreviews
@Composable
private fun HomeEmptyPreview() {
    PreviewSurface {
        HomeScaffold(selected = TopLevelDestination.Journal, onSelect = {}, onAddMeal = {}) {
            JournalScreen(uiState = JournalUiState(isLoading = false), onAddMeal = {}, onOpenMeal = {})
        }
    }
}
