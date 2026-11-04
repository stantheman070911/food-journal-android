package com.stanley.foodjournal.ui.meal

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.MealSteps
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

    @Test
    fun onlyASavedMealHasTheCameraIcon() {
        var replaced = 0
        show(state(isNew = false), MealActions(onReplacePhoto = { replaced++ }))

        compose.onNodeWithContentDescription(string(R.string.meal_add_photo)).performClick()
        assertEquals(1, replaced)
    }

    @Test
    fun aNewMealHasNoCameraIconAndItsPhotoOpensTheCamera() {
        var replaced = 0
        show(state(isNew = true), MealActions(onReplacePhoto = { replaced++ }))

        compose.onNodeWithContentDescription(string(R.string.meal_add_photo)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.meal_add_photo)).performClick()
        assertEquals(1, replaced)
    }

    @Test
    fun aSavedMealWithoutAPhotoShowsItsNameOnThePlainTile() {
        show(state(isNew = false))

        compose.onNodeWithText(string(R.string.meal_add_photo)).assertDoesNotExist()
        // Once on the tile, and once in the card's Name field.
        compose.onAllNodesWithText("Soup").assertCountEquals(2)
    }

    @Test
    fun aNewMealWithALocationHasTheSaveLocationSwitchAndItsDistrict() {
        val changes = mutableListOf<Boolean>()
        show(
            state(isNew = true).copy(locationRow = LocationRow(isOn = false, district = "臺北市 大安區")),
            MealActions(onSaveLocationChange = { changes += it }),
        )

        val row = compose.onNodeWithText(string(R.string.meal_save_location)).performScrollTo()
        row.assertIsOff()
        compose.onNodeWithText("臺北市 大安區").assertExists()
        row.performClick()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun aSavedMealsRowReadsLocationWithItsSwitchOn() {
        show(state(isNew = false).copy(locationRow = LocationRow(isOn = true, district = "臺北市 大安區")))

        compose.onNodeWithText(string(R.string.meal_location)).performScrollTo().assertIsOn()
        compose.onNodeWithText(string(R.string.meal_save_location)).assertDoesNotExist()
    }

    @Test
    fun withoutALocationThereIsNoRow() {
        show(state(isNew = true))

        compose.onNodeWithText(string(R.string.meal_save_location)).assertDoesNotExist()
        compose.onNodeWithText(string(R.string.meal_location)).assertDoesNotExist()
    }

    @Test
    fun theLeftThirdStepsBackAndTheRestStepsOn() {
        val steps = record()

        tile().performTouchInput { click(Offset(width * 0.3f, centerY)) }
        assertEquals(listOf("previous"), steps)
        steps.clear()

        tile().performTouchInput { click(Offset(width * 0.4f, centerY)) }
        tile().performTouchInput { click(Offset(width * 0.9f, centerY)) }
        assertEquals(listOf("next", "next"), steps)
    }

    @Test
    fun swipingLeftStepsOnAndSwipingRightStepsBack() {
        val steps = record()

        tile().performTouchInput { swipeLeft() }
        assertEquals(listOf("next"), steps)
        steps.clear()

        tile().performTouchInput { swipeRight() }
        assertEquals(listOf("previous"), steps)
    }

    @Test
    fun aShortDragStepsNowhere() {
        val steps = record()

        // Past the touch slop, so not a tap, but short of a swipe.
        tile().performTouchInput { swipeLeft(startX = centerX, endX = centerX - 80f) }

        assertEquals(emptyList<String>(), steps)
    }

    @Test
    fun draggingUpScrollsTheFormAndStepsNowhere() {
        val steps = record()

        // Slow and short, so no fling reaches the form's end: the stretch there never settles under
        // the test's clock.
        tile().performTouchInput {
            down(center)
            moveBy(Offset(0f, -60f), delayMillis = 300)
            advanceEventTime(300)
            up()
        }

        assertEquals(emptyList<String>(), steps)
    }

    @Test
    fun screenReadersGetTheStepsThereAre() {
        record()
        assertEquals(
            listOf(string(R.string.meal_previous), string(R.string.meal_next)),
            tile().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label },
        )
    }

    @Test
    fun atTheNewestMealScreenReadersGetOnlyPrevious() {
        show(state(isNew = false).copy(steps = MealSteps(previous = 1, next = null, weekCount = 2, weekIndex = 1)))

        assertEquals(
            listOf(string(R.string.meal_previous)),
            tile().fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label },
        )
    }

    /** Shows a saved meal with a step on either side, recording which are taken. */
    private fun record(): MutableList<String> {
        val taken = mutableListOf<String>()
        show(
            state(isNew = false).copy(steps = MealSteps(previous = 1, next = 3, weekCount = 3, weekIndex = 1)),
            MealActions(onPreviousMeal = { taken += "previous" }, onNextMeal = { taken += "next" }),
        )
        return taken
    }

    private fun tile() = compose.onNode(hasCustomActions)

    private val hasCustomActions = SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)
}
