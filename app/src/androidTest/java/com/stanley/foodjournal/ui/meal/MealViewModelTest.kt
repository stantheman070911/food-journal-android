package com.stanley.foodjournal.ui.meal

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.photos.PhotoMetadata
import com.stanley.foodjournal.photos.PhotoProcessing
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class MealViewModelTest {
    private lateinit var context: Context
    private lateinit var db: FoodJournalDatabase
    private lateinit var root: File
    private lateinit var photos: PhotoStore
    private lateinit var scope: CoroutineScope
    private lateinit var edits: UndoableEdits
    private lateinit var processing: FakeProcessing
    private val store = ViewModelStore()

    // One thousandth of a degree of latitude is about 111 m. Photo locations have no accuracy.
    private val noodlesSpot = LocationFix(25.0330, 121.5297)
    private val cafeSpot = LocationFix(25.0438, 121.5297)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FoodJournalDatabase::class.java).build()
        root = File(context.cacheDir, "meal-vm-test-${System.nanoTime()}")
        photos = PhotoStore(root).also { it.ensureFolders() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        edits = UndoableEdits(db.mealDao(), db.placeDao(), photos, scope, window = 1_500.milliseconds)
        processing = FakeProcessing(photos)
    }

    @After
    fun tearDown() {
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        scope.cancel()
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun aDoubleSaveStoresTheMealOnce() = runBlocking {
        val vm = newMeal()
        main {
            vm.onSkipPhoto()
            vm.onNameChange("Soup")
            vm.onPriceChange("280")
            // The keyboard's ✓ and a tap on Save, or two quick taps.
            vm.save()
            vm.save()
        }

        awaitTrue { mealCount() == 1 }
        delay(300)
        assertEquals(1, mealCount())
        assertTrue(vm.finished.value)
        assertTrue(main { vm.uiState!!.closing })
    }

    @Test
    fun saveThenBackOrDeleteStoresTheMealOnce() = runBlocking {
        val vm = newMeal()
        main {
            vm.onSkipPhoto()
            vm.onNameChange("Soup")
            vm.onPriceChange("280")
            vm.save()
            vm.onBack()
            vm.delete()
        }

        awaitTrue { mealCount() == 1 }
        delay(300)
        assertEquals(1, mealCount())
        assertNull(edits.pending.value)
    }

    @Test
    fun aFailedNewPhotoIsDroppedWithAMessage() = runBlocking {
        val vm = newMeal()
        val name = capture(vm)

        processing.finish(name, ok = false)

        awaitTrue { main { vm.uiState!!.photoFailed } }
        val state = main { vm.uiState!! }
        assertNull(state.draft.photo)
        assertFalse(state.photoProcessing)
        assertFalse(photos.pendingPhotoFile(name).exists())
    }

    @Test
    fun aFailedReplacementPhotoKeepsTheSavedOne() = runBlocking {
        val original = photos.newName()
        photos.photoFile(original).writeText("photo")
        photos.thumbFile(original).writeText("thumb")
        val mealId = db.mealDao().insert(Meal(eatenAt = noon, photo = original, name = "Soup", price = 280))
        val vm = savedMeal(mealId)
        awaitTrue { main { vm.uiState != null } }

        main { vm.onRetakePhoto() }
        val replacement = capture(vm)
        main { vm.onNameChange("Beef soup") }
        processing.finish(replacement, ok = false)
        awaitTrue { main { vm.uiState!!.photoFailed } }

        assertEquals(original, main { vm.uiState!!.draft.photo })
        assertFalse(main { vm.uiState!!.draft.photoIsPending })
        main { vm.onBack() }

        awaitTrue { db.mealDao().get(mealId)?.name == "Beef soup" }
        delay(300)
        assertEquals(original, db.mealDao().get(mealId)?.photo)
        assertTrue(photos.photoFile(original).exists())
    }

    @Test
    fun saveDuringProcessingWaitsAndThenSaves() = runBlocking {
        val vm = newMeal()
        val name = capture(vm)
        main {
            vm.onPriceChange("120")
            vm.save()
        }

        assertTrue(main { vm.uiState!!.savePending })
        assertFalse(vm.finished.value)
        assertEquals(0, mealCount())

        processing.finish(name, ok = true)

        awaitTrue { vm.finished.value }
        awaitTrue { mealCount() == 1 }
        val saved = db.mealDao().get(1)!!
        assertEquals(name, saved.photo)
        assertTrue(photos.photoFile(name).exists())
    }

    @Test
    fun saveDuringProcessingKeepsTheMealOpenWhenThePhotoFails() = runBlocking {
        val vm = newMeal()
        val name = capture(vm)
        main {
            vm.onPriceChange("120")
            vm.save()
        }

        processing.finish(name, ok = false)

        awaitTrue { main { vm.uiState!!.photoFailed } }
        val state = main { vm.uiState!! }
        assertFalse(vm.finished.value)
        assertFalse(state.savePending)
        assertEquals("120", state.draft.priceText)
        delay(300)
        assertEquals(0, mealCount())
    }

    @Test
    fun aGalleryPhotosDateAndLocationArriveLateAndPlaceTheMeal() = runBlocking {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = 25.0331, longitude = 121.5297))
        db.placeDao().insert(Place(name = "Cafe", latitude = cafeSpot.latitude, longitude = cafeSpot.longitude))
        val vm = newMeal()
        val takenAt = LocalDateTime.of(2026, 11, 1, 19, 5)
        processing.metadata = PhotoMetadata(takenAt, noodlesSpot)

        main { vm.onPicked(Uri.parse("content://test/photo/1")) }
        val name = main { vm.uiState!!.draft.photo!! }
        assertNull(main { vm.uiState!!.draft.placeId })
        processing.finish(name, ok = true)

        awaitTrue { main { vm.uiState!!.draft.eatenAt } == takenAt }
        awaitTrue { main { vm.uiState!!.draft.placeId } == noodles }
        assertTrue(main { vm.uiState!!.draft.fromGallery })
    }

    @Test
    fun aCameraMealIsNeverPlacedByLocation() = runBlocking {
        db.placeDao().insert(Place(name = "Noodles", latitude = noodlesSpot.latitude, longitude = noodlesSpot.longitude))
        val vm = newMeal()
        val name = capture(vm)
        processing.finish(name, ok = true)

        awaitTrue { !main { vm.uiState!!.photoProcessing } }
        delay(200)
        assertNull(main { vm.uiState!!.draft.placeId })
    }

    @Test
    fun aPlacePickedByHandIsNotReplacedByAGalleryPhoto() = runBlocking {
        db.placeDao().insert(Place(name = "Noodles", latitude = noodlesSpot.latitude, longitude = noodlesSpot.longitude))
        val market = db.placeDao().insert(Place(name = "Market"))
        val vm = newMeal()
        main { vm.onPlaceSelected(market) }
        processing.metadata = PhotoMetadata(takenAt = null, location = noodlesSpot)

        main { vm.onPicked(Uri.parse("content://test/photo/2")) }
        processing.finish(main { vm.uiState!!.draft.photo!! }, ok = true)

        awaitTrue { main { vm.uiState!!.draft.fromGallery } }
        delay(200)
        assertEquals(market, main { vm.uiState!!.draft.placeId })
    }

    @Test
    fun theMealFollowsAMergeAndItsUndo() = runBlocking {
        val away = db.placeDao().insert(Place(name = "Lin Dong Fang"))
        val survivor = db.placeDao().insert(Place(name = "Beef Noodles"))
        db.mealDao().insert(Meal(eatenAt = noon, name = "Soup", price = 100, placeId = away))
        val vm = newMeal()
        main { vm.onPlaceSelected(away) }

        val awayPlace = db.placeDao().get(away)!!
        main { vm.renamePlace(awayPlace, "beef noodles") }
        awaitTrue { main { vm.uiState!!.draft.placeId } == survivor }
        assertEquals("Beef Noodles", main { vm.uiState!!.placeName })

        edits.undo()
        awaitTrue { main { vm.uiState!!.draft.placeId } == away }
        assertEquals("Lin Dong Fang", main { vm.uiState!!.placeName })
    }

    @Test
    fun aCaptureLandingAfterTheScreenClosedIsDeleted() = runBlocking {
        val vm = newMeal()
        val (name, file) = main { vm.newCaptureFile() }
        file.writeText("capture")

        main {
            vm.onBack()
            vm.onCaptured(name)
        }

        awaitTrue { !file.exists() }
        assertNull(main { vm.uiState!!.draft.photo })
    }

    @Test
    fun aPhotoReplacedWhileStillProcessingIsDeleted() = runBlocking {
        val vm = newMeal()
        val first = capture(vm)
        main { vm.onRetakePhoto() }
        val second = capture(vm)

        processing.finish(second, ok = true)
        processing.finish(first, ok = true)

        awaitTrue { !photos.pendingPhotoFile(first).exists() }
        assertTrue(photos.pendingPhotoFile(second).exists())
        assertEquals(second, main { vm.uiState!!.draft.photo })
    }

    private val noon = LocalDateTime.of(2026, 11, 3, 12, 0)

    private fun newMeal(): MealViewModel = viewModel(mealId = null)

    private fun savedMeal(id: Long): MealViewModel = viewModel(mealId = id)

    private fun viewModel(mealId: Long?): MealViewModel = runBlocking {
        main {
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = MealViewModel(
                    savedState = SavedStateHandle(),
                    mealId = mealId,
                    mealDao = db.mealDao(),
                    placeDao = db.placeDao(),
                    edits = edits,
                    photos = photos,
                    processor = processing,
                    appScope = scope,
                ) as T
            }
            ViewModelProvider.create(store, factory)["meal-${System.nanoTime()}", MealViewModel::class]
        }
    }

    /** Takes a photo; it stays processing until the test finishes it. */
    private suspend fun capture(vm: MealViewModel): String {
        val (name, file) = main { vm.newCaptureFile() }
        file.writeText("capture")
        main { vm.onCaptured(name) }
        return name
    }

    private suspend fun mealCount(): Int = db.mealDao().observeCount().first()

    private suspend fun <T> main(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private suspend fun awaitTrue(condition: suspend () -> Boolean) {
        withTimeout(3_000) {
            while (!condition()) delay(20)
        }
    }

    /** Photo processing the test finishes by hand. A good photo leaves its pending files. */
    private class FakeProcessing(private val store: PhotoStore) : PhotoProcessing {
        private val results = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
        var metadata = PhotoMetadata(takenAt = null, location = null)

        fun finish(name: String, ok: Boolean) {
            result(name).complete(ok)
        }

        override suspend fun fromCapture(name: String): Boolean {
            store.captureFile(name).delete()
            return settle(name)
        }

        override suspend fun readMetadata(uri: Uri): PhotoMetadata = metadata

        override suspend fun fromPicker(uri: Uri, name: String): Boolean = settle(name)

        private suspend fun settle(name: String): Boolean {
            val ok = result(name).await()
            if (ok) {
                store.pendingPhotoFile(name).writeText("photo")
                store.pendingThumbFile(name).writeText("thumb")
            }
            return ok
        }

        private fun result(name: String) = results.getOrPut(name) { CompletableDeferred() }
    }
}
