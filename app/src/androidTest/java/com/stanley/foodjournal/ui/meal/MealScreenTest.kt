package com.stanley.foodjournal.ui.meal

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.R
import com.stanley.foodjournal.ui.theme.FoodJournalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class MealScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 11, 4)
    private val dateLine = "Tue, Nov 3 · 18:03"

    private fun state(isNew: Boolean) = MealUiState(
        isNew = isNew,
        stage = MealStage.Form,
        draft = MealDraft(eatenAt = LocalDateTime.of(2026, 11, 3, 18, 3), name = "Soup", priceText = "280"),
        photoFile = null,
        photoProcessing = false,
        photoFailed = false,
        savePending = false,
        closing = false,
        nameSuggestions = emptyList(),
        placeName = null,
        showSave = isNew,
    )

    private fun show(state: MealUiState, actions: MealActions = MealActions()) {
        compose.setContent {
            FoodJournalTheme {
                MealScreen(uiState = state, actions = actions, today = today)
            }
        }
    }

    private fun string(@StringRes id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun aSavedMealHasItsDateAboveThePhotoAndNoTimeRow() {
        show(state(isNew = false))

        compose.onNodeWithText(dateLine).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.meal_time)).assertDoesNotExist()
    }

    @Test
    fun aNewMealKeepsItsTimeRow() {
        show(state(isNew = true))

        compose.onNodeWithText(string(R.string.meal_time)).assertIsDisplayed()
        compose.onNodeWithText(dateLine).assertIsDisplayed()
    }

    @Test
    fun theDateLineOpensTheDateDialogAndNamesItsAction() {
        show(state(isNew = false))

        val action = compose.onNodeWithText(dateLine)
            .fetchSemanticsNode(errorMessageOnFail = "no date line")
            .let { generateSequence(it) { node -> node.parent } }
            .firstNotNullOf { it.config.getOrNull(SemanticsActions.OnClick) }
        assertEquals(string(R.string.meal_change_time), action.label)

        compose.onNodeWithText(dateLine).performClick()

        compose.onNodeWithText(string(R.string.next)).assertIsDisplayed()
    }
}
