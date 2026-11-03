package com.stanley.foodjournal.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stanley.foodjournal.R
import com.stanley.foodjournal.backup.BackupFailure
import com.stanley.foodjournal.backup.backupFileName
import java.time.LocalDate

/** Android's save dialog for a new backup, suggesting "food-journal-2026-11-04.zip". */
@Composable
fun rememberExportLauncher(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher: ActivityResultLauncher<String> =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
            if (uri != null) onPicked(uri)
        }
    return { launcher.launch(backupFileName(LocalDate.now())) }
}

/** Why an export or a restore didn't happen, with OK to close it. */
@Composable
fun BackupFailureDialog(failure: BackupFailure, onDismiss: () -> Unit) {
    val message = when (failure) {
        BackupFailure.MissingPhoto -> R.string.backup_failed_missing_photo
        BackupFailure.NotSaved -> R.string.backup_failed_not_saved
        BackupFailure.RestoreFailed -> R.string.backup_failed_restore
    }
    MessageDialog(message = stringResource(message), onDismiss = onDismiss)
}

/** One message and OK. */
@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
    )
}

const val BACKUP_MIME_TYPE = "application/zip"
