package com.stanley.foodjournal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.stanley.foodjournal.logic.mergedPlace
import com.stanley.foodjournal.logic.sameName
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlaceDao {
    @Query(
        """
        SELECT place.*, MAX(meal.eatenAt) AS lastVisit
        FROM place LEFT JOIN meal ON meal.placeId = place.id
        GROUP BY place.id
        """,
    )
    abstract fun observeSummaries(): Flow<List<PlaceSummary>>

    @Query("SELECT * FROM place")
    abstract suspend fun all(): List<Place>

    @Query("SELECT * FROM place WHERE id = :id")
    abstract suspend fun get(id: Long): Place?

    /** Inserts [place]. A nonzero id is kept, which is how an undone merge comes back. */
    @Insert
    abstract suspend fun insert(place: Place): Long

    @Update
    abstract suspend fun update(place: Place)

    @Delete
    protected abstract suspend fun delete(place: Place)

    /**
     * Removes every place with no meals, except those in [keep]. Uses NOT EXISTS because NOT IN
     * matches nothing as soon as any meal has no place.
     */
    @Query(
        """
        DELETE FROM place
        WHERE id NOT IN (:keep)
        AND NOT EXISTS (SELECT 1 FROM meal WHERE meal.placeId = place.id)
        """,
    )
    abstract suspend fun deleteEmpty(keep: List<Long> = emptyList()): Int

    @Query("SELECT id FROM meal WHERE placeId = :placeId")
    protected abstract suspend fun mealIdsAt(placeId: Long): List<Long>

    @Query("UPDATE meal SET placeId = :placeId WHERE id IN (:mealIds)")
    protected abstract suspend fun moveMeals(mealIds: List<Long>, placeId: Long)

    /**
     * Merges [mergedAway] into [survivor] in one step: its meals move over, it is removed, and
     * the survivor takes its coordinates if it has none. Returns the ids of the meals moved.
     */
    @Transaction
    open suspend fun merge(mergedAway: Place, survivor: Place): List<Long> {
        val moved = mealIdsAt(mergedAway.id)
        moveMeals(moved, survivor.id)
        delete(mergedAway)
        update(mergedPlace(survivor, mergedAway))
        return moved
    }

    /**
     * Reverses [merge]: [mergedAway] returns with its id, and [mealIds] move back to it. If a
     * place with its name was created meanwhile, its meals join that place instead, which takes
     * its coordinates if it has none.
     */
    @Transaction
    open suspend fun unmerge(mergedAway: Place, survivorBefore: Place, mealIds: List<Long>) {
        update(survivorBefore)
        val taken = all().firstOrNull { it.id != survivorBefore.id && sameName(it.name, mergedAway.name) }
        if (taken == null) {
            insert(mergedAway)
            moveMeals(mealIds, mergedAway.id)
        } else {
            moveMeals(mealIds, taken.id)
            update(mergedPlace(survivor = taken, mergedAway = mergedAway))
        }
    }
}
