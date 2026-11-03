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
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class SpendingViewModelTest {
    private lateinit var db: FoodJournalDatabase
    private val photos = PhotoStore(File("/spending-test"))
    private val store = ViewModelStore()

    // A Wednesday, so the current week is Nov 2 – 8.
    private var today = LocalDate.of(2026, 11, 4)

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

        assertEquals(listOf(PlaceRow("林家乾麵", 2, ShareRow("400", 0.8f))), figures.places)
        assertEquals(OtherRow(1, ShareRow("100", 0.2f)), figures.other)
        assertEquals(listOf("0", "400", "0", "100"), figures.bands.map { it.row.total })
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

    private suspend fun viewModel(): SpendingViewModel {
        val factory = viewModelFactory {
            initializer { SpendingViewModel(db.mealDao(), db.placeDao(), photos, clock = { today }) }
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
