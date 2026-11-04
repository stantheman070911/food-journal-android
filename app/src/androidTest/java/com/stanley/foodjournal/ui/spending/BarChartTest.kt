package com.stanley.foodjournal.ui.spending

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.Period
import com.stanley.foodjournal.logic.PeriodKind
import com.stanley.foodjournal.ui.theme.FoodJournalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BarChartTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = LocalDate.of(2026, 11, 2)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)
    private val opened = mutableListOf<Pair<LocalDate, LocalDate>>()

    private fun bar(day: LocalDate, amount: Int) = ChartBar(
        amount = amount,
        amountText = "$amount",
        axisLabel = null,
        description = "Nov ${day.dayOfMonth}, $amount",
        isFuture = false,
        firstDay = day,
        lastDay = day,
    )

    private fun show() {
        val figures = SpendingFigures(
            total = "500",
            change = null,
            dailyAverage = "166",
            perMeal = "250",
            bars = listOf(bar(monday, 320), bar(tuesday, 0), bar(wednesday, 180)),
            places = emptyList(),
            other = null,
            bands = emptyList(),
            priciest = emptyList(),
        )
        val page = SpendingPage(Period(PeriodKind.Week, monday), "Nov 2 – 8", figures)
        compose.setContent {
            FoodJournalTheme {
                SpendingScreen(
                    uiState = SpendingUiState(isLoading = false, kind = PeriodKind.Week, pages = listOf(page)),
                    onSelectKind = {},
                    onShowPeriod = {},
                    onOpenMeal = {},
                    onOpenDays = { from, to -> opened += from to to },
                )
            }
        }
    }

    private fun bar(description: String) = compose.onNodeWithContentDescription(description)

    private fun actionOf(description: String): String? =
        bar(description).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label

    private fun string(@StringRes id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun theFirstTapShowsTheAmountAndTheSecondOpensTheMeals() {
        show()

        bar("Nov 2, 320").performClick()
        bar("Nov 2, 320").assertIsSelected()
        assertEquals(emptyList<Pair<LocalDate, LocalDate>>(), opened)

        bar("Nov 2, 320").performClick()
        assertEquals(listOf(monday to monday), opened)
    }

    @Test
    fun tappingAnotherBarMovesTheAmountAndOpensNothing() {
        show()

        bar("Nov 2, 320").performClick()
        bar("Nov 4, 180").performClick()

        bar("Nov 4, 180").assertIsSelected()
        bar("Nov 2, 320").assertIsNotSelected()
        assertEquals(emptyList<Pair<LocalDate, LocalDate>>(), opened)
    }

    @Test
    fun aBarAtZeroShowsZeroAndNeverOpens() {
        show()

        bar("Nov 3, 0").performClick()
        bar("Nov 3, 0").performClick()

        bar("Nov 3, 0").assertIsSelected()
        assertEquals(emptyList<Pair<LocalDate, LocalDate>>(), opened)
        assertEquals(string(R.string.spending_show_amount), actionOf("Nov 3, 0"))
    }

    @Test
    fun theActionReadsShowAmountThenShowMeals() {
        show()

        assertEquals(string(R.string.spending_show_amount), actionOf("Nov 2, 320"))
        bar("Nov 2, 320").performClick()
        assertEquals(string(R.string.spending_show_meals), actionOf("Nov 2, 320"))
    }
}
