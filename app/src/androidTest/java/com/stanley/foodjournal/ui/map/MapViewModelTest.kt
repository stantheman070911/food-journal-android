package com.stanley.foodjournal.ui.map

import android.content.Context
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
import com.stanley.foodjournal.logic.DistrictMap
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
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class MapViewModelTest {
    private lateinit var db: FoodJournalDatabase
    private val store = ViewModelStore()
    private val noon = LocalDateTime.of(2026, 11, 3, 12, 0)

    // NTU's main gate is in 臺北市 大安區; Taipei 101 is in 臺北市 信義區.
    private val ntu = 25.0174 to 121.5340
    private val tower = 25.0339 to 121.5645

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FoodJournalDatabase::class.java).build()
        // Read before any test waits on the map, off the main thread, as the app reads it.
        districts
    }

    @After
    fun close() {
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        db.close()
    }

    @Test
    fun noMealWithALocationIsTheEmptyState() = runBlocking<Unit> {
        db.mealDao().insert(meal("Soup"))

        assertTrue(viewModel().state { true }.isEmpty)
    }

    @Test
    fun placesAndLoneMealsBecomePinsAndMealsAbroadAreCounted() = runBlocking<Unit> {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = ntu.first, longitude = ntu.second))
        val tokyo = db.placeDao().insert(Place(name = "Tokyo Ramen", latitude = 35.68, longitude = 139.76))
        db.mealDao().insert(meal("Soup", placeId = noodles))
        db.mealDao().insert(meal("More soup", placeId = noodles))
        db.mealDao().insert(meal("Ramen", placeId = tokyo))
        db.mealDao().insert(meal(null).copy(latitude = tower.first, longitude = tower.second))

        val state = viewModel().state { it.meals.pins.size == 2 }

        val place = state.meals.pins.single { it.placeId != null }
        assertEquals("Noodles", place.name)
        assertEquals(2, place.meals)
        // A lone meal without a name is named by its day.
        assertEquals("Tue, Nov 3", state.meals.pins.single { it.mealId != null }.name)
        assertEquals(1, state.meals.outsideTaiwan)
        assertFalse(state.isEmpty)
    }

    @Test
    fun aTapSelectsADistrictAndASecondTapOpensItsMeals() = runBlocking<Unit> {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = ntu.first, longitude = ntu.second))
        db.mealDao().insert(meal("Soup", placeId = noodles))
        db.mealDao().insert(meal("Tea", placeId = noodles))
        val vm = viewModel()
        vm.state { it.meals.pins.isNotEmpty() }

        assertNull(main { vm.onTap(ntu.first, ntu.second) })
        val selected = vm.state { it.selected != null }.selected!!
        assertEquals("臺北市 大安區", selected.district.name)
        assertEquals(2, selected.meals)

        assertEquals(selected.district.code, main { vm.onTap(25.0200, 121.5400) })
        assertEquals(selected.district.code, main { vm.openSelected() })
    }

    @Test
    fun aDistrictWithoutMealsCanBeSelectedButOpensNothing() = runBlocking<Unit> {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = ntu.first, longitude = ntu.second))
        db.mealDao().insert(meal("Soup", placeId = noodles))
        val vm = viewModel()
        vm.state { it.meals.pins.isNotEmpty() }

        main { vm.onTap(tower.first, tower.second) }
        val selected = vm.state { it.selected != null }.selected!!
        assertEquals("臺北市 信義區", selected.district.name)
        assertEquals(0, selected.meals)
        assertNull(main { vm.onTap(tower.first, tower.second) })
        assertNull(main { vm.openSelected() })
    }

    @Test
    fun aTapOutsideTaiwanClearsTheSelection() = runBlocking<Unit> {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = ntu.first, longitude = ntu.second))
        db.mealDao().insert(meal("Soup", placeId = noodles))
        val vm = viewModel()
        vm.state { it.meals.pins.isNotEmpty() }
        main { vm.onTap(ntu.first, ntu.second) }
        vm.state { it.selected != null }

        main { vm.onTap(24.5, 119.5) } // The Taiwan Strait

        vm.state { it.selected == null }
    }

    @Test
    fun theSelectionIsKeptInTheSavedState() = runBlocking<Unit> {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = ntu.first, longitude = ntu.second))
        db.mealDao().insert(meal("Soup", placeId = noodles))
        val saved = SavedStateHandle()
        val first = viewModel(saved)
        first.state { it.meals.pins.isNotEmpty() }
        main { first.onTap(ntu.first, ntu.second) }
        first.state { it.selected != null }

        val again = viewModel(saved).state { it.selected != null }

        assertEquals("臺北市 大安區", again.selected!!.district.name)
    }

    private suspend fun viewModel(savedState: SavedStateHandle = SavedStateHandle()): MapViewModel {
        val factory = viewModelFactory {
            initializer { MapViewModel(savedState, db.mealDao(), db.placeDao(), { districts }, today = { LocalDate.of(2026, 11, 4) }) }
        }
        return withContext(Dispatchers.Main) {
            ViewModelProvider.create(store, factory)["map-${System.nanoTime()}", MapViewModel::class]
        }
    }

    private suspend fun MapViewModel.state(until: (MapUiState) -> Boolean): MapUiState =
        withTimeout(5_000) { uiState.first { !it.isLoading && until(it) } }

    private suspend fun <T> main(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private fun meal(name: String?, placeId: Long? = null) =
        Meal(eatenAt = noon, photo = if (name == null) "a.jpg" else null, name = name, price = 100, placeId = placeId)

    private companion object {
        /** The bundled districts, read once for all the tests, off the main thread. */
        val districts: DistrictMap by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            DistrictMap.parse(context.assets.open("districts.json").use { it.readBytes().decodeToString() })
        }
    }
}
