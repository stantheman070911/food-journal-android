package com.stanley.foodjournal.ui.spending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stanley.foodjournal.R
import com.stanley.foodjournal.logic.RecapFigures
import com.stanley.foodjournal.logic.RecapOptions
import com.stanley.foodjournal.ui.components.CardDivider
import com.stanley.foodjournal.ui.components.GroupedCard
import com.stanley.foodjournal.ui.components.PillButton
import com.stanley.foodjournal.ui.components.SheetHeader
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews
import kotlinx.coroutines.launch

/**
 * Asks which figures a recap shows before it is made: meals, places, the total, and the change
 * from the period before, each with its own switch and the value it would add. A figure with
 * nothing to say, such as places when no meal has one, has no switch. Share makes the image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapSheet(
    sheet: RecapSheetUiState,
    onChangeOptions: ((RecapOptions) -> RecapOptions) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        tonalElevation = 0.dp,
        dragHandle = null,
    ) {
        RecapSheetContent(
            sheet = sheet,
            onChangeOptions = onChangeOptions,
            onShare = onShare,
            onClose = {
                // A recap being drawn is dropped at once, so it can't reach Android's share sheet
                // while this one slides away.
                if (sheet.isMaking) {
                    onDismiss()
                } else {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }
            },
        )
    }
}

@Composable
private fun RecapSheetContent(
    sheet: RecapSheetUiState,
    onChangeOptions: ((RecapOptions) -> RecapOptions) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val figures = sheet.figures
    val options = sheet.options
    Column(Modifier.navigationBarsPadding()) {
        SheetHeader(stringResource(R.string.recap_title), onClose = onClose)
        // In a short window, such as a phone on its side, the rows scroll and Share stays in view.
        GroupedCard(
            Modifier
                .padding(horizontal = 16.dp)
                .weight(1f, fill = false),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OptionRow(
                    title = stringResource(R.string.recap_meals),
                    value = figures.meals,
                    checked = options.meals,
                    enabled = !sheet.isMaking,
                    onCheckedChange = { checked -> onChangeOptions { it.copy(meals = checked) } },
                )
                figures.places?.let { places ->
                    CardDivider()
                    OptionRow(
                        title = stringResource(R.string.recap_places),
                        value = places,
                        checked = options.places,
                        enabled = !sheet.isMaking,
                        onCheckedChange = { checked -> onChangeOptions { it.copy(places = checked) } },
                    )
                }
                CardDivider()
                OptionRow(
                    title = stringResource(R.string.recap_total),
                    value = figures.total,
                    checked = options.total,
                    enabled = !sheet.isMaking,
                    onCheckedChange = { checked -> onChangeOptions { it.copy(total = checked) } },
                )
                figures.comparison?.let { comparison ->
                    CardDivider()
                    OptionRow(
                        title = stringResource(R.string.recap_change),
                        value = comparison,
                        checked = options.comparison,
                        enabled = !sheet.isMaking,
                        onCheckedChange = { checked -> onChangeOptions { it.copy(comparison = checked) } },
                    )
                }
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
            sheet = RecapSheetUiState(
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
            onChangeOptions = {},
            onShare = {},
            onClose = {},
        )
    }
}
