package com.stanley.foodjournal.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.backup.BackupFailure
import com.stanley.foodjournal.backup.BackupManager
import com.stanley.foodjournal.backup.BackupProblem
import com.stanley.foodjournal.backup.BackupWork
import com.stanley.foodjournal.backup.ImportRead
import com.stanley.foodjournal.backup.PreparedImport
import com.stanley.foodjournal.logic.formatDay
import com.stanley.foodjournal.logic.formatFullDay
import com.stanley.foodjournal.settings.AppSettings
import com.stanley.foodjournal.settings.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A question or a refusal the Settings screen shows over itself. */
sealed interface SettingsDialog {
    /** "This backup has 412 meals, the last on Oct 30. It replaces the 99 meals on this phone." */
    data class ConfirmImport(val mealCount: Int, val lastMeal: String?, val currentMeals: Int) : SettingsDialog

    data class ImportRefused(val problem: BackupProblem) : SettingsDialog
}

data class SettingsUiState(
    val theme: ThemeChoice = ThemeChoice.System,
    /** "Oct 2, 2026", or null when there has been no backup. */
    val lastBackup: String? = null,
    val work: BackupWork? = null,
    /** A backup file is being unpacked and checked. */
    val isReading: Boolean = false,
    val failure: BackupFailure? = null,
    val dialog: SettingsDialog? = null,
    /** A restore just finished; the screen closes onto a fresh Journal. */
    val restored: Boolean = false,
    val version: String = "",
) {
    /** Export and Import wait while either is busy. */
    val isBusy: Boolean get() = work != null || isReading
}

/**
 * The theme, backup and restore, and About. An import is read and checked here, then waits for
 * the go-ahead; the restore itself runs in the app's scope, so it always finishes.
 */
class SettingsViewModel(
    private val settings: AppSettings,
    private val backup: BackupManager,
    private val applyTheme: (ThemeChoice) -> Unit,
    private val appScope: CoroutineScope,
    version: String,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private data class Local(
        val isReading: Boolean = false,
        val prepared: PreparedImport? = null,
        val dialog: SettingsDialog? = null,
        val restored: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private var readJob: Job? = null

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.theme, settings.lastBackup, backup.state, local) { theme, last, state, local ->
            SettingsUiState(
                theme = theme,
                lastBackup = last?.let(::formatFullDay),
                work = state.work,
                isReading = local.isReading,
                failure = state.failure,
                dialog = local.dialog,
                restored = local.restored,
                version = version,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(version = version))

    /** Stores the choice, then hands it to Android, which redraws the app in it. */
    fun onSelectTheme(choice: ThemeChoice) {
        appScope.launch {
            settings.setTheme(choice)
            applyTheme(choice)
        }
    }

    fun onExport(uri: Uri) = backup.export(uri)

    /** Reads and checks the picked file, then asks before replacing anything. */
    fun onImportPicked(uri: Uri) {
        if (uiState.value.isBusy || readJob?.isActive == true) return
        local.update { it.copy(isReading = true) }
        readJob = viewModelScope.launch {
            val read = backup.read(uri)
            local.update {
                when (read) {
                    is ImportRead.Ready -> it.copy(
                        isReading = false,
                        prepared = read.prepared,
                        dialog = SettingsDialog.ConfirmImport(
                            mealCount = read.prepared.mealCount,
                            lastMeal = read.prepared.lastMeal?.let { day -> formatDay(day, today()) },
                            currentMeals = read.prepared.currentMeals,
                        ),
                    )
                    is ImportRead.Refused -> it.copy(isReading = false, dialog = SettingsDialog.ImportRefused(read.problem))
                }
            }
        }
    }

    fun onConfirmImport() {
        val prepared = local.value.prepared ?: return
        local.update { it.copy(prepared = null, dialog = null) }
        viewModelScope.launch {
            if (backup.restore(prepared)) local.update { it.copy(restored = true) }
        }
    }

    /** Closes the dialog. A backup read but not restored is deleted. */
    fun onDismissDialog() {
        val prepared = local.value.prepared
        local.update { it.copy(prepared = null, dialog = null) }
        if (prepared != null) appScope.launch { backup.discard() }
    }

    fun onFailureShown() = backup.clearFailure()

    fun onRestoredHandled() {
        local.update { it.copy(restored = false) }
    }

    override fun onCleared() {
        if (local.value.prepared != null) appScope.launch { backup.discard() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                val app = checkNotNull(this[APPLICATION_KEY])
                SettingsViewModel(
                    settings = container.settings,
                    backup = container.backup,
                    applyTheme = container.applyTheme,
                    appScope = container.appScope,
                    version = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty(),
                )
            }
        }
    }
}
