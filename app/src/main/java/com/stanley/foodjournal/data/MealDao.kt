package com.stanley.foodjournal.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Query("SELECT COUNT(*) FROM meal")
    fun observeCount(): Flow<Int>
}
