package com.stanley.foodjournal.ui.map

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.R
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.logic.DistrictMap
import com.stanley.foodjournal.ui.theme.FoodJournalTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class MapScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: FoodJournalDatabase
    private val noon = LocalDateTime.of(2026, 11, 3, 12, 0)

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(context, FoodJournalDatabase::class.java).build()
        districts
    }

    @After
    fun close() = db.close()

    @Test
    fun aPinIsAButtonThatOpensItsPlace() {
        val noodles = runBlocking {
            db.placeDao().insert(Place(name = "Noodles", latitude = 25.0174, longitude = 121.5340)).also { id ->
                db.mealDao().insert(Meal(eatenAt = noon, name = "Soup", price = 100, placeId = id))
                db.mealDao().insert(Meal(eatenAt = noon, name = "Tea", price = 50, placeId = id))
            }
        }
        val opened = mutableListOf<Long>()
        show(onOpenPlace = { opened += it })

        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodes(hasContentDescription("Noodles, 2 meals")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Noodles, 2 meals").performClick()

        assertEquals(listOf(noodles), opened)
    }

    @Test
    fun theSelectedDistrictsLineOpensItsMeals() {
        runBlocking {
            val id = db.placeDao().insert(Place(name = "Noodles", latitude = 25.0174, longitude = 121.5340))
            db.mealDao().insert(Meal(eatenAt = noon, name = "Soup", price = 100, placeId = id))
        }
        val daan = districts.districtContaining(25.0174, 121.5340)!!
        val opened = mutableListOf<String>()
        val vm = show(onOpenDistrict = { opened += it }, selected = daan.code)

        val line = hasText("臺北市 大安區", substring = true) and hasText("1 meal", substring = true)
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodes(line).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(line).performClick()

        assertEquals(listOf(daan.code), opened)
        assertEquals(daan.code, vm.openSelected())
    }

    @Test
    fun withoutAnyLocationTheTabShowsItsEmptyState() {
        runBlocking { db.mealDao().insert(Meal(eatenAt = noon, name = "Soup", price = 100)) }
        show()

        val empty = context.getString(R.string.map_empty)
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodes(hasText(empty)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun show(
        onOpenPlace: (Long) -> Unit = {},
        onOpenDistrict: (String) -> Unit = {},
        selected: String? = null,
    ): MapViewModel {
        val vm = MapViewModel(
            SavedStateHandle(mapOf("selectedDistrict" to selected)),
            db.mealDao(),
            db.placeDao(),
            { districts },
            today = { LocalDate.of(2026, 11, 4) },
        )
        compose.setContent {
            FoodJournalTheme {
                MapScreen(onOpenMeal = {}, onOpenPlace = onOpenPlace, onOpenDistrict = onOpenDistrict, viewModel = vm)
            }
        }
        return vm
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L

        /** The bundled districts, read once for all the tests, off the main thread. */
        val districts: DistrictMap by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            DistrictMap.parse(context.assets.open("districts.json").use { it.readBytes().decodeToString() })
        }
    }
}
