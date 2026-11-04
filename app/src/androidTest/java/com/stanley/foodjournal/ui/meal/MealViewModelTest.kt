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
import com.stanley.foodjournal.edits.PendingEdit
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.photos.PhotoMetadata
import com.stanley.foodjournal.photos.PhotoProcessing
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
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
        runBlocking {
            withContext(Dispatchers.Main) { store.clear() }
            scope.coroutineContext.job.cancelAndJoin()
            // A query the ViewModel's place list started may still be running on Room's own
            // threads; closing the database under it would crash the test process.
            delay(300)
        }
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
        awaitTrue { main { vm.uiState!!.placeName } == "Lin Dong Fang" }
    }

    @Test
    fun theMealFollowsAPlaceDeletionAndItsUndo() = runBlocking {
        val placeId = db.placeDao().insert(Place(name = "Kiosk"))
        db.mealDao().insert(Meal(eatenAt = noon, name = "Tea", price = 50, placeId = placeId))
        val vm = newMeal()
        main { vm.onPlaceSelected(placeId) }
        val place = db.placeDao().get(placeId)!!

        main { vm.deletePlace(place) }
        assertNull(main { vm.uiState!!.draft.placeId })
        awaitTrue { edits.pending.value is PendingEdit.PlaceDeleted }

        edits.undo()
        awaitTrue { main { vm.uiState!!.draft.placeId } == placeId }
        awaitTrue { main { vm.uiState!!.placeName } == "Kiosk" }
    }

    @Test
    fun mealsHereLeavesAsBackDoesAndKeepsValidEdits() = runBlocking {
        val placeId = db.placeDao().insert(Place(name = "Kiosk"))
        val mealId = db.mealDao().insert(Meal(eatenAt = noon, name = "Tea", price = 50, placeId = placeId))
        val vm = savedMeal(mealId)
        awaitTrue { main { vm.mealsHerePlaceId } == placeId }

        main { vm.onNameChange("Milk tea") }
        assertEquals(placeId, main { vm.leaveForPlace() })

        assertTrue(vm.finished.value)
        awaitTrue { db.mealDao().get(mealId)?.name == "Milk tea" }
        // Already closing: a second tap opens nothing.
        assertNull(main { vm.leaveForPlace() })
    }

    @Test
    fun mealsHereNeverOffersAPlaceBackWouldDrop() = runBlocking {
        val kiosk = db.placeDao().insert(Place(name = "Kiosk"))
        val stall = db.placeDao().insert(Place(name = "Stall"))
        val mealId = db.mealDao().insert(Meal(eatenAt = noon, name = "Tea", price = 50, placeId = kiosk))
        val vm = savedMeal(mealId)
        awaitTrue { main { vm.mealsHerePlaceId } == kiosk }

        // Without a price, Back keeps the saved meal, still at the kiosk.
        main { vm.onPriceChange("") }
        assertEquals(kiosk, main { vm.mealsHerePlaceId })

        main { vm.onPlaceSelected(stall) }
        assertNull(main { vm.mealsHerePlaceId })

        // A valid meal at the stall is what Back would save.
        main { vm.onPriceChange("60") }
        assertEquals(stall, main { vm.mealsHerePlaceId })
    }

    @Test
    fun aNewMealOrANewPlaceHasNoMealsHere() = runBlocking {
        val placeId = db.placeDao().insert(Place(name = "Kiosk"))
        val fresh = newMeal()
        main {
            fresh.onSkipPhoto()
            fresh.onPlaceSelected(placeId)
        }
        delay(200)
        assertNull(main { fresh.mealsHerePlaceId })
        assertNull(main { fresh.leaveForPlace() })
        assertFalse(fresh.finished.value)

        val mealId = db.mealDao().insert(Meal(eatenAt = noon, name = "Tea", price = 50, placeId = placeId))
        val saved = savedMeal(mealId)
        awaitTrue { main { saved.mealsHerePlaceId } == placeId }
        main { saved.onNewPlace("Corner stall") }
        assertNull(main { saved.mealsHerePlaceId })
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
        awaitTrue { photos.pendingPhotoFile(second).exists() && !main { vm.uiState!!.photoProcessing } }
        processing.finish(first, ok = true)
        // The first photo's files are written as it finishes, then dropped as no longer shown.
        delay(500)

        assertFalse(photos.pendingPhotoFile(first).exists())
        assertFalse(photos.pendingThumbFile(first).exists())
        assertTrue(photos.pendingPhotoFile(second).exists())
        assertEquals(second, main { vm.uiState!!.draft.photo })
    }

    private val noon = LocalDateTime.of(2026, 11, 3, 12, 0)

    @Test
    fun stepsFollowTheJournalsOrderAndStopAtBothEnds() = runBlocking {
        val first = insertMeal(LocalDateTime.of(2026, 10, 26, 9, 0), "First")
        val last = insertMeal(LocalDateTime.of(2026, 11, 1, 20, 0), "Last of the week")
        val next = insertMeal(LocalDateTime.of(2026, 11, 2, 8, 0), "First of the next week")

        val middle = savedMeal(last)
        awaitTrue { main { middle.uiState?.steps } != null }
        assertEquals(first, main { middle.uiState!!.steps!!.previous })
        main { middle.stepToNext() }
        awaitTrue { middle.stepTo.value == next }
        assertTrue(main { middle.uiState!!.closing })

        val newest = savedMeal(next)
        awaitTrue { main { newest.uiState?.steps } != null }
        main { newest.stepToNext() }
        delay(200)
        assertNull(newest.stepTo.value)
        assertFalse(main { newest.uiState!!.closing })

        val oldest = savedMeal(first)
        awaitTrue { main { oldest.uiState?.steps } != null }
        main { oldest.stepToPrevious() }
        delay(200)
        assertNull(oldest.stepTo.value)

        // A new meal has nowhere to step to.
        val new = newMeal()
        assertNull(main { new.uiState!!.steps })
    }

    @Test
    fun steppingKeepsValidEditsAndDropsTheRest() = runBlocking {
        val one = insertMeal(LocalDateTime.of(2026, 11, 2, 8, 0), "One")
        val two = insertMeal(LocalDateTime.of(2026, 11, 3, 8, 0), "Two")

        val edited = savedMeal(one)
        awaitTrue { main { edited.uiState?.steps } != null }
        main {
            edited.onNameChange("Edited")
            edited.stepToNext()
        }
        awaitTrue { edited.stepTo.value == two }
        // The step is only asked for once the save is done.
        assertEquals("Edited", db.mealDao().get(one)!!.name)

        val invalid = savedMeal(two)
        awaitTrue { main { invalid.uiState?.steps } != null }
        main {
            invalid.onPriceChange("")
            invalid.stepToPrevious()
        }
        awaitTrue { invalid.stepTo.value == one }
        assertEquals(100, db.mealDao().get(two)!!.price)
    }

    @Test
    fun steppingWaitsForAPhotoStillBeingProcessed() = runBlocking {
        val one = insertMeal(LocalDateTime.of(2026, 11, 2, 8, 0), "One")
        val two = insertMeal(LocalDateTime.of(2026, 11, 3, 8, 0), "Two")

        val vm = savedMeal(one)
        awaitTrue { main { vm.uiState?.steps } != null }
        val photo = capture(vm)
        main { vm.stepToNext() }
        delay(200)
        assertNull(vm.stepTo.value)
        assertTrue(main { vm.uiState!!.closing })

        processing.finish(photo, ok = true)
        awaitTrue { vm.stepTo.value == two }
        assertEquals(photo, db.mealDao().get(one)!!.photo)
    }

    private suspend fun insertMeal(eatenAt: LocalDateTime, name: String): Long =
        db.mealDao().insert(Meal(eatenAt = eatenAt, name = name, price = 100))

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
