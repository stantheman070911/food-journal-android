package com.stanley.foodjournal.ui.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The type roles together: Fraunces for big numbers and headings, the sans for the rest. */
@Composable
internal fun TypeSpecimen(modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    Column(modifier.padding(16.dp)) {
        Text("November", style = type.headlineSmall)
        Text("12,480", style = type.displayLarge)
        Text("−12% vs Oct", style = type.bodyMedium, color = colors.onSurfaceVariant)
        Text("Daily 4,160 · Per meal 312", style = type.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Oct 26 – Nov 1", style = type.headlineSmall)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "· 2,340",
                style = type.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Text("280", style = type.displayMedium)
        Text("Beef noodle soup", style = type.titleMedium)
        Text("Lin Dong Fang · 12:40", style = type.bodyMedium, color = colors.onSurfaceVariant)
    }
}

@ThemePreviews
@Composable
private fun TypePreview() {
    PreviewSurface {
        TypeSpecimen()
    }
}
