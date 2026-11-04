package com.stanley.foodjournal.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// Version 2 adds where each meal was eaten. Its columns may be empty, so Room migrates on its own.
@Database(
    entities = [Meal::class, Place::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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
