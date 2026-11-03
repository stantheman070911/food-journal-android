package com.stanley.foodjournal.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.data.MealDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class JournalUiState(
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
)

class JournalViewModel(mealDao: MealDao) : ViewModel() {
    val uiState: StateFlow<JournalUiState> = mealDao.observeCount()
        .map { count -> JournalUiState(isLoading = false, isEmpty = count == 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { JournalViewModel(appContainer.mealDao) }
        }
    }
}
