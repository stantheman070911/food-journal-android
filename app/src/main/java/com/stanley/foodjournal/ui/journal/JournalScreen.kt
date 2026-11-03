package com.stanley.foodjournal.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.unbreakableWords
import com.stanley.foodjournal.ui.components.EmptyState
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
            horizontalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            itemsIndexed(week.tiles, key = { _, tile -> tile.mealId }) { index, tile ->
                Tile(
                    tile = tile,
                    shape = stripShape(first = index == 0, last = index == week.tiles.lastIndex),
                    onClick = { onOpenMeal(tile.mealId) },
                )
            }
        }
    }
}

/**
 * One meal: its photo, or a plain grey tile with its name. The weekday sits in the lower left,
 * in white over a photo and grey on the plain tile.
 *
 * A thumbnail that can't be read, such as one not yet rebuilt after a restore, gives way to the
 * full photo, and if that fails too, to the plain tile.
 */
@Composable
private fun Tile(tile: JournalTile, shape: Shape, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var failures by remember(tile.thumb, tile.photo) { mutableIntStateOf(0) }
    val image = listOfNotNull(tile.thumb, tile.photo).getOrNull(failures)
    Box(
        Modifier
            .size(TileWidth, TileHeight)
            .clip(shape)
            .background(colors.surfaceContainer)
            .clickable(onClickLabel = stringResource(R.string.journal_open_meal), onClick = onClick),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = tile.name,
                contentScale = ContentScale.Crop,
                onError = { failures++ },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val name = tile.name.orEmpty()
            Text(
                text = name,
                style = tileNameStyle(name),
                color = colors.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(NamePadding),
            )
        }
        Text(
            text = tile.weekday,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                shadow = if (image != null) WeekdayShadow else null,
            ),
            color = if (image != null) Color.White else colors.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp),
        )
    }
}

/**
 * A plain tile's name, set so a word too long for the tile, as at a large font size, rarely breaks
 * just anywhere. The name first shrinks, by up to a fifth, until its longest word fits; past that,
 * words break with a hyphen. English patterns come first, as the interface is English; the phone's
 * own locales follow, so Chinese names keep their glyphs.
 */
@Composable
private fun tileNameStyle(name: String): TextStyle {
    val base = MaterialTheme.typography.labelLarge.copy(
        hyphens = Hyphens.Auto,
        localeList = LocaleList(listOf(Locale("en")) + LocaleList.current.localeList),
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scale = remember(name, base, density) {
        val room = with(density) { (TileWidth - NamePadding * 2).toPx() }
        val widest = unbreakableWords(name).maxOfOrNull { word ->
            measurer.measure(word, base, softWrap = false, maxLines = 1).size.width
        } ?: 0
        // A little to spare, so a word sized to fit exactly isn't broken after all.
        if (widest <= room) 1f else (room * FitSlack / widest).coerceAtLeast(MinNameScale)
    }
    return if (scale == 1f) {
        base
    } else {
        base.copy(
            fontSize = base.fontSize * scale,
            lineHeight = base.lineHeight * scale,
            letterSpacing = base.letterSpacing * scale,
        )
    }
}

/** The strip reads as one rounded band: only its two ends are rounded. */
private fun stripShape(first: Boolean, last: Boolean): Shape {
    val start = if (first) StripCorner else 0.dp
    val end = if (last) StripCorner else 0.dp
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

// Portrait tiles, like the photos; about four and a half fit across, so the strip invites a swipe.
private val TileWidth = 88.dp
private val TileHeight = 117.dp
private val TileGap = 2.dp
private val StripCorner = 12.dp
private val NamePadding = 10.dp
private const val MinNameScale = 0.8f
private const val FitSlack = 0.97f
private val WeekdayShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f)

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
