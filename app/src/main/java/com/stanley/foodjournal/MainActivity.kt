package com.stanley.foodjournal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.stanley.foodjournal.ui.FoodJournalApp
import com.stanley.foodjournal.ui.theme.FoodJournalTheme

class MainActivity : ComponentActivity() {
    /** A meal reminder was tapped: open a new meal at the camera, once. */
    private var addMeal by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only a fresh launch; a recreated activity has already acted on its intent.
        addMeal = savedInstanceState == null && intent?.action == ACTION_ADD_MEAL
        setContent {
            FoodJournalTheme {
                FoodJournalApp(addMeal = addMeal, onAddMealHandled = { addMeal = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_ADD_MEAL) addMeal = true
    }

    override fun onResume() {
        super.onResume()
        // The notification permission may have changed in Android's settings meanwhile.
        (application as FoodJournalApplication).container.reminders.onPermissionChanged()
    }

    companion object {
        /** Opens a new meal, as a meal reminder's tap does. */
        const val ACTION_ADD_MEAL = "com.stanley.foodjournal.action.ADD_MEAL"
    }
}
