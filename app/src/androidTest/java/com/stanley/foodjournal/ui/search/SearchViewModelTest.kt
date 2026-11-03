package com.stanley.foodjournal.ui.search

import androidx.lifecycle.SavedStateHandle
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
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.ui.navigation.SearchRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class SearchViewModelTest {
    private lateinit var db: FoodJournalDatabase
    private val photos = PhotoStore(File("/search-test"))
    private val store = ViewModelStore()
    private val today = LocalDate.of(2026, 11, 4)

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
    fun noMealsMeansTheEmptyState() = runBlocking {
        assertTrue(viewModel().state { true }.isEmpty)
    }

    @Test
    fun beforeTypingEveryMealShowsNewestFirstWithItsTotal() = runBlocking {
        val noodles = db.placeDao().insert(Place(name = "林家乾麵"))
        val soup = db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), "Beef noodle soup", 180, placeId = noodles))
        val latte = db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 9, 0), "Latte", 140, photo = "a.jpg", favorite = true))

        val state = viewModel().state { it.results.size == 2 }

        assertEquals(listOf(latte, soup), state.results.map { it.mealId })
        assertEquals("320", state.total)
        assertEquals(2, state.count)
        assertEquals("Tue, Nov 3", state.results[0].detail)
        assertEquals("Mon, Nov 2 · 林家乾麵", state.results[1].detail)
        assertEquals(photos.thumbFile("a.jpg"), state.results[0].thumb)
        assertTrue(state.results[0].isFavorite)
        assertNull(state.placeName)
    }

    @Test
    fun typingNarrowsTheResultsAndTheirTotal() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), "Beef noodle soup", 180))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 9, 0), "Latte", 140))
        val vm = viewModel()
        vm.state { it.results.size == 2 }

        withContext(Dispatchers.Main) { vm.onQueryChange("soup") }

        val state = vm.state { it.results.size == 1 }
        assertEquals("180", state.total)
        assertEquals("soup", withContext(Dispatchers.Main) { vm.query })
    }

    @Test
    fun aRouteWithAPlaceAndDatesStartsWithJustThem() = runBlocking {
        val noodles = db.placeDao().insert(Place(name = "林家乾麵"))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 30, 12, 0), "Soup", 180, placeId = noodles))
        val inRange = db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), "Soup", 200, placeId = noodles))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 9, 0), "Latte", 140))

        val route = SearchRoute(noodles, LocalDate.of(2026, 11, 1).toEpochDay(), today.toEpochDay())
        val state = viewModel(route = route).state { it.placeName != null }

        assertEquals("林家乾麵", state.placeName)
        assertEquals("Nov 1 – 4", state.dates)
        assertEquals(listOf(inRange), state.results.map { it.mealId })
        assertFalse(state.favoritesOnly)
    }

    @Test
    fun clearingThePlaceAndDatesWidensTheSearch() = runBlocking {
        val noodles = db.placeDao().insert(Place(name = "林家乾麵"))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 30, 12, 0), "Soup", 180, placeId = noodles))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 9, 0), "Latte", 140))
        val vm = viewModel(route = SearchRoute(noodles, today.toEpochDay(), today.toEpochDay()))
        vm.state { it.placeName != null }

        withContext(Dispatchers.Main) { vm.clearPlace() }
        // Still only today, which has no meals.
        val noChip = vm.state { it.placeName == null }
        assertEquals("Nov 4", noChip.dates)
        assertEquals(0, noChip.count)

        withContext(Dispatchers.Main) { vm.setDates(null, null) }
        val state = vm.state { it.dates == null }
        assertEquals(2, state.count)
    }

    @Test
    fun oneDayPickedIsThatDayAlone() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 23, 0), "Snack", 60))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 4, 1, 0), "Noodles", 90))
        val vm = viewModel()
        vm.state { it.count == 2 }

        withContext(Dispatchers.Main) { vm.setDates(LocalDate.of(2026, 11, 3), null) }

        val state = vm.state { it.dates != null }
        assertEquals("Nov 3", state.dates)
        assertEquals(1, state.count)
    }

    @Test
    fun favoritesOnly() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), "Soup", 180, favorite = true))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 9, 0), "Latte", 140))
        val vm = viewModel()
        vm.state { it.count == 2 }

        withContext(Dispatchers.Main) { vm.toggleFavorites() }

        val state = vm.state { it.favoritesOnly }
        assertEquals(1, state.count)
    }

    @Test
    fun aPlaceThatIsGoneLeavesNoChipAndFiltersNothing() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), "Soup", 180))

        val state = viewModel(route = SearchRoute(placeId = 99)).state { it.results.isNotEmpty() }

        assertNull(state.placeName)
        assertEquals(1, state.count)
    }

    @Test
    fun aSearchComesBackFromTheSavedState() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), "Soup", 180, favorite = true))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 9, 0), "Latte", 140, favorite = true))
        val saved = SavedStateHandle(mapOf("text" to "latte", "favorites" to true))

        val vm = viewModel(savedState = saved)
        val state = vm.state { it.count == 1 }

        assertEquals("latte", withContext(Dispatchers.Main) { vm.query })
        assertTrue(state.favoritesOnly)
    }

    private suspend fun viewModel(
        route: SearchRoute = SearchRoute(),
        savedState: SavedStateHandle = SavedStateHandle(),
    ): SearchViewModel {
        val factory = viewModelFactory {
            initializer { SearchViewModel(savedState, route, db.mealDao(), db.placeDao(), photos, today = { today }) }
        }
        return withContext(Dispatchers.Main) {
            ViewModelProvider.create(store, factory)["search-${System.nanoTime()}", SearchViewModel::class]
        }
    }

    private suspend fun SearchViewModel.state(until: (SearchUiState) -> Boolean): SearchUiState =
        withTimeout(3_000) { uiState.first { !it.isLoading && until(it) } }

    private fun meal(
        eatenAt: LocalDateTime,
        name: String?,
        price: Int,
        photo: String? = null,
        placeId: Long? = null,
        favorite: Boolean = false,
    ) = Meal(eatenAt = eatenAt, photo = photo, name = name, price = price, placeId = placeId, isFavorite = favorite)
}
