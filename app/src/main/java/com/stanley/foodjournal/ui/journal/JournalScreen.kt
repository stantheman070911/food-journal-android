package com.stanley.foodjournal.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanley.foodjournal.R
import com.stanley.foodjournal.ui.components.EmptyState
import com.stanley.foodjournal.ui.components.MealTile
import com.stanley.foodjournal.ui.components.MealTileGap
import com.stanley.foodjournal.ui.components.stripShape
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import java.time.LocalDate

@Composable
fun JournalScreen(
    onAddMeal: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JournalScreen(uiState = uiState, onAddMeal = onAddMeal, onOpenMeal = onOpenMeal, modifier = modifier)
}

/**
 * Weeks, newest first, each a header and a strip of tiles from Monday to Sunday. The settings
 * icon sits at the top; its backup and restore arrive in Phase 6.
 */
@Composable
fun JournalScreen(
    uiState: JournalUiState,
    onAddMeal: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        JournalTopBar(onSettings = {})
        when {
            uiState.isEmpty -> EmptyState(
                message = stringResource(R.string.empty_journal),
                actionLabel = stringResource(R.string.log_first_meal),
                onAction = onAddMeal,
            )
            !uiState.isLoading -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Room below the last week for the Add button.
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(uiState.weeks, key = { it.start.toEpochDay() }) { week ->
                    WeekRow(week = week, onOpenMeal = onOpenMeal)
                }
            }
        }
    }
}

@Composable
private fun JournalTopBar(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSettings) {
            Icon(FoodJournalIcons.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

/**
 * "Oct 26 – Nov 1" in the serif, the total beside it in grey, then the week's strip. The current
 * week's strip opens at its newest meal. A meal that turns up later, logged meanwhile or brought
 * back by Undo, scrolls into view.
 */
@Composable
private fun WeekRow(week: JournalWeek, onOpenMeal: (Long) -> Unit) {
    val strip = rememberLazyListState(
        initialFirstVisibleItemIndex = if (week.isCurrent) week.tiles.lastIndex else 0,
    )
    // Saved, so a meal logged while the Meal screen covered the Journal still counts as new.
    var shown by rememberSaveable { mutableStateOf(week.tiles.map { it.mealId }.toLongArray()) }
    LaunchedEffect(week.tiles) {
        val added = week.tiles.indexOfFirst { it.mealId !in shown }
        shown = week.tiles.map { it.mealId }.toLongArray()
        if (added >= 0) strip.animateScrollToItem(added)
    }
    Column(Modifier.padding(top = 8.dp, bottom = 20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = week.range,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                text = "  ·  ${week.total}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            state = strip,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(MealTileGap),
        ) {
            itemsIndexed(week.tiles, key = { _, tile -> tile.mealId }) { index, tile ->
                MealTile(
                    thumb = tile.thumb,
                    photo = tile.photo,
                    name = tile.name,
                    label = tile.weekday,
                    shape = stripShape(first = index == 0, last = index == week.tiles.lastIndex),
                    onClick = { onOpenMeal(tile.mealId) },
                )
            }
        }
    }
}

private fun sampleWeeks(): List<JournalWeek> = listOf(
    JournalWeek(
        start = LocalDate.of(2026, 11, 2),
        range = "Nov 2 – 8",
        total = "620",
        tiles = listOf(
            JournalTile(1, null, "Beef noodle soup", "Mon"),
            JournalTile(2, null, "Latte", "Tue"),
        ),
    ),
    JournalWeek(
        start = LocalDate.of(2026, 10, 26),
        range = "Oct 26 – Nov 1",
        total = "2,340",
        tiles = listOf(
            JournalTile(3, null, "Dumplings", "Mon"),
            JournalTile(4, null, "Braised pork rice", "Wed"),
            JournalTile(5, null, "Oyster omelette at the night market", "Wed"),
            JournalTile(6, null, "Bubble tea", "Fri"),
            JournalTile(7, null, "Hot pot", "Sat"),
        ),
    ),
)

@ThemePreviews
@Composable
private fun JournalPreview() {
    PreviewSurface {
        JournalScreen(
            uiState = JournalUiState(isLoading = false, weeks = sampleWeeks()),
            onAddMeal = {},
            onOpenMeal = {},
        )
    }
}

@ThemePreviews
@Composable
private fun JournalEmptyPreview() {
    PreviewSurface {
        JournalScreen(uiState = JournalUiState(isLoading = false), onAddMeal = {}, onOpenMeal = {})
    }
}
