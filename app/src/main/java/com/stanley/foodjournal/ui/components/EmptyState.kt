package com.stanley.foodjournal.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val ActionGap = 24.dp

/**
 * One short line, set as a quiet heading, and optionally one outlined pill below it.
 *
 * The line is always centered in the available space, with or without a button, so it sits at
 * the same height on every tab. Inside a scrolling container, where there is no height to center
 * in, the line and button simply stack from the top.
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
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                OutlinedPillButton(text = actionLabel, onClick = onAction)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val line = measurables[0].measure(loose)
        val action = measurables.getOrNull(1)?.measure(loose)
        val gap = ActionGap.roundToPx()

        val width = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            maxOf(line.width, action?.width ?: 0)
        }
        val height = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            line.height + (action?.let { gap + it.height } ?: 0)
        }
        val lineY = if (constraints.hasBoundedHeight) (height - line.height) / 2 else 0

        layout(width, height) {
            line.place((width - line.width) / 2, lineY)
            action?.place(x = (width - action.width) / 2, y = lineY + line.height + gap)
        }
    }
}
