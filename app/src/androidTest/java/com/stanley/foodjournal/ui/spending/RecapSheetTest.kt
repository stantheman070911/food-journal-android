package com.stanley.foodjournal.ui.spending

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.RecapFigures
import com.stanley.foodjournal.logic.RecapOptions
import com.stanley.foodjournal.ui.theme.FoodJournalTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecapSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val everyFigure = RecapFigures(
        title = "Nov 2 – 8",
        year = "2026",
        meals = "5 meals",
        places = "4 places",
        total = "435",
        comparison = "120 less than last week",
    )
    private val open = RecapSheetUiState(everyFigure, RecapOptions())

    @Test
    fun aShortWindowKeepsShareInViewAndTheRowsScroll() {
        show(open, height = PHONE_ON_ITS_SIDE)

        shareLabel().assertIsDisplayed()
        row(R.string.recap_change).performScrollTo().assertIsDisplayed()
        shareLabel().assertIsDisplayed()
    }

    @Test
    fun aTallWindowShowsEveryRow() {
        show(open)

        listOf(R.string.recap_meals, R.string.recap_places, R.string.recap_total, R.string.recap_change)
            .forEach { row(it).assertIsDisplayed() }
        shareLabel().assertIsDisplayed()
    }

    @Test
    fun aFigureWithNothingToSayHasNoSwitch() {
        show(RecapSheetUiState(everyFigure.copy(places = null, comparison = null), RecapOptions()))

        row(R.string.recap_meals).assertIsDisplayed()
        row(R.string.recap_total).assertIsDisplayed()
        row(R.string.recap_places).assertDoesNotExist()
        row(R.string.recap_change).assertDoesNotExist()
    }

    @Test
    fun aRowTogglesOnlyItsOwnFigure() {
        show(open)

        row(R.string.recap_total).assertIsOn().performClick().assertIsOff()
        row(R.string.recap_meals).assertIsOn()
        row(R.string.recap_places).assertIsOn()
        row(R.string.recap_change).assertIsOn()
    }

    @Test
    fun shareMakesTheImage() {
        var shares = 0
        show(open, onShare = { shares++ })

        compose.onNodeWithText(label(R.string.recap_share)).performClick()

        assertEquals(1, shares)
    }

    @Test
    fun whileMakingTheSwitchesAndShareWait() {
        var shares = 0
        show(open.copy(isMaking = true), onShare = { shares++ })

        compose.onNodeWithText(label(R.string.recap_making)).assertIsNotEnabled().performClick()
        row(R.string.recap_meals).assertIsNotEnabled().performClick().assertIsOn()

        assertEquals(0, shares)
    }

    @Test
    fun theCloseButtonSlidesTheSheetAwayFirst() {
        var dismissed = false
        showWholeSheet(open) { dismissed = true }
        val close = compose.onNodeWithContentDescription(label(R.string.close)).assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        close.performClick()
        compose.mainClock.advanceTimeByFrame()
        assertFalse(dismissed)

        compose.mainClock.autoAdvance = true
        compose.waitUntil(3_000) { dismissed }
    }

    /** So a recap being dropped can't reach Android's share sheet while this one slides away. */
    @Test
    fun whileMakingTheCloseButtonClosesAtOnce() {
        var dismissed = false
        showWholeSheet(open.copy(isMaking = true)) { dismissed = true }
        val close = compose.onNodeWithContentDescription(label(R.string.close)).assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        close.performClick()
        compose.mainClock.advanceTimeByFrame()

        assertTrue(dismissed)
    }

    /** The sheet's content in a window of [height], with its switches working. */
    private fun show(sheet: RecapSheetUiState, height: Dp = TALL_WINDOW, onShare: () -> Unit = {}) {
        compose.setContent {
            var options by remember { mutableStateOf(sheet.options) }
            FoodJournalTheme {
                Box(Modifier.height(height)) {
                    RecapSheetContent(
                        sheet = sheet.copy(options = options),
                        onChangeOptions = { change -> options = change(options) },
                        onShare = onShare,
                        onClose = {},
                    )
                }
            }
        }
    }

    private fun showWholeSheet(sheet: RecapSheetUiState, onDismiss: () -> Unit) {
        compose.setContent {
            FoodJournalTheme {
                RecapSheet(sheet = sheet, onChangeOptions = {}, onShare = {}, onDismiss = onDismiss)
            }
        }
    }

    private fun row(@StringRes title: Int) = compose.onNodeWithText(label(title))

    /** The label itself, not the button: a squeezed button still reports its label when merged. */
    private fun shareLabel() = compose.onNodeWithText(label(R.string.recap_share), useUnmergedTree = true)

    private fun label(@StringRes id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private companion object {
        /** A 411 dp phone on its side, less 40 dp for the system bars. */
        val PHONE_ON_ITS_SIDE = 371.dp
        val TALL_WINDOW = 800.dp
    }
}
