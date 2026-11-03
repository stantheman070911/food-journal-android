package com.stanley.foodjournal.ui.spending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.logic.ComparedWith
import com.stanley.foodjournal.logic.Period
import com.stanley.foodjournal.logic.PeriodKind
import com.stanley.foodjournal.logic.PeriodSpending
import com.stanley.foodjournal.logic.TimeBand
import com.stanley.foodjournal.logic.axisLabel
import com.stanley.foodjournal.logic.comparedWith
import com.stanley.foodjournal.logic.describeBar
import com.stanley.foodjournal.logic.formatChange
import com.stanley.foodjournal.logic.formatPeriodName
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.logic.periodOf
import com.stanley.foodjournal.logic.periodSpending
import com.stanley.foodjournal.logic.periodsThrough
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDate

/** One bar of the chart. A future bar is drawn empty and can't be tapped. */
data class ChartBar(
    val amount: Int,
    val amountText: String,
    val axisLabel: String?,
    val description: String,
    val isFuture: Boolean,
)

/** A row with a share bar: a place, Other, or a time band. [share] runs from 0 to 1. */
data class ShareRow(val total: String, val share: Float)

data class PlaceRow(val name: String, val visits: Int, val row: ShareRow)

data class OtherRow(val meals: Int, val row: ShareRow)

data class BandRow(val band: TimeBand, val row: ShareRow)

/** One of the priciest meals, as a tile with its price where the Journal shows the weekday. */
data class PriciestTile(
    val mealId: Long,
    val thumb: File?,
    val photo: File?,
    val name: String?,
    val price: String,
)

/** The change line's percent, such as "−12%", and what it compares with. */
data class ChangeLine(val percent: String, val comparedWith: ComparedWith)

/** The figures of a period with meals. */
data class SpendingFigures(
    val total: String,
    val change: ChangeLine?,
    val dailyAverage: String,
    val perMeal: String,
    val bars: List<ChartBar>,
    val places: List<PlaceRow>,
    val other: OtherRow?,
    val bands: List<BandRow>,
    val priciest: List<PriciestTile>,
)

/** One period's page. [figures] is null when the period has no meals. */
data class SpendingPage(
    val period: Period,
    val name: String,
    val figures: SpendingFigures?,
)

/**
 * The periods of [kind] from the earliest meal's to today's, oldest first, with the one in view
 * at [selectedIndex]. With no meals at all, [pages] is empty.
 */
data class SpendingUiState(
    val isLoading: Boolean = true,
    val kind: PeriodKind = PeriodKind.Month,
    val pages: List<SpendingPage> = emptyList(),
    val selectedIndex: Int = 0,
) {
    val isEmpty: Boolean get() = !isLoading && pages.isEmpty()
}

/** Which kind of period is shown, and a date inside the period in view. */
private data class Selection(val kind: PeriodKind, val anchor: LocalDate)

private data class Pages(val kind: PeriodKind, val pages: List<SpendingPage>)

/**
 * Spending by week, month, or year. The tab opens on the current month. "Today" is read again
 * on [refreshToday], so a tab left open overnight moves on to the new day.
 */
class SpendingViewModel(
    mealDao: MealDao,
    placeDao: PlaceDao,
    private val photos: PhotoStore,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val today = MutableStateFlow(clock())
    private val selection = MutableStateFlow(Selection(PeriodKind.Month, today.value))

    private val pages = combine(
        mealDao.observeAll(),
        placeDao.observeAll(),
        today,
        selection.map { it.kind }.distinctUntilChanged(),
    ) { meals, places, today, kind -> Pages(kind, pages(meals, places, today, kind)) }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<SpendingUiState> = combine(pages, selection) { built, selection ->
        SpendingUiState(
            isLoading = false,
            kind = built.kind,
            pages = built.pages,
            selectedIndex = indexOf(periodOf(built.kind, selection.anchor), built.pages),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingUiState())

    /**
     * Shows periods of [kind], starting with the one holding the last day of the period in view,
     * or today if that comes sooner.
     */
    fun selectKind(kind: PeriodKind) {
        val state = uiState.value
        val shown = state.pages.getOrNull(state.selectedIndex)?.period
        selection.update { current ->
            if (current.kind == kind) return@update current
            val end = (shown ?: periodOf(current.kind, current.anchor)).end
            Selection(kind, minOf(end, today.value))
        }
    }

    /** Records the period swiped to. */
    fun showPeriod(period: Period) {
        selection.update { if (it.kind == period.kind) it.copy(anchor = period.start) else it }
    }

    /** Reads the date again. If today's period was in view, the new today's period comes into view. */
    fun refreshToday() {
        val now = clock()
        val before = today.value
        if (now == before) return
        selection.update { current ->
            if (periodOf(current.kind, current.anchor) == periodOf(current.kind, before)) current.copy(anchor = now) else current
        }
        today.value = now
    }

    private fun pages(meals: List<Meal>, places: List<Place>, today: LocalDate, kind: PeriodKind): List<SpendingPage> {
        val earliest = meals.minOfOrNull { it.eatenAt } ?: return emptyList()
        val names = places.associate { it.id to it.name }
        val byPeriod = meals.groupBy { periodOf(kind, it.eatenAt.toLocalDate()).start }
        return periodsThrough(kind, earliest.toLocalDate(), today).map { period ->
            val spending = periodSpending(
                period = period,
                meals = byPeriod[period.start].orEmpty(),
                previousMeals = byPeriod[period.plus(-1).start].orEmpty(),
                today = today,
                knownPlaces = names.keys,
            )
            SpendingPage(period, formatPeriodName(period, today), spending?.toFigures(names, today))
        }
    }

    private fun PeriodSpending.toFigures(names: Map<Long, String>, today: LocalDate): SpendingFigures {
        fun share(amount: Int) = ShareRow(formatPrice(amount), if (total == 0) 0f else amount.toFloat() / total)
        return SpendingFigures(
            total = formatPrice(total),
            change = change?.let { ChangeLine(formatChange(it), comparedWith(period, today)) },
            dailyAverage = formatPrice(dailyAverage),
            perMeal = formatPrice(perMeal),
            bars = bars.map { bar ->
                ChartBar(
                    amount = bar.amount,
                    amountText = formatPrice(bar.amount),
                    axisLabel = axisLabel(period.kind, bar.start),
                    description = describeBar(period.kind, bar),
                    isFuture = bar.isFuture,
                )
            },
            places = topPlaces.map { PlaceRow(names.getValue(it.placeId), it.visits, share(it.total)) },
            other = other?.let { OtherRow(it.meals, share(it.total)) },
            bands = bands.map { (band, amount) -> BandRow(band, share(amount)) },
            priciest = priciest.map { meal ->
                PriciestTile(
                    mealId = meal.id,
                    thumb = meal.photo?.let(photos::thumbFile),
                    photo = meal.photo?.let(photos::photoFile),
                    name = meal.name,
                    price = formatPrice(meal.price),
                )
            },
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SpendingViewModel(appContainer.mealDao, appContainer.placeDao, appContainer.photoStore)
            }
        }
    }
}

/** The index of [period] in [pages], or the nearest end when it lies outside them. */
private fun indexOf(period: Period, pages: List<SpendingPage>): Int {
    if (pages.isEmpty()) return 0
    val index = pages.indexOfFirst { it.period == period }
    return when {
        index >= 0 -> index
        period.start < pages.first().period.start -> 0
        else -> pages.lastIndex
    }
}
