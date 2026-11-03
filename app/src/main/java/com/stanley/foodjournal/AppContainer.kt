package com.stanley.foodjournal

import android.content.Context
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.location.LocationTracker
import com.stanley.foodjournal.photos.PhotoProcessor
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the app's long-lived objects. Built once by [FoodJournalApplication]. */
class AppContainer(context: Context) {
    /** Work that must outlive any one screen, such as an undo bar's cleanup. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: FoodJournalDatabase by lazy { FoodJournalDatabase.create(context) }

    val mealDao: MealDao get() = database.mealDao()

    val placeDao: PlaceDao get() = database.placeDao()

    val photoStore = PhotoStore(context.filesDir)

    val photoProcessor = PhotoProcessor(photoStore, context.contentResolver)

    val edits: UndoableEdits by lazy { UndoableEdits(mealDao, placeDao, photoStore, appScope) }

    val settings: AppSettings by lazy { AppSettings(context) }

    val locationTracker: LocationTracker by lazy { LocationTracker(context) }

    /**
     * Tidies up after an earlier run that ended mid-edit: photos no meal uses, stale pending
     * photos, and places with no meals. Nothing is pending at launch, so nothing is kept back.
     */
    fun sweepOnLaunch() {
        appScope.launch {
            photoStore.ensureFolders()
            photoStore.sweep(inUse = mealDao.photos().toSet())
            placeDao.deleteEmpty()
        }
    }
}
