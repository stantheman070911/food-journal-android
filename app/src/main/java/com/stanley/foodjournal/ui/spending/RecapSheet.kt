package com.stanley.foodjournal.ui.spending

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.Period
import com.stanley.foodjournal.logic.PeriodKind
import com.stanley.foodjournal.logic.RecapFigures
import com.stanley.foodjournal.logic.RecapOptions
import com.stanley.foodjournal.ui.components.CardDivider
import com.stanley.foodjournal.ui.components.GroupedCard
import com.stanley.foodjournal.ui.components.PillButton
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import java.time.LocalDate

/**
 * Asks which figures a recap shows before it is made: meals, places, the total, and the change
 * from the period before, each with its own switch and the value it would add. A figure with
 * nothing to say, such as places when no meal has one, has no switch. Share makes the image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapSheet(
    sheet: RecapSheet,
    onOptionsChange: (RecapOptions) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        RecapSheetContent(sheet, onOptionsChange, onShare, onClose = onDismiss)
    }
}

@Composable
private fun RecapSheetContent(
    sheet: RecapSheet,
    onOptionsChange: (RecapOptions) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val figures = sheet.figures
    val options = sheet.options
    Column(Modifier.navigationBarsPadding()) {
        SheetHeader(onClose = onClose)
        GroupedCard(Modifier.padding(horizontal = 16.dp)) {
            OptionRow(
                title = stringResource(R.string.recap_meals),
                value = figures.meals,
                checked = options.meals,
                enabled = !sheet.isMaking,
                onCheckedChange = { onOptionsChange(options.copy(meals = it)) },
            )
            figures.places?.let { places ->
                CardDivider()
                OptionRow(
                    title = stringResource(R.string.recap_places),
                    value = places,
                    checked = options.places,
                    enabled = !sheet.isMaking,
                    onCheckedChange = { onOptionsChange(options.copy(places = it)) },
                )
            }
            CardDivider()
            OptionRow(
                title = stringResource(R.string.recap_total),
                value = figures.total,
                checked = options.total,
                enabled = !sheet.isMaking,
                onCheckedChange = { onOptionsChange(options.copy(total = it)) },
            )
            figures.comparison?.let { comparison ->
                CardDivider()
                OptionRow(
                    title = stringResource(R.string.recap_change),
                    value = comparison,
                    checked = options.comparison,
                    enabled = !sheet.isMaking,
                    onCheckedChange = { onOptionsChange(options.copy(comparison = it)) },
                )
            }
        }
        PillButton(
            text = stringResource(if (sheet.isMaking) R.string.recap_making else R.string.recap_share),
            onClick = onShare,
            interactive = !sheet.isMaking,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        )
    }
}

/** A centered title with a close button, as on the place picker. */
@Composable
private fun SheetHeader(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Text(
            text = stringResource(R.string.recap_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
        ) {
            Icon(FoodJournalIcons.Close, contentDescription = stringResource(R.string.close))
        }
    }
}

/** A figure's switch: its name in semibold, the value it adds in grey, the whole row a toggle. */
@Composable
private fun OptionRow(
    title: String,
    value: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        // The row takes the tap, so the switch only shows the state.
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                checkedBorderColor = colors.primary,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = colors.surfaceContainerHighest,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}

@ThemePreviews
@Composable
private fun RecapSheetPreview() {
    PreviewSurface {
        RecapSheetContent(
            sheet = RecapSheet(
                period = Period(PeriodKind.Week, LocalDate.of(2026, 11, 2)),
                figures = RecapFigures(
                    title = "Nov 2 – 8",
                    year = "2026",
                    meals = "5 meals",
                    places = "4 places",
                    total = "435",
                    comparison = "120 less than last week",
                ),
                options = RecapOptions(places = false),
            ),
            onOptionsChange = {},
            onShare = {},
            onClose = {},
        )
    }
}
