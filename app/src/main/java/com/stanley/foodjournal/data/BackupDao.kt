package com.stanley.foodjournal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Every meal and place, read together. */
data class Records(val meals: List<Meal>, val places: List<Place>)

/** What a backup reads from the database and an import writes to it. */
interface BackupRecords {
    suspend fun snapshot(): Records

    suspend fun replaceAll(places: List<Place>, meals: List<Meal>)

    suspend fun mealCount(): Int
}

@Dao
abstract class BackupDao : BackupRecords {
    @Query("SELECT * FROM meal ORDER BY eatenAt, id")
    protected abstract suspend fun meals(): List<Meal>

    @Query("SELECT * FROM place ORDER BY id")
    protected abstract suspend fun places(): List<Place>

    @Query("DELETE FROM meal")
    protected abstract suspend fun deleteMeals()

    @Query("DELETE FROM place")
    protected abstract suspend fun deletePlaces()

    @Insert
    protected abstract suspend fun insertPlaces(places: List<Place>)

    @Insert
    protected abstract suspend fun insertMeals(meals: List<Meal>)

    @Query("SELECT COUNT(*) FROM meal")
    abstract override suspend fun mealCount(): Int

    /** All meals and places as one consistent picture. */
    @Transaction
    override suspend fun snapshot(): Records = Records(meals(), places())

    /**
     * Replaces every meal and place with [places] and [meals] in one step, keeping their ids, so
     * each meal keeps its place. If anything fails, nothing changes.
     */
    @Transaction
    override suspend fun replaceAll(places: List<Place>, meals: List<Meal>) {
        deleteMeals()
        deletePlaces()
        insertPlaces(places)
        insertMeals(meals)
    }
}
