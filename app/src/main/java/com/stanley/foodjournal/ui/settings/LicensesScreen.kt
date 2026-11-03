package com.stanley.foodjournal.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanley.foodjournal.R

/**
 * The licenses of the font and libraries inside the app, as plain text. A paragraph starting
 * "## " is a heading.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val resources = LocalResources.current
    val paragraphs = remember(resources) {
        resources.openRawResource(R.raw.open_source_licenses).bufferedReader().use { it.readText() }
            .split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            PageTopBar(title = stringResource(R.string.settings_licenses), onBack = onBack)
            LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                items(paragraphs) { paragraph ->
                    if (paragraph.startsWith(HEADING)) {
                        Text(
                            text = paragraph.removePrefix(HEADING),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
                        )
                    } else {
                        Text(
                            text = paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

private const val HEADING = "## "
