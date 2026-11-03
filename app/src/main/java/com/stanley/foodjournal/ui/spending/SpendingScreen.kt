package com.stanley.foodjournal.ui.spending

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
fun SpendingScreen(
    modifier: Modifier = Modifier,
    viewModel: SpendingViewModel = viewModel(factory = SpendingViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SpendingScreen(uiState = uiState, modifier = modifier)
}

@Composable
fun SpendingScreen(
    uiState: SpendingUiState,
    modifier: Modifier = Modifier,
) {
    // No meal has been logged yet.
    if (!uiState.isLoading && uiState.isEmpty) {
        EmptyState(message = stringResource(R.string.empty_spending), modifier = modifier)
    }
}

@ThemePreviews
@Composable
private fun SpendingEmptyPreview() {
    PreviewSurface {
        SpendingScreen(uiState = SpendingUiState(isLoading = false, isEmpty = true))
    }
}
