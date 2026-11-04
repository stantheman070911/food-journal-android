package com.stanley.foodjournal.ui.spending

import android.util.Log
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
import com.stanley.foodjournal.logic.lastDayInSearch
import com.stanley.foodjournal.logic.periodOf
import com.stanley.foodjournal.logic.periodSpending
import com.stanley.foodjournal.logic.periodsThrough
import com.stanley.foodjournal.logic.RecapFigures
import com.stanley.foodjournal.logic.RecapOptions
import com.stanley.foodjournal.logic.recapMeals
import com.stanley.foodjournal.logic.recapFigures
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.recap.RecapDrawing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * One bar of the chart. A future bar is drawn empty and can't be tapped. [firstDay] and [lastDay]
 * are the dates its meals open with in Search.
 */
data class ChartBar(
    val amount: Int,
    val amountText: String,
    val axisLabel: String?,
    val description: String,
    val isFuture: Boolean,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
)

/** A row with a share bar: a place, Other, or a time band. [share] runs from 0 to 1. */
data class ShareRow(val total: String, val share: Float)

data class PlaceRow(val placeId: Long, val name: String, val visits: Int, val row: ShareRow)

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

/**
 * One period's page. [figures] is null when the period has no meals. [lastDay] is where a
 * place's meals in Search end: today, or the period's end once it is over, or a later meal's day
 * when one is dated after today, so Search's total matches the place's.
 */
data class SpendingPage(
    val period: Period,
    val name: String,
    val figures: SpendingFigures?,
    val lastDay: LocalDate = period.end,
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
    /** The Share sheet, while it is open. */
    val recap: RecapSheetUiState? = null,
) {
    val isEmpty: Boolean get() = !isLoading && pages.isEmpty()
}

/**
 * The Share sheet: everything its recap could show, [options] for which of it it will, and
 * whether the image [isMaking] now.
 */
data class RecapSheetUiState(
    val figures: RecapFigures,
    val options: RecapOptions,
    val isMaking: Boolean = false,
)

/** An open Share sheet, with the meals its recap is drawn from. */
private data class OpenRecap(val sheet: RecapSheetUiState, val meals: List<Meal>)

/** Which kind of period is shown, and a date inside the period in view. */
private data class Selection(val kind: PeriodKind, val anchor: LocalDate)

private data class Pages(val kind: PeriodKind, val pages: List<SpendingPage>)

/**
 * Spending by week, month, or year. The tab opens on the current month. "Today" is read again
 * on [refreshToday], so a tab left open overnight moves on to the new day.
 */
class SpendingViewModel(
    private val mealDao: MealDao,
    placeDao: PlaceDao,
    private val photos: PhotoStore,
    private val recapDrawing: RecapDrawing,
    /** The figures last chosen for a recap, remembered across launches. */
    private val savedRecapOptions: Flow<RecapOptions>,
    private val saveRecapOptions: suspend (RecapOptions) -> Unit,
    private val appScope: CoroutineScope,
    private val clock: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val today = MutableStateFlow(clock())
    private val selection = MutableStateFlow(Selection(PeriodKind.Month, today.value))
    private val recap = MutableStateFlow<OpenRecap?>(null)
    private var recapJob: Job? = null
    private val recapFiles = Channel<File>(Channel.BUFFERED)

    /** Each recap image once it is drawn, for the screen to hand to the share sheet. */
    val recaps: Flow<File> = recapFiles.receiveAsFlow()

    private val pages = combine(
        mealDao.observeAll(),
        placeDao.observeAll(),
        today,
        selection.map { it.kind }.distinctUntilChanged(),
    ) { meals, places, today, kind -> Pages(kind, pages(meals, places, today, kind)) }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<SpendingUiState> = combine(pages, selection, recap) { built, selection, recap ->
        SpendingUiState(
            isLoading = false,
            kind = built.kind,
            pages = built.pages,
            selectedIndex = indexOf(periodOf(built.kind, selection.anchor), built.pages),
            recap = recap?.sheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingUiState())

    /**
     * Opens the Share sheet for [period], with the figures last chosen. Its meals are read now, so
     * the sheet shows what the image will. If they can't be read, that is logged and the sheet
     * stays closed.
     */
    fun openRecap(period: Period) {
        if (recap.value != null || recapJob?.isActive == true) return
        recapJob = viewModelScope.launch {
            try {
                val meals = mealDao.observeAll().first()
                val inPeriod = meals.filter { it.eatenAt.toLocalDate() in period }
                val previous = period.plus(-1)
                val figures = recapFigures(period, inPeriod, meals.filter { it.eatenAt.toLocalDate() in previous }, today.value)
                recap.value = OpenRecap(RecapSheetUiState(figures, savedRecapOptions.first()), inPeriod)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Recap failed", e)
            }
        }
    }

    /**
     * Changes which figures the recap shows, and remembers the choice for the next one. Each change
     * builds on the last, even before the sheet has redrawn. It is saved in the app's scope, where
     * a failure is logged rather than crashing the app, starting on the main thread so changes are
     * saved in the order they were made.
     */
    fun changeRecapOptions(change: (RecapOptions) -> RecapOptions) {
        val open = recap.value ?: return
        if (open.sheet.isMaking) return
        val options = change(open.sheet.options)
        recap.value = open.copy(sheet = open.sheet.copy(options = options))
        appScope.launch(Dispatchers.Main.immediate) { saveRecapOptions(options) }
    }

    /**
     * Draws the open sheet's recap: every meal of the period as a collage, above the period and
     * the chosen figures, then closes the sheet as the image goes to the share sheet. A recap that
     * can't be drawn is logged, and the sheet stays open to try again.
     */
    fun shareRecap(dark: Boolean) {
        val open = recap.value ?: return
        if (open.sheet.isMaking) return
        recap.value = open.copy(sheet = open.sheet.copy(isMaking = true))
        recapJob = viewModelScope.launch {
            try {
                val text = open.sheet.figures.text(open.sheet.options)
                recapFiles.send(recapDrawing.draw(recapMeals(open.meals), text, dark))
                recap.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Recap failed", e)
                recap.update { it?.copy(sheet = it.sheet.copy(isMaking = false)) }
            }
        }
    }

    /** Closes the Share sheet, dropping a recap still being drawn. */
    fun closeRecap() {
        recapJob?.cancel()
        recapJob = null
        recap.value = null
    }

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
            val latest = byPeriod[period.start]?.maxOfOrNull { it.eatenAt.toLocalDate() }
            val lastDay = lastDayInSearch(period.end, today, latest)
            SpendingPage(period, formatPeriodName(period, today), spending?.toFigures(names, today), lastDay)
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
                    firstDay = bar.start,
                    lastDay = bar.lastDay,
                )
            },
            places = topPlaces.map { PlaceRow(it.placeId, names.getValue(it.placeId), it.visits, share(it.total)) },
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
                SpendingViewModel(
                    mealDao = appContainer.mealDao,
                    placeDao = appContainer.placeDao,
                    photos = appContainer.photoStore,
                    recapDrawing = appContainer.recaps,
                    savedRecapOptions = appContainer.settings.recapOptions,
                    saveRecapOptions = appContainer.settings::setRecapOptions,
                    appScope = appContainer.appScope,
                )
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

private const val TAG = "FoodJournal"
