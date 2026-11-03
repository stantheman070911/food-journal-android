package com.stanley.foodjournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stanley.foodjournal.ui.FoodJournalApp
import com.stanley.foodjournal.ui.theme.FoodJournalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FoodJournalTheme {
                FoodJournalApp()
            }
        }
    }
}
