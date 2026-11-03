package com.stanley.foodjournal

import android.content.Context
import android.util.Log
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.photos.PhotoProcessor
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Holds the app's long-lived objects. Built once by [FoodJournalApplication]. */
class AppContainer(context: Context) {
    /**
     * Work that must outlive any one screen, such as a save or an undo bar's cleanup. A failure
     * in one piece of work is logged rather than crashing the app; the others carry on.
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Background work failed", error)
        },
    )

    private val database: FoodJournalDatabase by lazy { FoodJournalDatabase.create(context) }

    val mealDao: MealDao get() = database.mealDao()

    val placeDao: PlaceDao get() = database.placeDao()

    val photoStore = PhotoStore(context.filesDir)

    val photoProcessor = PhotoProcessor(photoStore, context.contentResolver)

    val edits: UndoableEdits by lazy { UndoableEdits(mealDao, placeDao, photoStore, appScope) }

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

private const val TAG = "FoodJournal"
