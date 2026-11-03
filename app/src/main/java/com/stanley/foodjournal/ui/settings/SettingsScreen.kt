package com.stanley.foodjournal.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stanley.foodjournal.R
import com.stanley.foodjournal.backup.BackupProblem
import com.stanley.foodjournal.backup.BackupWork
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.settings.ThemeChoice
import com.stanley.foodjournal.ui.components.SegmentedPill
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import com.stanley.foodjournal.ui.preview.PreviewSurface
import com.stanley.foodjournal.ui.preview.ThemePreviews

/**
 * The theme, backup and restore, and About. [onRestored] follows a finished restore, so the
 * tabs can start afresh on the restored meals.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    onRestored: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val export = rememberExportLauncher(viewModel::onExport)
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onImportPicked(uri)
    }
    LaunchedEffect(uiState.restored) {
        if (uiState.restored) {
            viewModel.onRestoredHandled()
            onRestored()
        }
    }
    // A restore takes a moment; the screen stays until it is done.
    BackHandler(enabled = uiState.work == BackupWork.Restoring) {}
    SettingsScreen(
        uiState = uiState,
        actions = SettingsActions(
            onBack = { if (uiState.work != BackupWork.Restoring) onBack() },
            onSelectTheme = viewModel::onSelectTheme,
            onExport = export,
            onImport = { import.launch(IMPORT_MIME_TYPES) },
            onOpenLicenses = onOpenLicenses,
            onConfirmImport = viewModel::onConfirmImport,
            onDismissDialog = viewModel::onDismissDialog,
            onFailureShown = viewModel::onFailureShown,
        ),
        modifier = modifier,
    )
}

/** Everything the Settings screen can ask for. */
class SettingsActions(
    val onBack: () -> Unit = {},
    val onSelectTheme: (ThemeChoice) -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onOpenLicenses: () -> Unit = {},
    val onConfirmImport: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onFailureShown: () -> Unit = {},
)

/** Grey section labels over rows on rounded grey cards, after Retro's Settings. */
@Composable
fun SettingsScreen(uiState: SettingsUiState, actions: SettingsActions, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            PageTopBar(title = stringResource(R.string.settings), onBack = actions.onBack)
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                SectionLabel(stringResource(R.string.settings_appearance))
                SegmentedPill(
                    options = ThemeChoice.entries,
                    selected = uiState.theme,
                    label = { stringResource(it.label) },
                    onSelect = actions.onSelectTheme,
                )
                SectionLabel(stringResource(R.string.settings_backup))
                Card {
                    SettingsRow(
                        title = stringResource(
                            if (uiState.work == BackupWork.Exporting) R.string.backup_exporting else R.string.backup_export,
                        ),
                        detail = uiState.lastBackup?.let { stringResource(R.string.backup_last, it) }
                            ?: stringResource(R.string.backup_never),
                        enabled = !uiState.isBusy,
                        onClick = actions.onExport,
                    )
                    CardDivider()
                    SettingsRow(
                        title = stringResource(
                            when {
                                uiState.isReading -> R.string.backup_reading
                                uiState.work == BackupWork.Restoring -> R.string.backup_restoring
                                else -> R.string.backup_import
                            },
                        ),
                        detail = stringResource(R.string.backup_import_detail),
                        enabled = !uiState.isBusy,
                        onClick = actions.onImport,
                    )
                }
                SectionLabel(stringResource(R.string.settings_about))
                Card {
                    SettingsRow(title = stringResource(R.string.settings_version), value = uiState.version)
                    CardDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_licenses),
                        opensPage = true,
                        onClick = actions.onOpenLicenses,
                    )
                }
            }
        }
    }
    when (val dialog = uiState.dialog) {
        is SettingsDialog.ConfirmImport -> ConfirmImportDialog(dialog, actions.onConfirmImport, actions.onDismissDialog)
        is SettingsDialog.ImportRefused -> MessageDialog(message = refusal(dialog.problem), onDismiss = actions.onDismissDialog)
        null -> uiState.failure?.let { BackupFailureDialog(it, actions.onFailureShown) }
    }
}

/** "This backup has 412 meals, the last on Oct 30. It replaces the 99 meals on this phone." */
@Composable
private fun ConfirmImportDialog(dialog: SettingsDialog.ConfirmImport, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val has = if (dialog.mealCount == 0 || dialog.lastMeal == null) {
        stringResource(R.string.import_backup_empty)
    } else {
        pluralStringResource(R.plurals.import_backup_has, dialog.mealCount, formatPrice(dialog.mealCount), dialog.lastMeal)
    }
    val replaces = if (dialog.currentMeals == 0) {
        stringResource(R.string.import_replaces_nothing)
    } else {
        pluralStringResource(R.plurals.import_replaces, dialog.currentMeals, formatPrice(dialog.currentMeals))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_replace)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = { Text("$has $replaces", style = MaterialTheme.typography.bodyLarge) },
    )
}

@Composable
private fun refusal(problem: BackupProblem): String = stringResource(
    when (problem) {
        BackupProblem.NotABackup -> R.string.import_not_a_backup
        BackupProblem.NewerVersion -> R.string.import_newer
        is BackupProblem.Damaged -> R.string.import_damaged
        BackupProblem.Unreadable -> R.string.import_unreadable
    },
)

private val ThemeChoice.label: Int
    get() = when (this) {
        ThemeChoice.System -> R.string.theme_system
        ThemeChoice.Light -> R.string.theme_light
        ThemeChoice.Dark -> R.string.theme_dark
    }

/** A back arrow and the page's title, as "← Settings". */
@Composable
fun PageTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(FoodJournalIcons.Back, contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

/** Rows on a card are divided as the Meal form's are: a line of the background, inset. */
@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.background,
    )
}

/**
 * A row: a title in semibold, with a grey [detail] below it or a grey [value] at the end. One
 * that [opensPage] ends in a chevron. Not [enabled], its title turns grey and taps do nothing.
 */
@Composable
private fun SettingsRow(
    title: String,
    detail: String? = null,
    value: String? = null,
    opensPage: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) colors.onSurface else colors.outline,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
        if (opensPage) {
            Icon(FoodJournalIcons.Chevron, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** Some file apps label a ZIP as one of the other two. */
private val IMPORT_MIME_TYPES = arrayOf(BACKUP_MIME_TYPE, "application/x-zip-compressed", "application/octet-stream")

@ThemePreviews
@Composable
private fun SettingsPreview() {
    PreviewSurface {
        SettingsScreen(
            uiState = SettingsUiState(theme = ThemeChoice.System, lastBackup = "Oct 2, 2026", version = "0.1"),
            actions = SettingsActions(),
        )
    }
}

@ThemePreviews
@Composable
private fun SettingsExportingPreview() {
    PreviewSurface {
        SettingsScreen(
            uiState = SettingsUiState(theme = ThemeChoice.Dark, work = BackupWork.Exporting, version = "0.1"),
            actions = SettingsActions(),
        )
    }
}
