package com.stanley.foodjournal.ui.search

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
fun SearchScreen(
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(uiState = uiState, onAddMeal = onAddMeal, modifier = modifier)
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onAddMeal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!uiState.isLoading && uiState.isEmpty) {
        EmptyState(
            message = stringResource(R.string.empty_search),
            actionLabel = stringResource(R.string.log_first_meal),
            onAction = onAddMeal,
            modifier = modifier,
        )
    }
}

@ThemePreviews
@Composable
private fun SearchEmptyPreview() {
    PreviewSurface {
        SearchScreen(uiState = SearchUiState(isLoading = false, isEmpty = true), onAddMeal = {})
    }
}
