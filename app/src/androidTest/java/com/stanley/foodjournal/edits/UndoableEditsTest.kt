package com.stanley.foodjournal.edits

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class UndoableEditsTest {
    private lateinit var db: FoodJournalDatabase
    private lateinit var root: File
    private lateinit var photos: PhotoStore
    private lateinit var scope: CoroutineScope
    private lateinit var edits: UndoableEdits

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FoodJournalDatabase::class.java).build()
        root = File(context.cacheDir, "undo-test-${System.nanoTime()}")
        photos = PhotoStore(root).also { it.ensureFolders() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        edits = UndoableEdits(db.mealDao(), db.placeDao(), photos, scope, window = 400.milliseconds)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun deleteKeepsPhotoAndPlaceUntilTheBarCloses() = runBlocking {
        val (meal, photo, placeId) = savedMealWithPhotoAndPlace()

        edits.deleteMeal(meal)
        awaitPending { it is PendingEdit.MealDeleted }

        assertNull(db.mealDao().get(meal.id))
        edits.removeEmptyPlaces()
        assertNotNull(db.placeDao().get(placeId))
        assertTrue(photos.photoFile(photo).exists())

        awaitPending { it == null }

        assertFalse(photos.photoFile(photo).exists())
        assertFalse(photos.thumbFile(photo).exists())
        assertNull(db.placeDao().get(placeId))
    }

    @Test
    fun undoPutsTheMealBackWithItsPlace() = runBlocking {
        val (meal, photo, placeId) = savedMealWithPhotoAndPlace()

        edits.deleteMeal(meal)
        awaitPending { it is PendingEdit.MealDeleted }
        edits.undo()
        awaitPending { it == null }
        delay(600)

        assertEquals(meal, db.mealDao().get(meal.id))
        assertEquals(placeId, db.mealDao().get(meal.id)?.placeId)
        assertTrue(photos.photoFile(photo).exists())
    }

    @Test
    fun renamingOnlyTheCapitalizationKeepsThePlace() = runBlocking {
        val id = db.placeDao().insert(Place(name = "starbucks"))
        db.mealDao().insert(meal(placeId = id))

        val result = edits.renamePlace(db.placeDao().get(id)!!, "Starbucks")

        assertEquals(RenameResult.Renamed, result)
        assertEquals("Starbucks", db.placeDao().get(id)?.name)
        assertNull(edits.pending.value)
    }

    @Test
    fun renamingToAnotherPlacesNameMergesAndCanBeUndone() = runBlocking {
        val awayId = db.placeDao().insert(Place(name = "Lin Dong Fang"))
        val survivorId = db.placeDao().insert(Place(name = "Beef Noodles"))
        val mealId = db.mealDao().insert(meal(placeId = awayId))

        val result = edits.renamePlace(db.placeDao().get(awayId)!!, "beef noodles")

        assertEquals(survivorId, (result as RenameResult.Merged).survivor.id)
        assertEquals(survivorId, db.mealDao().get(mealId)?.placeId)
        assertTrue(edits.pending.value is PendingEdit.PlacesMerged)

        edits.undo()
        awaitPending { it == null }
        delay(200)

        assertEquals("Lin Dong Fang", db.placeDao().get(awayId)?.name)
        assertEquals(awayId, db.mealDao().get(mealId)?.placeId)
    }

    private suspend fun savedMealWithPhotoAndPlace(): Triple<Meal, String, Long> {
        val photo = photos.newName()
        photos.photoFile(photo).writeText("photo")
        photos.thumbFile(photo).writeText("thumb")
        val placeId = db.placeDao().insert(Place(name = "Dumplings"))
        val id = db.mealDao().insert(meal(placeId = placeId, photo = photo))
        return Triple(db.mealDao().get(id)!!, photo, placeId)
    }

    private suspend fun awaitPending(condition: (PendingEdit?) -> Boolean) {
        withTimeout(3_000) {
            while (!condition(edits.pending.value)) delay(20)
        }
    }

    private fun meal(placeId: Long? = null, photo: String? = null) = Meal(
        eatenAt = LocalDateTime.of(2026, 11, 3, 12, 0),
        name = "Dumplings",
        price = 120,
        placeId = placeId,
        photo = photo,
    )
}
