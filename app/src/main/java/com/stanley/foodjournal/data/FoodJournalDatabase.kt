package com.stanley.foodjournal.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Version 2 adds where each meal was eaten, and version 3 whether it was a snack. Both new columns
// may be empty or default to a meal, so Room migrates on its own.
@Database(
    entities = [Meal::class, Place::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
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
