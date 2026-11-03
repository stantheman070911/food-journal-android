package com.stanley.foodjournal.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.logic.Week
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.logic.formatWeekRange
import com.stanley.foodjournal.logic.formatWeekday
import com.stanley.foodjournal.logic.groupIntoWeeks
import com.stanley.foodjournal.photos.PhotoStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate

/** One meal's tile in a week's strip. A meal without a photo shows its [name] instead. */
data class JournalTile(
    val mealId: Long,
    val thumb: File?,
    val name: String?,
    val weekday: String,
)

/** A week's row: its header, "Oct 26 – Nov 1 · 2,340", and its tiles, Monday to Sunday. */
data class JournalWeek(
    val start: LocalDate,
    val range: String,
    val total: String,
    val tiles: List<JournalTile>,
)

data class JournalUiState(
    val isLoading: Boolean = true,
    val weeks: List<JournalWeek> = emptyList(),
) {
    val isEmpty: Boolean get() = !isLoading && weeks.isEmpty()
}

class JournalViewModel(
    mealDao: MealDao,
    private val photos: PhotoStore,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    val uiState: StateFlow<JournalUiState> = mealDao.observeAll()
        .map { meals -> JournalUiState(isLoading = false, weeks = groupIntoWeeks(meals).map { it.toRow() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    private fun Week.toRow() = JournalWeek(
        start = start,
        range = formatWeekRange(start, today()),
        total = formatPrice(total),
        tiles = meals.map { it.toTile() },
    )

    private fun Meal.toTile() = JournalTile(
        mealId = id,
        thumb = photo?.let(photos::thumbFile),
        name = name,
        weekday = formatWeekday(eatenAt.toLocalDate()),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { JournalViewModel(appContainer.mealDao, appContainer.photoStore) }
        }
    }
}
