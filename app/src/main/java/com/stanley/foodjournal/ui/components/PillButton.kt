package com.stanley.foodjournal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews

/**
 * The main action on a screen: a solid pill, black (white in dark). Disabled, it turns a
 * plain mid grey, so it reads as "not yet" rather than as faded text.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.outline,
            disabledContentColor = colors.onPrimary,
        ),
        modifier = modifier.height(PillHeight),
    ) {
        Text(text, style = PillLabel)
    }
}

/** A secondary action: an outlined pill in the ink color. */
@Composable
fun OutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, colors.onSurface),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
        modifier = modifier.height(OutlinedPillHeight),
    ) {
        Text(text, style = PillLabel, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

private val PillHeight = 56.dp
private val OutlinedPillHeight = 48.dp

private val PillLabel
    @Composable get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

@ThemePreviews
@Composable
private fun PillButtonPreview() {
    PreviewSurface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(text = "Save", onClick = {}, modifier = Modifier.fillMaxWidth())
            PillButton(text = "Save", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            OutlinedPillButton(text = "Log your first meal", onClick = {})
        }
    }
}
