package com.stanley.foodjournal

import android.content.Context
import android.util.Log
import com.stanley.foodjournal.backup.BackupManager
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.photos.PhotoProcessor
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.settings.AppSettings
import com.stanley.foodjournal.settings.ThemeChoice
import com.stanley.foodjournal.settings.applyNightMode
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

    val settings = AppSettings.create(context, appScope)

    /** Applies a theme choice for the whole app, as Settings makes it. */
    val applyTheme: (ThemeChoice) -> Unit = { applyNightMode(context, it) }

    val backup: BackupManager by lazy {
        BackupManager(database.backupDao(), photoStore, photoProcessor, edits, settings, context.contentResolver, appScope)
    }

    /**
     * Sets the stored theme again, in case Android's copy of it and the app's ever drift apart,
     * as after the app's data is cleared.
     */
    fun applyStoredTheme() {
        appScope.launch { applyTheme(settings.theme.first()) }
    }

    /**
     * Tidies up after an earlier run that ended mid-edit or mid-import: photos no meal uses,
     * stale pending photos, and places with no meals. Nothing is pending at launch, so nothing is
     * kept back.
     */
    fun sweepOnLaunch() {
        appScope.launch {
            photoStore.ensureFolders()
            photoStore.recoverInterruptedImport()
            photoStore.sweep(inUse = mealDao.photos().toSet())
            placeDao.deleteEmpty()
        }
    }
}

private const val TAG = "FoodJournal"
