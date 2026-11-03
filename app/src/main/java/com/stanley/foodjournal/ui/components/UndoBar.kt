package com.stanley.foodjournal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stanley.foodjournal.R
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.edits.PendingEdit
import com.stanley.foodjournal.edits.UndoableEdits
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

class UndoBarViewModel(private val edits: UndoableEdits) : ViewModel() {
    val pending: StateFlow<PendingEdit?> = edits.pending

    fun undo() = edits.undo()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { UndoBarViewModel(appContainer.edits) }
        }
    }
}

/** The grey "Deleted · Undo" bar, shown for whichever edit can still be undone. */
@Composable
fun UndoBar(
    modifier: Modifier = Modifier,
    viewModel: UndoBarViewModel = viewModel(factory = UndoBarViewModel.Factory),
) {
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    UndoBar(pending = pending, onUndo = viewModel::undo, modifier = modifier)
}

@Composable
fun UndoBar(
    pending: PendingEdit?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the last message while the bar slides away.
    var lastShown by remember { mutableStateOf<PendingEdit?>(null) }
    LaunchedEffect(pending) { if (pending != null) lastShown = pending }
    val shown = pending ?: lastShown
    AnimatedVisibility(
        visible = pending != null,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
    ) {
        val message = when (shown) {
            is PendingEdit.PlacesMerged -> stringResource(R.string.undo_merged)
            else -> stringResource(R.string.undo_deleted)
        }
        Snackbar(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            action = {
                TextButton(onClick = onUndo) {
                    Text(stringResource(R.string.undo), color = MaterialTheme.colorScheme.inversePrimary)
                }
            },
        ) {
            Text(message)
        }
    }
}

@ThemePreviews
@Composable
private fun UndoBarPreview() {
    PreviewSurface {
        UndoBar(
            pending = PendingEdit.MealDeleted(Meal(eatenAt = LocalDateTime.of(2026, 11, 3, 12, 40), price = 280)),
            onUndo = {},
        )
    }
}
