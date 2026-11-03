package com.stanley.foodjournal.ui.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanley.foodjournal.R
import com.stanley.foodjournal.ui.components.EmptyState
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews

@Composable
fun JournalScreen(
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JournalScreen(uiState = uiState, onAddMeal = onAddMeal, modifier = modifier)
}

@Composable
fun JournalScreen(
    uiState: JournalUiState,
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoading && uiState.isEmpty) {
        EmptyState(
            message = stringResource(R.string.empty_journal),
            actionLabel = stringResource(R.string.log_first_meal),
            onAction = onAddMeal,
            modifier = modifier,
        )
    }
}

@ThemePreviews
@Composable
private fun JournalEmptyPreview() {
    PreviewSurface {
        JournalScreen(uiState = JournalUiState(isLoading = false, isEmpty = true), onAddMeal = {})
    }
}
