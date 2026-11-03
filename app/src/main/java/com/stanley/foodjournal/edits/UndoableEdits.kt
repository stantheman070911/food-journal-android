package com.stanley.foodjournal.edits

import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.data.PlaceDao
import com.stanley.foodjournal.logic.RenamePlan
import com.stanley.foodjournal.logic.planRename
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** An edit that can still be undone while its grey bar shows. */
sealed interface PendingEdit {
    data class MealDeleted(val meal: Meal) : PendingEdit

    /** A place deleted from the picker; [mealIds] were at it and are now without a place. */
    data class PlaceDeleted(val place: Place, val mealIds: List<Long>) : PendingEdit

    data class PlacesMerged(
        val mergedAway: Place,
        val survivorBefore: Place,
        val movedMealIds: List<Long>,
    ) : PendingEdit
}

/** What renaming a place did. */
sealed interface RenameResult {
    data object Unchanged : RenameResult

    data object Renamed : RenameResult

    /** The place merged into [survivor], which its meals now belong to. */
    data class Merged(val survivor: Place) : RenameResult
}

/**
 * Deletes and merges, with undo. Lives for the whole app, so an undo bar keeps working after
 * the screen that started it has closed.
 *
 * A deleted meal's row goes at once, but its photo, and a place it leaves empty, stay until the
 * bar closes. Undo then only has to put the row back. One edit is pending at a time: a new one
 * closes the previous bar first.
 */
class UndoableEdits(
    private val mealDao: MealDao,
    private val placeDao: PlaceDao,
    private val photos: PhotoStore,
    private val scope: CoroutineScope,
    private val window: Duration = 4.seconds,
) {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow<PendingEdit?>(null)
    private var closeJob: Job? = null

    /** The edit whose undo bar is showing, if any. */
    val pending: StateFlow<PendingEdit?> = _pending.asStateFlow()

    fun deleteMeal(meal: Meal) {
        scope.launch {
            mutex.withLock {
                closeLocked()
                mealDao.delete(meal)
                openLocked(PendingEdit.MealDeleted(meal))
            }
        }
    }

    /** Deletes [place], with undo. Its meals stay, without a place, unless the deletion is undone. */
    fun deletePlace(place: Place) {
        scope.launch {
            mutex.withLock {
                closeLocked()
                val current = placeDao.get(place.id) ?: return@withLock
                val mealIds = placeDao.remove(current)
                openLocked(PendingEdit.PlaceDeleted(current, mealIds))
            }
        }
    }

    /**
     * Renames [place] to [typed], merging it into another place that already has the name.
     * Any pending edit is closed first, so the rename works on settled data.
     */
    suspend fun renamePlace(place: Place, typed: String): RenameResult = scope.async {
        mutex.withLock {
            closeLocked()
            val current = placeDao.get(place.id) ?: return@withLock RenameResult.Unchanged
            when (val plan = planRename(placeDao.all(), current, typed)) {
                RenamePlan.Unchanged -> RenameResult.Unchanged
                is RenamePlan.Rename -> {
                    placeDao.update(current.copy(name = plan.name))
                    RenameResult.Renamed
                }
                is RenamePlan.Merge -> {
                    val moved = placeDao.merge(mergedAway = current, survivor = plan.into)
                    openLocked(PendingEdit.PlacesMerged(current, plan.into, moved))
                    RenameResult.Merged(survivor = placeDao.get(plan.into.id) ?: plan.into)
                }
            }
        }
    }.await()

    fun undo() {
        scope.launch {
            mutex.withLock {
                val edit = _pending.value ?: return@withLock
                closeJob?.cancel()
                closeJob = null
                _pending.value = null
                when (edit) {
                    is PendingEdit.MealDeleted -> mealDao.insert(edit.meal)
                    is PendingEdit.PlaceDeleted -> placeDao.restore(edit.place, edit.mealIds)
                    is PendingEdit.PlacesMerged ->
                        placeDao.unmerge(edit.mergedAway, edit.survivorBefore, edit.movedMealIds)
                }
            }
        }
    }

    /** Closes the bar now, as when it is swiped away. */
    fun dismiss() {
        scope.launch { mutex.withLock { closeLocked() } }
    }

    /** Removes places left with no meals, keeping any that a pending undo may still need. */
    suspend fun removeEmptyPlaces() {
        mutex.withLock {
            val keep = (_pending.value as? PendingEdit.MealDeleted)?.meal?.placeId
            placeDao.deleteEmpty(listOfNotNull(keep))
        }
    }

    private fun openLocked(edit: PendingEdit) {
        _pending.value = edit
        closeJob = scope.launch {
            delay(window)
            mutex.withLock {
                if (_pending.value === edit) {
                    // This job is the one finishing; it must not cancel itself in closeLocked.
                    closeJob = null
                    closeLocked()
                }
            }
        }
    }

    private suspend fun closeLocked() {
        val edit = _pending.value ?: return
        closeJob?.cancel()
        closeJob = null
        _pending.value = null
        if (edit is PendingEdit.MealDeleted) {
            edit.meal.photo?.let { photos.delete(it) }
            placeDao.deleteEmpty()
        }
    }
}
