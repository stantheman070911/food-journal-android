package com.stanley.foodjournal.ui.preview

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stanley.foodjournal.ui.theme.FoodJournalTheme

/** The test phone, a Pixel 8a: 1080 × 2400 px at 420 dpi. */
private const val PHONE = "spec:width=411dp,height=914dp,dpi=420"

/** Light and dark previews at phone size. */
@Preview(name = "Light", device = PHONE, showSystemUi = false)
@Preview(
    name = "Dark",
    device = PHONE,
    showSystemUi = false,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
)
annotation class ThemePreviews

/** Wraps preview content in the app theme on the theme's background. */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    FoodJournalTheme {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
