package com.stanley.foodjournal.ui.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.ui.components.EmptyState
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import java.time.LocalDate

@Composable
fun SearchScreen(
    onAddMeal: () -> Unit,
    onOpenMeal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        uiState = uiState,
        query = viewModel.query,
        actions = SearchActions(
            onQueryChange = viewModel::onQueryChange,
            onClearPlace = viewModel::clearPlace,
            onSetDates = viewModel::setDates,
            onToggleFavorites = viewModel::toggleFavorites,
            onOpenMeal = onOpenMeal,
            onAddMeal = onAddMeal,
        ),
        modifier = modifier,
    )
}

/** Everything the Search screen can ask for. */
class SearchActions(
    val onQueryChange: (String) -> Unit = {},
    val onClearPlace: () -> Unit = {},
    val onSetDates: (LocalDate?, LocalDate?) -> Unit = { _, _ -> },
    val onToggleFavorites: () -> Unit = {},
    val onOpenMeal: (Long) -> Unit = {},
    val onAddMeal: () -> Unit = {},
)

/**
 * The search field, with the place chip inside it, then the Dates and Favorites pills, the
 * total, and the meals found, newest first.
 */
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    query: String,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    when {
        // No meal has been logged yet.
        uiState.isEmpty -> EmptyState(
            message = stringResource(R.string.empty_search),
            actionLabel = stringResource(R.string.log_first_meal),
            onAction = actions.onAddMeal,
            modifier = modifier,
        )
        !uiState.isLoading -> Column(modifier.fillMaxSize()) {
            SearchField(
                query = query,
                onQueryChange = actions.onQueryChange,
                placeName = uiState.placeName,
                onClearPlace = actions.onClearPlace,
            )
            Filters(uiState = uiState, actions = actions)
            if (uiState.results.isEmpty()) {
                EmptyState(message = stringResource(R.string.search_no_results))
            } else {
                Results(uiState = uiState, onOpenMeal = actions.onOpenMeal)
            }
        }
    }
}

/** A grey pill: the search icon, the place chip, and the text, with × to clear the text. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeName: String?,
    onClearPlace: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(CircleShape)
            .background(colors.surfaceContainer)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            FoodJournalIcons.Search,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        if (placeName != null) {
            PlaceChip(name = placeName, onClear = onClearPlace)
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    FoodJournalIcons.Close,
                    contentDescription = stringResource(R.string.search_clear_text),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** The place, as a pill on the field's grey, with × to clear it. */
@Composable
private fun PlaceChip(name: String, onClear: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(colors.background)
            .clickable(onClickLabel = stringResource(R.string.search_clear_place), onClick = onClear)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = ChipMaxWidth),
        )
        Spacer(Modifier.width(4.dp))
        Icon(FoodJournalIcons.Close, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/** The Dates and Favorites pills. A set filter is filled in ink. */
@Composable
private fun Filters(uiState: SearchUiState, actions: SearchActions) {
    var pickingDates by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dates = uiState.dates
        FilterPill(
            label = dates ?: stringResource(R.string.search_dates),
            selected = dates != null,
            onClick = { pickingDates = true },
            onClear = if (dates != null) {
                { actions.onSetDates(null, null) }
            } else {
                null
            },
            clearLabel = stringResource(R.string.search_clear_dates),
        )
        FilterPill(
            label = stringResource(R.string.search_favorites),
            selected = uiState.favoritesOnly,
            icon = if (uiState.favoritesOnly) FoodJournalIcons.FavoriteOn else FoodJournalIcons.Favorite,
            onClick = actions.onToggleFavorites,
        )
    }
    if (pickingDates) {
        DatesDialog(
            from = uiState.from,
            to = uiState.to,
            onDone = { from, to ->
                pickingDates = false
                actions.onSetDates(from, to)
            },
            onDismiss = { pickingDates = false },
        )
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    onClear: (() -> Unit)? = null,
    clearLabel: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimary else colors.onSurface
    val on = stringResource(R.string.search_filter_on)
    Row(
        Modifier
            .height(PillHeight)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .height(PillHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { if (selected) stateDescription = on }
                .padding(start = 14.dp, end = if (onClear != null) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = content,
                maxLines = 1,
            )
        }
        if (onClear != null) {
            Box(
                Modifier
                    .height(PillHeight)
                    .clickable(onClickLabel = clearLabel, role = Role.Button, onClick = onClear)
                    .padding(start = 4.dp, end = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(FoodJournalIcons.Close, contentDescription = clearLabel, tint = content, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Material's date-range dialog. Future days can't be picked; one day picked is that day alone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatesDialog(
    from: LocalDate?,
    to: LocalDate?,
    onDone: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from?.let { it.toEpochDay() * DAY_MILLIS },
        initialSelectedEndDateMillis = to?.let { it.toEpochDay() * DAY_MILLIS },
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / DAY_MILLIS <= today.toEpochDay()

            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    val start = state.selectedStartDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null,
                onClick = {
                    val first = LocalDate.ofEpochDay((start ?: return@TextButton) / DAY_MILLIS)
                    val last = state.selectedEndDateMillis?.let { LocalDate.ofEpochDay(it / DAY_MILLIS) } ?: first
                    onDone(first, last)
                },
            ) { Text(stringResource(R.string.done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    ) {
        val colors = MaterialTheme.colorScheme
        DateRangePicker(
            state = state,
            showModeToggle = false,
            // The defaults leave room for a full-screen picker's close button.
            title = {
                DateRangePickerDefaults.DateRangePickerTitle(
                    displayMode = state.displayMode,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            headline = {
                DateRangePickerDefaults.DateRangePickerHeadline(
                    selectedStartDateMillis = state.selectedStartDateMillis,
                    selectedEndDateMillis = state.selectedEndDateMillis,
                    displayMode = state.displayMode,
                    dateFormatter = remember { DatePickerDefaults.dateFormatter() },
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
            // The days between the ends, in a grey the dialog's own grey doesn't swallow.
            colors = DatePickerDefaults.colors(
                dayInSelectionRangeContainerColor = colors.onSurface.copy(alpha = RangeAlpha),
                dayInSelectionRangeContentColor = colors.onSurface,
            ),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Results(uiState: SearchUiState, onOpenMeal: (Long) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room below the last result for the Add button.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "total") {
            Text(
                text = stringResource(
                    R.string.search_total,
                    pluralStringResource(R.plurals.meal_count, uiState.count, formatPrice(uiState.count)),
                    uiState.total,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        items(uiState.results, key = { it.mealId }) { result ->
            ResultRow(result = result, onClick = { onOpenMeal(result.mealId) })
        }
    }
}

/** A thumbnail, the name over "Tue, Nov 3 · 林家乾麵", a star for a favorite, and the price. */
@Composable
private fun ResultRow(result: SearchResult, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.open_meal), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(result)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            result.name?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = result.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (result.isFavorite) {
            Icon(
                FoodJournalIcons.FavoriteOn,
                contentDescription = stringResource(R.string.meal_favorite),
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
            )
        }
        Text(
            text = result.price,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * The meal's thumbnail at 3:4, falling back to the full photo and then to a plain grey tile, as
 * the Journal's tiles do.
 */
@Composable
private fun Thumbnail(result: SearchResult) {
    var failures by remember(result.thumb, result.photo) { mutableIntStateOf(0) }
    val image = listOfNotNull(result.thumb, result.photo).getOrNull(failures)
    Box(
        Modifier
            .size(ThumbWidth, ThumbHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failures++ },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val DAY_MILLIS = 86_400_000L
private const val RangeAlpha = 0.12f
private val FieldHeight = 48.dp
private val PillHeight = 36.dp
private val ChipMaxWidth = 160.dp
private val ThumbWidth = 48.dp
private val ThumbHeight = 64.dp

private fun sampleState() = SearchUiState(
    isLoading = false,
    hasMeals = true,
    placeName = "林家乾麵",
    dates = "Oct 1 – Nov 4",
    from = LocalDate.of(2026, 10, 1),
    to = LocalDate.of(2026, 11, 4),
    count = 3,
    total = "620",
    results = listOf(
        SearchResult(1, null, null, "Beef noodle soup", "Tue, Nov 3 · 林家乾麵", "180", isFavorite = true),
        SearchResult(2, null, null, "Dumplings", "Mon, Oct 26 · 林家乾麵", "120", isFavorite = false),
        SearchResult(3, null, null, null, "Tue, Oct 20 · 林家乾麵", "320", isFavorite = false),
    ),
)

@ThemePreviews
@Composable
private fun SearchPreview() {
    PreviewSurface {
        SearchScreen(uiState = sampleState(), query = "", actions = SearchActions())
    }
}

@ThemePreviews
@Composable
private fun SearchNoResultsPreview() {
    PreviewSurface {
        SearchScreen(
            uiState = SearchUiState(isLoading = false, hasMeals = true, favoritesOnly = true),
            query = "ramen",
            actions = SearchActions(),
        )
    }
}

@ThemePreviews
@Composable
private fun SearchEmptyPreview() {
    PreviewSurface {
        SearchScreen(uiState = SearchUiState(isLoading = false), query = "", actions = SearchActions())
    }
}
