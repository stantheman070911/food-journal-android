package com.stanley.foodjournal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.stanley.foodjournal.logic.sameName
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MealDao {
    @Query("SELECT COUNT(*) FROM meal")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT * FROM meal WHERE id = :id")
    abstract suspend fun get(id: Long): Meal?

    /** Inserts [meal]. A nonzero id is kept, which is how an undone delete comes back. */
    @Insert
    abstract suspend fun insert(meal: Meal): Long

    @Update
    abstract suspend fun update(meal: Meal)

    @Delete
    abstract suspend fun delete(meal: Meal)

    /** Names of recent meals, most recent first, one per name ignoring case. */
    @Query(
        """
        SELECT name FROM meal
        WHERE name IS NOT NULL AND name != ''
        GROUP BY name COLLATE NOCASE
        ORDER BY MAX(eatenAt) DESC
        LIMIT :limit
        """,
    )
    abstract suspend fun recentNames(limit: Int): List<String>

    /** The photo file of every meal that has one. */
    @Query("SELECT photo FROM meal WHERE photo IS NOT NULL")
    abstract suspend fun photos(): List<String>

    @Query("SELECT * FROM place")
    protected abstract suspend fun places(): List<Place>

    @Insert
    protected abstract suspend fun insertPlace(place: Place): Long

    /**
     * Saves [meal] in one step. With [newPlace], the meal goes to the saved place of that name,
     * or to [newPlace] itself when no place has the name yet. Returns the meal's id.
     */
    @Transaction
    open suspend fun save(meal: Meal, newPlace: Place? = null): Long {
        val placeId = if (newPlace != null) {
            places().firstOrNull { sameName(it.name, newPlace.name) }?.id ?: insertPlace(newPlace)
        } else {
            meal.placeId
        }
        val saved = meal.copy(placeId = placeId)
        return if (saved.id == 0L) {
            insert(saved)
        } else {
            update(saved)
            saved.id
        }
    }
}
