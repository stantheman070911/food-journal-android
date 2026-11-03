package com.stanley.foodjournal

import android.content.Context
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.MealDao

/** Holds the app's long-lived objects. Built once by [FoodJournalApplication]. */
class AppContainer(context: Context) {
    private val database: FoodJournalDatabase by lazy { FoodJournalDatabase.create(context) }

    val mealDao: MealDao get() = database.mealDao()
}
