package com.stanley.foodjournal.ui.journal

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.photos.PhotoStore
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
class JournalViewModelTest {
    private lateinit var db: FoodJournalDatabase
    private val photos = PhotoStore(File("/journal-test"))
    private val store = ViewModelStore()

    // A Wednesday, so the current week is Nov 2 – 8.
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
        // Stop the ViewModel's query before the database closes under it.
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        db.close()
    }

    @Test
    fun tilesCarryTheirPhotoFilesOrTheirName() = runBlocking {
        val withPhoto = db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 12, 0), photo = "a.jpg", name = "Soup"))
        val withoutPhoto = db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 3, 1, 10), name = "Late-night snack"))

        val tiles = weeks().single().tiles

        assertEquals(listOf(withPhoto, withoutPhoto), tiles.map { it.mealId })
        assertEquals(photos.thumbFile("a.jpg"), tiles[0].thumb)
        assertEquals(photos.photoFile("a.jpg"), tiles[0].photo)
        assertEquals("Mon", tiles[0].weekday)
        assertNull(tiles[1].thumb)
        assertNull(tiles[1].photo)
        assertEquals("Late-night snack", tiles[1].name)
        // A 01:10 snack counts toward the day it was eaten.
        assertEquals("Tue", tiles[1].weekday)
    }

    @Test
    fun onlyTheWeekHoldingTodayIsCurrent() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 2, 8, 0), price = 80))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 11, 4, 12, 0), price = 1_050))
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 27, 19, 0), price = 2_340))

        val weeks = weeks()

        assertEquals(listOf("Nov 2 – 8", "Oct 26 – Nov 1"), weeks.map { it.range })
        assertEquals(listOf("1,130", "2,340"), weeks.map { it.total })
        assertTrue(weeks[0].isCurrent)
        assertFalse(weeks[1].isCurrent)
    }

    @Test
    fun noWeekIsCurrentWhenThisWeekHasNoMeals() = runBlocking {
        db.mealDao().insert(meal(LocalDateTime.of(2026, 10, 27, 19, 0)))

        assertFalse(weeks().single().isCurrent)
    }

    private suspend fun weeks(): List<JournalWeek> {
        val factory = viewModelFactory { initializer { JournalViewModel(db.mealDao(), photos, today = { today }) } }
        val vm = withContext(Dispatchers.Main) {
            ViewModelProvider.create(store, factory)["journal-${System.nanoTime()}", JournalViewModel::class]
        }
        return withTimeout(3_000) { vm.uiState.first { !it.isLoading }.weeks }
    }

    private fun meal(eatenAt: LocalDateTime, photo: String? = null, name: String? = "Tea", price: Int = 50) =
        Meal(eatenAt = eatenAt, photo = photo, name = name, price = price)
}
