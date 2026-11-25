package com.stanley.foodjournal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LateNightBarViewModel(private val note: MutableStateFlow<Boolean>) : ViewModel() {
    val shown: StateFlow<Boolean> = note

    fun dismiss() {
        note.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LateNightBarViewModel(appContainer.lateNightNote) }
        }
    }
}

/** "Let's not eat so late, shall we?", after a meal logged past midnight, for 4 seconds. */
@Composable
fun LateNightBar(
    modifier: Modifier = Modifier,
    viewModel: LateNightBarViewModel = viewModel(factory = LateNightBarViewModel.Factory),
) {
    val shown by viewModel.shown.collectAsStateWithLifecycle()
    LateNightBar(shown = shown, onDismiss = viewModel::dismiss, modifier = modifier)
}

/** Grey, as the undo bar is, with no action. Swiping it sideways closes it early. */
@Composable
fun LateNightBar(shown: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(shown) {
        if (shown) {
            delay(NOTE_MILLIS)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
    ) {
        key(shown) {
            val swipe = rememberSwipeToDismissBoxState()
            LaunchedEffect(swipe.currentValue) {
                if (swipe.currentValue != SwipeToDismissBoxValue.Settled) onDismiss()
            }
            SwipeToDismissBox(state = swipe, backgroundContent = {}) {
                Snackbar(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Text(stringResource(R.string.late_night_note))
                }
            }
        }
    }
}

private const val NOTE_MILLIS = 4_000L

@ThemePreviews
@Composable
private fun LateNightBarPreview() {
    PreviewSurface {
        LateNightBar(shown = true, onDismiss = {})
    }
}
