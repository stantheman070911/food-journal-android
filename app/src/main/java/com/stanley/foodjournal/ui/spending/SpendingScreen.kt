package com.stanley.foodjournal.ui.spending

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.ComparedWith
import com.stanley.foodjournal.logic.Period
import com.stanley.foodjournal.logic.PeriodKind
import com.stanley.foodjournal.logic.TimeBand
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.ui.components.EmptyState
import com.stanley.foodjournal.ui.components.MealTile
import com.stanley.foodjournal.ui.components.MealTileGap
import com.stanley.foodjournal.ui.components.stripShape
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import java.time.LocalDate

@Composable
fun SpendingScreen(
    onOpenMeal: (Long) -> Unit,
    onOpenPlace: (placeId: Long, from: LocalDate, to: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpendingViewModel = viewModel(factory = SpendingViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // A tab left open overnight moves on to the new day.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose {}
    }
    SpendingScreen(
        uiState = uiState,
        onSelectKind = viewModel::selectKind,
        onShowPeriod = viewModel::showPeriod,
        onOpenMeal = onOpenMeal,
        onOpenPlace = onOpenPlace,
        modifier = modifier,
    )
}

/**
 * The Week, Month, Year switch, with the periods beneath it to swipe through: total, change,
 * averages, bars, places, time of day, and the priciest meals. A place's row opens its meals of
 * the period in Search.
 */
@Composable
fun SpendingScreen(
    uiState: SpendingUiState,
    onSelectKind: (PeriodKind) -> Unit,
    onShowPeriod: (Period) -> Unit,
    onOpenMeal: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlace: (placeId: Long, from: LocalDate, to: LocalDate) -> Unit = { _, _, _ -> },
) {
    when {
        // No meal has been logged yet.
        uiState.isEmpty -> EmptyState(message = stringResource(R.string.empty_spending), modifier = modifier)
        !uiState.isLoading -> Column(modifier.fillMaxSize()) {
            PeriodSwitch(selected = uiState.kind, onSelect = onSelectKind)
            // A new pager for each kind, opening on the period the view model picked.
            key(uiState.kind) {
                PeriodPager(
                    uiState = uiState,
                    onShowPeriod = onShowPeriod,
                    onOpenMeal = onOpenMeal,
                    onOpenPlace = onOpenPlace,
                )
            }
        }
    }
}

/** Week, Month, and Year as a segmented pill, the selected segment filled in ink. */
@Composable
private fun PeriodSwitch(selected: PeriodKind, onSelect: (PeriodKind) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainer)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        PeriodKind.entries.forEach { kind ->
            val isSelected = kind == selected
            val container by animateColorAsState(
                if (isSelected) colors.primary else Color.Transparent,
                tween(SWITCH_FADE_MILLIS),
                label = "segment",
            )
            val content by animateColorAsState(
                if (isSelected) colors.onPrimary else colors.onSurface,
                tween(SWITCH_FADE_MILLIS),
                label = "segment label",
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(kind) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(kind.label),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                )
            }
        }
    }
}

/**
 * The periods, oldest first, opening on [SpendingUiState.selectedIndex]. The period swiped to is
 * reported back, and the pager follows when the view model moves on, as it does after midnight.
 *
 * The pager's page is not saved. The view model holds the selection, so a page restored on its
 * own, after Android closed the app or while another tab was showing, could disagree with it and
 * be reported as one swiped to.
 */
@Composable
private fun PeriodPager(
    uiState: SpendingUiState,
    onShowPeriod: (Period) -> Unit,
    onOpenMeal: (Long) -> Unit,
    onOpenPlace: (placeId: Long, from: LocalDate, to: LocalDate) -> Unit,
) {
    val pages = uiState.pages
    val currentPages by rememberUpdatedState(pages)
    val pager = remember { PagerState(currentPage = uiState.selectedIndex) { currentPages.size } }
    val currentOnShowPeriod by rememberUpdatedState(onShowPeriod)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            currentPages.getOrNull(page)?.let { currentOnShowPeriod(it.period) }
        }
    }
    LaunchedEffect(pager, uiState.selectedIndex) {
        if (!pager.isScrollInProgress && pager.settledPage != uiState.selectedIndex) {
            pager.scrollToPage(uiState.selectedIndex)
        }
    }
    HorizontalPager(
        state = pager,
        key = { pages[it].period.start.toEpochDay() },
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        PeriodPage(page = pages[index], onOpenMeal = onOpenMeal, onOpenPlace = onOpenPlace)
    }
}

@Composable
private fun PeriodPage(
    page: SpendingPage,
    onOpenMeal: (Long) -> Unit,
    onOpenPlace: (placeId: Long, from: LocalDate, to: LocalDate) -> Unit,
) {
    val figures = page.figures
    if (figures == null) {
        // The switch and swiping stay, so an empty period never strands you.
        Column(Modifier.fillMaxSize()) {
            PeriodName(page.name)
            EmptyState(message = stringResource(R.string.empty_spending))
        }
        return
    }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Room below the last section for the Add button.
            .padding(bottom = 96.dp),
    ) {
        PeriodName(page.name)
        Text(
            text = figures.total,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        figures.change?.let { change ->
            Text(
                text = change.text(),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Text(
            text = stringResource(R.string.spending_averages, figures.dailyAverage, figures.perMeal),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )

        Section(stringResource(R.string.spending_over_time)) {
            BarChart(bars = figures.bars)
        }

        Section(stringResource(R.string.spending_by_place)) {
            figures.places.forEach { place ->
                ShareLine(
                    name = place.name,
                    detail = pluralStringResource(R.plurals.spending_visits, place.visits, formatPrice(place.visits)),
                    row = place.row,
                    onClick = { onOpenPlace(place.placeId, page.period.start, page.lastDay) },
                )
            }
            figures.other?.let { other ->
                ShareLine(
                    name = stringResource(R.string.spending_other),
                    detail = pluralStringResource(R.plurals.meal_count, other.meals, formatPrice(other.meals)),
                    row = other.row,
                )
            }
        }

        Section(stringResource(R.string.spending_by_time)) {
            figures.bands.forEach { band ->
                ShareLine(name = stringResource(band.band.label), detail = null, row = band.row)
            }
        }

        if (figures.priciest.isNotEmpty()) {
            Section(stringResource(R.string.spending_priciest)) {
                Priciest(tiles = figures.priciest, onOpenMeal = onOpenMeal)
            }
        }
    }
}

@Composable
private fun PeriodName(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}

@Composable
private fun ChangeLine.text(): String = when (val previous = comparedWith) {
    ComparedWith.LastWeek -> stringResource(R.string.spending_vs_last_week, percent)
    ComparedWith.PreviousWeek -> stringResource(R.string.spending_vs_previous_week, percent)
    is ComparedWith.Named -> stringResource(R.string.spending_vs, percent, previous.name)
}

/** A grey label over its content. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 36.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/**
 * Grey bars on a hairline baseline, scaled to the period's dearest bar. Tapping a bar turns it
 * ink and shows its amount above it; a second tap clears it. Each bar is a tap target the full
 * height of the chart. Future bars are empty and can't be tapped.
 *
 * The room above the bars and below the baseline is measured from the label text, so the amount
 * and the axis stay inside the chart at any font size.
 */
@Composable
private fun BarChart(bars: List<ChartBar>) {
    // Changing period starts a new page, which clears the selection.
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val amountStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val max = bars.maxOf { it.amount }
    Box(
        Modifier
            .fillMaxWidth()
            .height(ChartHeight),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val slot = size.width / bars.size
            val barWidth = minOf(slot * BarFill, MaxBarWidth.toPx())
            val amountRoom = measurer.measure("0", amountStyle).size.height + AmountGap.toPx()
            val axisRoom = measurer.measure("0", axisStyle).size.height + AxisGap.toPx()
            val baseline = size.height - axisRoom
            val room = baseline - amountRoom
            val corner = CornerRadius(minOf(barWidth / 2, BarCorner.toPx()))
            fun heightOf(bar: ChartBar) =
                if (bar.amount == 0) 0f else maxOf(room * bar.amount / max, MinBarHeight.toPx())

            drawLine(
                color = colors.outlineVariant,
                start = Offset(0f, baseline),
                end = Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx(),
            )
            bars.forEachIndexed { index, bar ->
                val center = slot * (index + 0.5f)
                bar.axisLabel?.let { label ->
                    val text = measurer.measure(label, axisStyle)
                    drawText(text, topLeft = Offset(center - text.size.width / 2f, baseline + AxisGap.toPx()))
                }
                val height = heightOf(bar)
                if (bar.isFuture || height == 0f) return@forEachIndexed
                drawRoundRect(
                    color = if (selected == index) colors.primary else colors.outline,
                    topLeft = Offset(center - barWidth / 2, baseline - height),
                    size = Size(barWidth, height),
                    cornerRadius = corner,
                )
            }
            selected?.let { index ->
                val bar = bars[index]
                val text = measurer.measure(bar.amountText, amountStyle)
                val x = (slot * (index + 0.5f) - text.size.width / 2f).coerceIn(0f, size.width - text.size.width)
                val y = baseline - heightOf(bar) - AmountGap.toPx() - text.size.height
                drawText(text, topLeft = Offset(x, y))
            }
        }
        Row(Modifier.matchParentSize()) {
            bars.forEachIndexed { index, bar ->
                val target = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                val showAmount = stringResource(R.string.spending_show_amount)
                Box(
                    if (bar.isFuture) {
                        target.clearAndSetSemantics {}
                    } else {
                        target
                            .clickable(
                                interactionSource = null,
                                indication = null,
                                onClickLabel = showAmount,
                                role = Role.Button,
                            ) {
                                selected = if (selected == index) null else index
                            }
                            .semantics {
                                contentDescription = bar.description
                                this.selected = selected == index
                            }
                    },
                )
            }
        }
    }
}

/**
 * A name, an optional grey detail, and a total, over a thin bar showing its share. With
 * [onClick], as for a place, the row opens its meals.
 */
@Composable
private fun ShareLine(name: String, detail: String?, row: ShareRow, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val showMeals = stringResource(R.string.spending_show_meals)
    Column(
        Modifier
            .then(if (onClick != null) Modifier.clickable(onClickLabel = showMeals, onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Row {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .alignByBaseline(),
                )
            }
            Text(
                text = row.total,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .alignByBaseline(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(ShareBarHeight),
        ) {
            val corner = CornerRadius(size.height / 2)
            drawRoundRect(color = colors.surfaceContainerHighest, cornerRadius = corner)
            if (row.share > 0f) {
                drawRoundRect(
                    color = colors.outline,
                    size = Size(maxOf(size.width * row.share, size.height), size.height),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/** Up to three tiles filling the row, each with its price where the Journal shows the weekday. */
@Composable
private fun Priciest(tiles: List<PriciestTile>, onOpenMeal: (Long) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = (maxWidth - MealTileGap * 2) / 3
        Row(horizontalArrangement = Arrangement.spacedBy(MealTileGap)) {
            tiles.forEachIndexed { index, tile ->
                MealTile(
                    thumb = tile.thumb,
                    photo = tile.photo,
                    name = tile.name,
                    label = tile.price,
                    shape = stripShape(first = index == 0, last = index == tiles.lastIndex),
                    onClick = { onOpenMeal(tile.mealId) },
                    width = width,
                    height = width * 4 / 3,
                )
            }
        }
    }
}

private val PeriodKind.label: Int
    get() = when (this) {
        PeriodKind.Week -> R.string.spending_week
        PeriodKind.Month -> R.string.spending_month
        PeriodKind.Year -> R.string.spending_year
    }

private val TimeBand.label: Int
    get() = when (this) {
        TimeBand.Morning -> R.string.spending_morning
        TimeBand.Midday -> R.string.spending_midday
        TimeBand.Evening -> R.string.spending_evening
        TimeBand.Late -> R.string.spending_late
    }

private const val SWITCH_FADE_MILLIS = 150

private val ChartHeight = 180.dp
private val AmountGap = 4.dp
private val AxisGap = 4.dp
private val MaxBarWidth = 28.dp
private val MinBarHeight = 2.dp
private val BarCorner = 2.dp
private const val BarFill = 0.6f
private val ShareBarHeight = 4.dp

private fun samplePage(): SpendingPage {
    val amounts = listOf(320, 0, 180, 455, 0, 0, 0)
    return SpendingPage(
        period = Period(PeriodKind.Week, LocalDate.of(2026, 11, 2)),
        name = "Nov 2 – 8",
        figures = SpendingFigures(
            total = "955",
            change = ChangeLine("−12%", ComparedWith.LastWeek),
            dailyAverage = "239",
            perMeal = "191",
            bars = amounts.mapIndexed { index, amount ->
                ChartBar(amount, "$amount", "MTWTFSS"[index].toString(), "Nov ${index + 2}, $amount", isFuture = index > 3)
            },
            places = listOf(
                PlaceRow(1, "林家乾麵", 2, ShareRow("410", 0.43f)),
                PlaceRow(2, "Corner café", 1, ShareRow("180", 0.19f)),
            ),
            other = OtherRow(2, ShareRow("365", 0.38f)),
            bands = listOf(
                BandRow(TimeBand.Morning, ShareRow("180", 0.19f)),
                BandRow(TimeBand.Midday, ShareRow("410", 0.43f)),
                BandRow(TimeBand.Evening, ShareRow("365", 0.38f)),
                BandRow(TimeBand.Late, ShareRow("0", 0f)),
            ),
            priciest = listOf(
                PriciestTile(1, null, null, "Hot pot", "455"),
                PriciestTile(2, null, null, "Beef noodle soup", "320"),
                PriciestTile(3, null, null, "Latte", "180"),
            ),
        ),
    )
}

@ThemePreviews
@Composable
private fun SpendingPreview() {
    PreviewSurface {
        SpendingScreen(
            uiState = SpendingUiState(isLoading = false, kind = PeriodKind.Week, pages = listOf(samplePage())),
            onSelectKind = {},
            onShowPeriod = {},
            onOpenMeal = {},
        )
    }
}

@ThemePreviews
@Composable
private fun SpendingEmptyPreview() {
    PreviewSurface {
        SpendingScreen(uiState = SpendingUiState(isLoading = false), onSelectKind = {}, onShowPeriod = {}, onOpenMeal = {})
    }
}
