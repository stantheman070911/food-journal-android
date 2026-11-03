package com.stanley.foodjournal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Meal::class, Place::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class FoodJournalDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao

    abstract fun placeDao(): PlaceDao

    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): FoodJournalDatabase =
            Room.databaseBuilder(context, FoodJournalDatabase::class.java, "food-journal.db").build()
    }
}
