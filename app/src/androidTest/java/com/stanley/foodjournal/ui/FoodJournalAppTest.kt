package com.stanley.foodjournal.ui

import android.content.Context
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.FoodJournalApplication
import com.stanley.foodjournal.R
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.ui.theme.FoodJournalTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** The whole app, on its own database, so each test adds what it needs and removes it. */
@RunWith(AndroidJUnit4::class)
class FoodJournalAppTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val meals = (context as FoodJournalApplication).container.mealDao

    // Far ahead, so it is the newest week's first tile, whatever else the database holds.
    private val meal = Meal(eatenAt = LocalDateTime.of(2099, 6, 1, 12, 0), name = "Test meal", price = 100)
    private var mealId = 0L

    @Before
    fun addMeal() = runBlocking {
        mealId = meals.insert(meal)
    }

    @After
    fun removeMeal() = runBlocking {
        meals.delete(meal.copy(id = mealId))
    }

    @Test
    fun closingAMealWhileItStillOpensClosesIt() {
        compose.setContent { FoodJournalTheme { FoodJournalApp() } }
        compose.waitUntil(WAIT_MILLIS) {
            compose.onAllNodes(hasText(meal.name!!)).fetchSemanticsNodes().isNotEmpty()
        }

        // Stop the clock partway through the Meal screen's opening motion.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(meal.name!!).performClick()
        compose.mainClock.advanceTimeBy(MID_MOTION_MILLIS)
        // The meal loads on the database's threads, whatever the clock says.
        val back = hasContentDescription(context.getString(R.string.back))
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (compose.onAllNodes(back).fetchSemanticsNodes().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS)
            compose.mainClock.advanceTimeByFrame()
        }
        compose.onNode(back).performClick()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()

        compose.onNodeWithContentDescription(context.getString(R.string.meal_delete)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.add_meal)).assertExists()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 20L
        const val MID_MOTION_MILLIS = 48L
    }
}
