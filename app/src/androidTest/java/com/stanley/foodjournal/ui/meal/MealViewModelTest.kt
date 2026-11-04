package com.stanley.foodjournal.ui.meal

import android.Manifest
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
import com.stanley.foodjournal.logic.DistrictMap
import com.stanley.foodjournal.edits.PendingEdit
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.location.PhoneLocation
import com.stanley.foodjournal.logic.LocationFix
import com.stanley.foodjournal.logic.LocationSource
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
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var phone: FakeLocation
    private val locationAsked = MutableStateFlow(false)
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
        phone = FakeLocation()
        // Read here, on the test's thread, as the app reads it off the main thread. Read on the
        // main thread instead, it holds up every step a test waits for.
        districts
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
        assertEquals(LocationSource.Gallery, main { vm.uiState!!.draft.locationSource })
    }

    @Test
    fun aCameraMealWithoutAFixIsNotPlaced() = runBlocking {
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

        awaitTrue { main { vm.uiState!!.draft.locationSource } == LocationSource.Gallery }
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

    @Test
    fun aSavedMealShowsItsPlacesDistrictFromTheBundledFile() = runBlocking {
        val inTaipei = db.placeDao().insert(Place(name = "滷三塊", latitude = 25.0264, longitude = 121.5436))
        val abroad = db.placeDao().insert(Place(name = "Tokyo Ramen", latitude = 35.68, longitude = 139.76))
        val noCoordinates = db.placeDao().insert(Place(name = "Corner café"))
        val meal = db.mealDao().insert(Meal(eatenAt = LocalDateTime.of(2026, 11, 2, 8, 0), name = "Soup", price = 100, placeId = inTaipei))

        val vm = savedMeal(meal)
        awaitTrue { main { vm.uiState?.placeDistrict } != null }
        assertEquals("臺北市 大安區", main { vm.uiState!!.placeDistrict })

        val rows = main { vm.placePicker("") }.places.associateBy { it.place.id }
        assertEquals("臺北市 大安區", rows.getValue(inTaipei).detail)
        assertNull(rows.getValue(abroad).detail)
        assertNull(rows.getValue(noCoordinates).detail)
    }

    @Test
    fun aNewMealsPlaceRowHasNoDistrict() = runBlocking {
        val place = db.placeDao().insert(Place(name = "滷三塊", latitude = 25.0264, longitude = 121.5436))

        val vm = newMeal()
        awaitTrue { main { vm.placePicker("") }.places.singleOrNull()?.detail != null }
        main { vm.onPlaceSelected(place) }

        assertNull(main { vm.uiState!!.placeDistrict })
    }

    @Test
    fun theSwitchStartsOnForACameraPhotoOrAGalleryLocationAndOffForThePhoneWhileTyping() = runBlocking {
        phone.fix.value = noodlesSpot
        val camera = newMealChecked()
        processing.finish(capture(camera), ok = true)
        assertEquals(LocationRow(isOn = true, district = null), main { camera.uiState!!.locationRow }?.copy(district = null))
        assertEquals(LocationSource.Camera, main { camera.uiState!!.draft.locationSource })

        val gallery = newMealChecked()
        processing.metadata = PhotoMetadata(takenAt = null, location = cafeSpot)
        main { gallery.onPicked(Uri.parse("content://test/photo/3")) }
        processing.finish(main { gallery.uiState!!.draft.photo!! }, ok = true)
        awaitTrue { main { gallery.uiState!!.draft.locationSource } == LocationSource.Gallery }
        assertEquals(cafeSpot, main { gallery.uiState!!.draft.location })
        assertTrue(main { gallery.uiState!!.locationRow!!.isOn })

        val skipped = newMealChecked()
        skip(skipped)
        assertEquals(LocationSource.Phone, main { skipped.uiState!!.draft.locationSource })
        assertFalse(main { skipped.uiState!!.locationRow!!.isOn })

        // A gallery photo without a location falls back to the phone's, switched off.
        val unlocated = newMealChecked()
        processing.metadata = PhotoMetadata(takenAt = null, location = null)
        main { unlocated.onPicked(Uri.parse("content://test/photo/4")) }
        processing.finish(main { unlocated.uiState!!.draft.photo!! }, ok = true)
        awaitTrue { main { unlocated.uiState!!.draft.locationSource } == LocationSource.Phone }
        assertFalse(main { unlocated.uiState!!.locationRow!!.isOn })
    }

    @Test
    fun aFixArrivingAfterTheCaptureIsWhereThePhotoWasTakenAndPlacesTheMeal() = runBlocking {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = noodlesSpot.latitude, longitude = noodlesSpot.longitude))
        val vm = newMealChecked()
        processing.finish(capture(vm), ok = true)
        assertNull(main { vm.uiState!!.locationRow })

        phone.fix.value = LocationFix(25.0331, 121.5297, accuracyMeters = 12f)

        awaitTrue { main { vm.uiState!!.locationRow } != null }
        awaitTrue { main { vm.uiState!!.draft.placeId } == noodles }
        // A later fix doesn't move where the photo was taken.
        phone.fix.value = cafeSpot
        delay(200)
        assertEquals(25.0331, main { vm.uiState!!.draft.latitude }!!, 0.0)
    }

    @Test
    fun theRowNamesTheLocationsDistrict() = runBlocking {
        // NTU's main gate.
        phone.fix.value = LocationFix(25.0174, 121.5397, accuracyMeters = 10f)
        val vm = newMealChecked()
        processing.finish(capture(vm), ok = true)

        awaitTrue { main { vm.uiState!!.locationRow?.district } == "臺北市 大安區" }
    }

    @Test
    fun theWhileTypingLocationFollowsTheLatestFix() = runBlocking {
        val vm = newMealChecked()
        skip(vm)
        assertNull(main { vm.uiState!!.locationRow })

        phone.fix.value = noodlesSpot
        awaitTrue { main { vm.uiState!!.draft.location } == noodlesSpot }
        phone.fix.value = cafeSpot
        awaitTrue { main { vm.uiState!!.draft.location } == cafeSpot }
    }

    @Test
    fun aFailedFirstPhotoLeavesTheFormFollowingThePhone() = runBlocking {
        val vm = newMealChecked()
        processing.finish(capture(vm), ok = false)
        awaitTrue { main { vm.uiState!!.photoFailed } }

        phone.fix.value = noodlesSpot
        awaitTrue { main { vm.uiState!!.draft.location } == noodlesSpot }
        val row = main { vm.uiState!!.locationRow!! }
        assertFalse(row.isOn)
    }

    @Test
    fun aMealSavedBeforeAnyFixArrivesSavesWithoutALocation() = runBlocking {
        val vm = newMealChecked()
        main {
            vm.onSkipPhoto()
            vm.onNameChange("Soup")
            vm.onPriceChange("280")
            vm.onSaveLocationChange(true)
            vm.save()
        }

        awaitTrue { mealCount() == 1 }
        val saved = db.mealDao().observeAll().first().single()
        assertNull(saved.latitude)
        assertNull(saved.longitude)
    }

    @Test
    fun theSwitchOnSavesTheLocationAndOffDropsItAndThePlaceItFilledIn() = runBlocking {
        val noodles = db.placeDao().insert(Place(name = "Noodles", latitude = noodlesSpot.latitude, longitude = noodlesSpot.longitude))
        phone.fix.value = noodlesSpot
        val on = newMealChecked()
        val name = capture(on)
        processing.finish(name, ok = true)
        awaitTrue { main { on.uiState!!.draft.placeId } == noodles }
        main {
            on.onPriceChange("120")
            on.save()
        }
        awaitTrue { mealCount() == 1 }
        val kept = db.mealDao().observeAll().first().single()
        assertEquals(noodlesSpot.latitude, kept.latitude!!, 0.0)
        assertEquals(noodlesSpot.longitude, kept.longitude!!, 0.0)

        val off = newMealChecked()
        processing.finish(capture(off), ok = true)
        awaitTrue { main { off.uiState!!.draft.placeId } == noodles }
        main { off.onSaveLocationChange(false) }
        assertNull(main { off.uiState!!.draft.placeId })
        main {
            off.onPriceChange("90")
            off.save()
        }
        awaitTrue { mealCount() == 2 }
        val dropped = db.mealDao().observeAll().first().single { it.id != kept.id }
        assertNull(dropped.latitude)
        assertNull(dropped.placeId)
    }

    @Test
    fun aPlaceTakesTheLocationOnlyWhenItHasNoCoordinates() = runBlocking {
        val market = db.placeDao().insert(Place(name = "Market"))
        val cafe = db.placeDao().insert(Place(name = "Cafe", latitude = cafeSpot.latitude, longitude = cafeSpot.longitude))
        // Each has a meal already, as a saved place does, so neither is tidied away as empty.
        insertMeal(noon, "Earlier", placeId = market)
        insertMeal(noon, "Earlier", placeId = cafe)
        phone.fix.value = noodlesSpot

        for (placeId in listOf(market, cafe)) {
            val vm = newMealChecked()
            processing.finish(capture(vm), ok = true)
            main {
                vm.onPlaceSelected(placeId)
                vm.onPriceChange("100")
                vm.save()
            }
        }

        awaitTrue { mealCount() == 4 }
        assertEquals(noodlesSpot.latitude, db.placeDao().get(market)!!.latitude!!, 0.0)
        assertEquals(cafeSpot.latitude, db.placeDao().get(cafe)!!.latitude!!, 0.0)
    }

    @Test
    fun aNewPlaceIsCreatedAtTheSavedLocation() = runBlocking {
        phone.fix.value = noodlesSpot
        val vm = newMealChecked()
        processing.finish(capture(vm), ok = true)
        main {
            vm.onNewPlace("Night market")
            vm.onPriceChange("100")
            vm.save()
        }

        awaitTrue { mealCount() == 1 }
        assertEquals(noodlesSpot.latitude, db.placeDao().all().single().latitude!!, 0.0)
    }

    @Test
    fun withLocationRefusedTheRowNeverShowsAndAGalleryPhotoStillLocatesANewPlace() = runBlocking {
        phone.allowed = false
        processing.metadata = PhotoMetadata(takenAt = null, location = noodlesSpot)
        val vm = newMealChecked()
        main { vm.onPicked(Uri.parse("content://test/photo/5")) }
        processing.finish(main { vm.uiState!!.draft.photo!! }, ok = true)
        awaitTrue { main { vm.uiState!!.draft.locationSource } == LocationSource.Gallery }
        assertNull(main { vm.uiState!!.locationRow })

        main {
            vm.onNewPlace("Night market")
            vm.onPriceChange("100")
            vm.save()
        }

        awaitTrue { mealCount() == 1 }
        assertNull(db.mealDao().observeAll().first().single().latitude)
        assertEquals(noodlesSpot.latitude, db.placeDao().all().single().latitude!!, 0.0)
        assertEquals(0, phone.starts)
    }

    @Test
    fun locationIsAskedForOnceAndOnlyForANewMeal() = runBlocking {
        val mealId = insertMeal(noon, "Tea")
        phone.allowed = false

        assertEquals(listOf(Manifest.permission.CAMERA), savedMeal(mealId).permissionsToRequest(hasCamera = false))
        assertFalse(locationAsked.value)

        val first = newMeal().permissionsToRequest(hasCamera = true)
        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), first)
        // A request that never reached Android, as when the camera closed first, is asked again.
        assertFalse(locationAsked.value)
        val again = newMeal()
        assertEquals(first, again.permissionsToRequest(hasCamera = true))

        again.onPermissionsRequested(first)
        awaitTrue { locationAsked.value }
        assertEquals(emptyList<String>(), newMeal().permissionsToRequest(hasCamera = true))
    }

    @Test
    fun aSavedMealShowsItsLocationAndTurningItOffRemovesIt() = runBlocking {
        val mealId = db.mealDao().insert(
            Meal(eatenAt = noon, name = "Tea", price = 50, latitude = noodlesSpot.latitude, longitude = noodlesSpot.longitude),
        )
        val vm = savedMeal(mealId)
        awaitTrue { main { vm.uiState?.locationRow } != null }
        assertTrue(main { vm.uiState!!.locationRow!!.isOn })
        assertFalse(main { vm.uiState!!.showSave })

        main { vm.onSaveLocationChange(false) }
        assertTrue(main { vm.uiState!!.showSave })
        main { vm.save() }

        awaitTrue { db.mealDao().get(mealId)!!.latitude == null }
        assertNull(db.mealDao().get(mealId)!!.longitude)
    }

    @Test
    fun aSavedMealNeverGainsALocation() = runBlocking {
        val mealId = insertMeal(noon, "Tea")
        phone.fix.value = noodlesSpot
        val vm = savedMeal(mealId)
        awaitTrue { main { vm.uiState } != null }
        main { vm.onPermissionsChecked() }
        val name = capture(vm)
        processing.finish(name, ok = true)

        awaitTrue { !main { vm.uiState!!.photoProcessing } }
        assertNull(main { vm.uiState!!.locationRow })
        assertEquals(0, phone.starts)
    }

    private suspend fun insertMeal(eatenAt: LocalDateTime, name: String, placeId: Long? = null): Long =
        db.mealDao().insert(Meal(eatenAt = eatenAt, name = name, price = 100, placeId = placeId))

    private fun newMeal(): MealViewModel = viewModel(mealId = null)

    /** A new meal past the camera's permission check, as the screen runs it. */
    private suspend fun newMealChecked(): MealViewModel = newMeal().also { vm ->
        main { vm.onPermissionsChecked() }
    }

    private suspend fun skip(vm: MealViewModel, name: String = "Soup", price: String = "280") = main {
        vm.onSkipPhoto()
        vm.onNameChange(name)
        vm.onPriceChange(price)
    }

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
                    districtMap = { districts },
                    phoneLocation = phone,
                    locationAsked = locationAsked,
                    markLocationAsked = { locationAsked.value = true },
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

    /** The phone's location, set by the test. Allowed and precise unless a test says otherwise. */
    private class FakeLocation : PhoneLocation {
        override val fix = MutableStateFlow<LocationFix?>(null)
        var allowed = true
        var starts = 0

        override fun isAllowed() = allowed

        override fun start() {
            if (allowed) starts++
        }

        override fun stop() = Unit
    }

    private companion object {
        /** The bundled districts, read once for all the tests. */
        val districts: DistrictMap by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            DistrictMap.parse(context.assets.open("districts.json").use { it.readBytes().decodeToString() })
        }
    }
}
