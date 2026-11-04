package com.stanley.foodjournal.ui.spending

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.logic.ComparedWith
import com.stanley.foodjournal.logic.Period
import com.stanley.foodjournal.logic.PeriodKind
import com.stanley.foodjournal.logic.RecapOptions
import com.stanley.foodjournal.logic.RecapText
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class SpendingViewModelTest {
    private lateinit var db: FoodJournalDatabase
    private val photos = PhotoStore(File("/spending-test"))
    private val store = ViewModelStore()

    // A Wednesday, so the current week is Nov 2 – 8.
    private var today = LocalDate.of(2026, 11, 4)

    private var savedOptions = RecapOptions()
    private val saves = CopyOnWriteArrayList<RecapOptions>()
    private val drawn = CopyOnWriteArrayList<RecapText>()
    private var drawingFails = false
    private var savingFails = false
    private var readingOptionsFails = false
    private var drawingWaitsFor: CompletableDeferred<Unit>? = null

    @Volatile
    private var drawingStarted = false

    @Volatile
    private var drawingDropped = false

    @Volatile
    private var drawingFailed = false

    @Volatile
    private var readingOptionsFailed = false

    /** As the app's scope does, logs a failure rather than crashing. */
    private val backgroundFailures = CopyOnWriteArrayList<Throwable>()
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> backgroundFailures += error },
    )

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FoodJournalDatabase::class.java,
        ).build()
    }

    @After
    fun close() {
        // Stop the ViewModel's queries before the database closes under it.
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        db.close()
    }

    @Test
    fun noMealsMeansNoPeriods() = runBlocking {
        assertTrue(viewModel().state { true }.isEmpty)
    }

    @Test
    fun opensOnTheCurrentMonthWithEveryMonthSinceTheFirstMeal() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 9, 20, 12, 0), price = 300))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), price = 450))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 3, 12, 0), price = 500))

        val state = viewModel().state { it.pages.isNotEmpty() }

        assertEquals(PeriodKind.Month, state.kind)
        assertEquals(listOf("September", "October", "November"), state.pages.map { it.name })
        assertEquals(2, state.selectedIndex)
        val november = state.pages[2].figures!!
        assertEquals("450", november.total)
        // Nov 1 – 4 against Oct 1 – 4: 450 against 500.
        assertEquals(ChangeLine("−10%", ComparedWith.Named("Oct")), november.change)
        assertEquals("113", november.dailyAverage)
        assertEquals(30, november.bars.size)
    }

    @Test
    fun aMonthWithoutMealsKeepsItsPage() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 9, 20, 12, 0)))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0)))

        val pages = viewModel().state { it.pages.isNotEmpty() }.pages

        assertEquals("October", pages[1].name)
        assertNull(pages[1].figures)
    }

    @Test
    fun switchingKindKeepsTheShownPeriodsLastDay() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 9, 20, 12, 0)))
        val vm = viewModel()
        vm.state { it.pages.isNotEmpty() }

        withContext(Dispatchers.Main) { vm.showPeriod(Period(PeriodKind.Month, LocalDate.of(2026, 10, 1))) }
        vm.state { it.selectedIndex == 1 }
        withContext(Dispatchers.Main) { vm.selectKind(PeriodKind.Week) }

        val weeks = vm.state { it.kind == PeriodKind.Week }
        assertEquals("Oct 26 – Nov 1", weeks.pages[weeks.selectedIndex].name)

        withContext(Dispatchers.Main) { vm.selectKind(PeriodKind.Year) }
        val years = vm.state { it.kind == PeriodKind.Year }
        assertEquals("2026", years.pages[years.selectedIndex].name)
    }

    @Test
    fun switchingKindUsesThePeriodShownWhenTheChosenOneIsGone() = runBlocking {
        val september = db.mealDao().insert(meal(LocalDateTime.of(2026, 9, 10, 12, 0)))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 5, 12, 0)))
        val vm = viewModel()
        vm.state { it.pages.size == 3 }
        withContext(Dispatchers.Main) { vm.showPeriod(Period(PeriodKind.Month, LocalDate.of(2026, 9, 1))) }
        vm.state { it.selectedIndex == 0 }

        // September's only meal goes, so October is the first period and the one shown.
        db.mealDao().delete(db.mealDao().get(september)!!)
        val shown = vm.state { it.pages.size == 2 }
        assertEquals("October", shown.pages[shown.selectedIndex].name)

        withContext(Dispatchers.Main) { vm.selectKind(PeriodKind.Week) }

        // October's last day, not September's.
        val weeks = vm.state { it.kind == PeriodKind.Week }
        assertEquals("Oct 26 – Nov 1", weeks.pages[weeks.selectedIndex].name)
    }

    @Test
    fun aSwipeReportedForTheOldKindIsIgnored() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 9, 20, 12, 0)))
        val vm = viewModel()
        vm.state { it.pages.isNotEmpty() }

        withContext(Dispatchers.Main) {
            vm.selectKind(PeriodKind.Week)
            // The month pager settles once more before the week pager replaces it.
            vm.showPeriod(Period(PeriodKind.Month, LocalDate.of(2026, 9, 1)))
        }

        val weeks = vm.state { it.kind == PeriodKind.Week }
        assertEquals("Nov 2 – 8", weeks.pages[weeks.selectedIndex].name)
    }

    @Test
    fun switchingFromTheCurrentPeriodLandsOnToday() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 9, 20, 12, 0)))
        val vm = viewModel()
        vm.state { it.pages.isNotEmpty() }

        withContext(Dispatchers.Main) { vm.selectKind(PeriodKind.Week) }

        val weeks = vm.state { it.kind == PeriodKind.Week }
        assertEquals(weeks.pages.lastIndex, weeks.selectedIndex)
        assertEquals("Nov 2 – 8", weeks.pages[weeks.selectedIndex].name)
    }

    @Test
    fun aNewDayMovesOnFromTheCurrentPeriodOnly() = runBlocking {
        today = LocalDate.of(2026, 11, 30)
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 3, 12, 0)))
        val vm = viewModel()
        assertEquals(1, vm.state { it.pages.isNotEmpty() }.selectedIndex)

        today = LocalDate.of(2026, 12, 1)
        withContext(Dispatchers.Main) { vm.refreshToday() }

        val moved = vm.state { it.pages.size == 3 }
        assertEquals("December", moved.pages[moved.selectedIndex].name)

        // An older period in view stays put.
        withContext(Dispatchers.Main) { vm.showPeriod(Period(PeriodKind.Month, LocalDate.of(2026, 10, 1))) }
        vm.state { it.selectedIndex == 0 }
        today = LocalDate.of(2027, 1, 1)
        withContext(Dispatchers.Main) { vm.refreshToday() }

        val stayed = vm.state { it.pages.size == 4 }
        // Named with its year, now that the year has turned.
        assertEquals("October 2026", stayed.pages[stayed.selectedIndex].name)
    }

    @Test
    fun placesShowByNameAndTheRestIsOther() = runBlocking {
        val noodles = db.placeDao().insert(Place(name = "林家乾麵"))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), price = 300, placeId = noodles))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 12, 0), price = 100, placeId = noodles))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 23, 0), price = 100))

        val figures = viewModel().state { it.pages.isNotEmpty() }.pages.last().figures!!

        assertEquals(listOf(PlaceRow(noodles, "林家乾麵", 2, ShareRow("400", 0.8f))), figures.places)
        assertEquals(OtherRow(1, ShareRow("100", 0.2f)), figures.other)
        assertEquals(listOf("0", "400", "0", "100"), figures.bands.map { it.row.total })
    }

    @Test
    fun aPlacesMealsInSearchEndAtToday() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 3, 12, 0)))

        val pages = viewModel().state { it.pages.isNotEmpty() }.pages

        assertEquals(LocalDate.of(2026, 10, 31), pages[0].lastDay)
        assertEquals(today, pages[1].lastDay)
    }

    @Test
    fun aMealDatedLaterStretchesSearchsDatesSoTheTotalsMatch() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0)))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 20, 12, 0)))

        val page = viewModel().state { it.pages.isNotEmpty() }.pages.last()

        assertEquals(LocalDate.of(2026, 11, 20), page.lastDay)
    }

    @Test
    fun priciestTilesCarryTheirPhotoAndPrice() = runBlocking {
        val photo = db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), price = 1_280, photo = "a.jpg"))
        val named = db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 12, 0), price = 90, name = "Latte"))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 13, 0), price = 0))

        val tiles = viewModel().state { it.pages.isNotEmpty() }.pages.last().figures!!.priciest

        assertEquals(listOf(photo, named), tiles.map { it.mealId })
        assertEquals("1,280", tiles[0].price)
        assertEquals(photos.thumbFile("a.jpg"), tiles[0].thumb)
        assertEquals(photos.photoFile("a.jpg"), tiles[0].photo)
        assertNull(tiles[1].thumb)
        assertEquals("Latte", tiles[1].name)
    }

    @Test
    fun theShareSheetOpensWithTheFiguresLastChosen() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), price = 450))
        savedOptions = RecapOptions(total = false)
        val vm = viewModel()
        val page = vm.state { it.pages.isNotEmpty() }.pages.last()

        vm.openRecap(page.period)
        val sheet = vm.state { it.recap != null }.recap!!

        assertEquals("November", sheet.figures.title)
        assertEquals(RecapOptions(total = false), sheet.options)
        assertEquals("1 meal", sheet.figures.meals)
        assertEquals("450", sheet.figures.total)
        assertEquals(emptyList<RecapText>(), drawn)
    }

    @Test
    fun theRecapShowsOnlyTheChosenFiguresAndTheChoiceIsSaved() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), price = 450))
        val vm = viewModel()
        val page = vm.state { it.pages.isNotEmpty() }.pages.last()
        vm.openRecap(page.period)
        vm.state { it.recap != null }

        val chosen = RecapOptions(total = false)
        vm.changeRecapOptions { it.copy(total = false) }
        vm.state { it.recap?.options == chosen }
        vm.shareRecap(dark = false)

        assertEquals(File("/spending-test/recap.png"), withTimeout(3_000) { vm.recaps.first() })
        assertNull(vm.state { it.recap == null }.recap)
        assertEquals("1 meal", drawn.single().stats)
        withTimeout(3_000) { while (saves.isEmpty()) delay(20) }
        assertEquals(chosen, saves.single())
    }

    @Test
    fun aRecapThatCantBeDrawnLeavesTheSheetOpen() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0)))
        drawingFails = true
        val vm = viewModel()
        vm.openRecap(vm.state { it.pages.isNotEmpty() }.pages.last().period)
        vm.state { it.recap != null }

        vm.shareRecap(dark = false)
        withTimeout(3_000) { while (!drawingFailed) delay(20) }

        assertTrue(vm.state { it.recap?.isMaking == false }.recap != null)
        vm.closeRecap()
        assertNull(vm.state { it.recap == null }.recap)
    }

    @Test
    fun closingTheSheetDropsARecapStillBeingDrawn() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0)))
        drawingWaitsFor = CompletableDeferred()
        val vm = viewModel()
        val period = vm.state { it.pages.isNotEmpty() }.pages.last().period
        vm.openRecap(period)
        vm.state { it.recap != null }

        vm.shareRecap(dark = false)
        withTimeout(3_000) { while (!drawingStarted) delay(20) }
        vm.closeRecap()

        withTimeout(3_000) { while (!drawingDropped) delay(20) }
        assertNull(vm.state { it.recap == null }.recap)
        assertNull(withTimeoutOrNull(200) { vm.recaps.first() })
        assertEquals(emptyList<RecapText>(), drawn)

        // The next recap starts afresh.
        drawingWaitsFor = null
        vm.openRecap(period)
        vm.state { it.recap != null }
        vm.shareRecap(dark = false)
        assertEquals(File("/spending-test/recap.png"), withTimeout(3_000) { vm.recaps.first() })
    }

    @Test
    fun changesMadeBeforeTheSheetRedrawsAllCount() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0)))
        val vm = viewModel()
        vm.openRecap(vm.state { it.pages.isNotEmpty() }.pages.last().period)
        vm.state { it.recap != null }

        vm.changeRecapOptions { it.copy(meals = false) }
        vm.changeRecapOptions { it.copy(places = false) }

        val chosen = RecapOptions(meals = false, places = false)
        vm.state { it.recap?.options == chosen }
        withTimeout(3_000) { while (saves.size < 2) delay(20) }
        assertEquals(chosen, saves.last())
    }

    /** A failure outside the app's scope would crash the app, and with it this test. */
    @Test
    fun aChoiceThatCantBeSavedStillShowsAndIsLogged() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0)))
        savingFails = true
        val vm = viewModel()
        vm.openRecap(vm.state { it.pages.isNotEmpty() }.pages.last().period)
        vm.state { it.recap != null }

        vm.changeRecapOptions { it.copy(total = false) }

        vm.state { it.recap?.options == RecapOptions(total = false) }
        withTimeout(3_000) { while (backgroundFailures.isEmpty()) delay(20) }
        assertTrue(backgroundFailures.single() is IOException)
    }

    /** An uncaught failure would crash the app, and with it this test. */
    @Test
    fun aSheetThatCantBeReadStaysClosedAndCanBeTriedAgain() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0)))
        readingOptionsFails = true
        val vm = viewModel()
        val period = vm.state { it.pages.isNotEmpty() }.pages.last().period

        vm.openRecap(period)
        withTimeout(3_000) { while (!readingOptionsFailed) delay(20) }
        // Once the main thread has moved on, the failed attempt has finished.
        withContext(Dispatchers.Main) {}
        assertNull(vm.state { true }.recap)

        readingOptionsFails = false
        vm.openRecap(period)
        assertEquals(RecapOptions(), vm.state { it.recap != null }.recap!!.options)
    }

    private suspend fun viewModel(): SpendingViewModel {
        val factory = viewModelFactory {
            initializer {
                SpendingViewModel(
                    mealDao = db.mealDao(),
                    placeDao = db.placeDao(),
                    photos = photos,
                    recapDrawing = { _, text, _ ->
                        drawingStarted = true
                        try {
                            drawingWaitsFor?.await()
                        } catch (e: CancellationException) {
                            drawingDropped = true
                            throw e
                        }
                        if (drawingFails) {
                            drawingFailed = true
                            throw IOException("No room")
                        }
                        drawn += text
                        File("/spending-test/recap.png")
                    },
                    savedRecapOptions = flow {
                        if (readingOptionsFails) {
                            readingOptionsFailed = true
                            throw IOException("Can't read")
                        }
                        emit(savedOptions)
                    },
                    saveRecapOptions = {
                        if (savingFails) throw IOException("No room")
                        saves += it
                    },
                    appScope = appScope,
                    clock = { today },
                )
            }
        }
        return withContext(Dispatchers.Main) {
            ViewModelProvider.create(store, factory)["spending-${System.nanoTime()}", SpendingViewModel::class]
        }
    }

    private suspend fun SpendingViewModel.state(until: (SpendingUiState) -> Boolean): SpendingUiState =
        withTimeout(3_000) { uiState.first { !it.isLoading && until(it) } }

    private fun meal(
        eatenAt: LocalDateTime,
        price: Int = 50,
        name: String? = "Tea",
        photo: String? = null,
        placeId: Long? = null,
    ) = Meal(eatenAt = eatenAt, photo = photo, name = name, price = price, placeId = placeId)
}
