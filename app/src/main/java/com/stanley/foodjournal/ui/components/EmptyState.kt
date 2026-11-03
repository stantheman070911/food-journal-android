package com.stanley.foodjournal.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val ActionGap = 20.dp

/**
 * One short line, and optionally one button below it.
 *
 * The line is always centered in the available space, with or without a button, so it sits at
 * the same height on every tab.
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Layout(
        content = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val line = measurables[0].measure(loose)
        val action = measurables.getOrNull(1)?.measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val lineY = (constraints.maxHeight - line.height) / 2
            line.place((constraints.maxWidth - line.width) / 2, lineY)
            action?.place(
                x = (constraints.maxWidth - action.width) / 2,
                y = lineY + line.height + ActionGap.roundToPx(),
            )
        }
    }
}
