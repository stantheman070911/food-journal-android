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
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.logic.TimeBand
import com.stanley.foodjournal.logic.formatDay
import com.stanley.foodjournal.logic.usualTimes
import com.stanley.foodjournal.logic.formatFullDay
import com.stanley.foodjournal.settings.AppSettings
import com.stanley.foodjournal.settings.ThemeChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** A question or a refusal the Settings screen shows over itself. */
sealed interface SettingsDialog {
    /** "This backup has 412 meals, the last on Oct 30. It replaces the 99 meals on this phone." */
    data class ConfirmImport(val mealCount: Int, val lastMeal: String?, val currentMeals: Int) : SettingsDialog

    data class ImportRefused(val problem: BackupProblem) : SettingsDialog
}

/**
 * The Reminders section. [mealRemindersOn] is the stored switch; it shows on only while
 * [notificationsAllowed]. [usual] lists the blocks with a usual time, which reminders follow.
 */
data class RemindersUiState(
    val mealRemindersOn: Boolean = false,
    val notificationsAllowed: Boolean = true,
    /** Notifications were asked for and refused, so the row sends to Android's settings instead. */
    val notificationsRefused: Boolean = false,
    val usual: List<Pair<TimeBand, LocalTime>> = emptyList(),
    val lateNightNote: Boolean = true,
) {
    /** The switch is on and Android lets it work. */
    val mealRemindersShowOn: Boolean get() = mealRemindersOn && notificationsAllowed

    /** Notifications are off, so the row offers Android's settings. */
    val blocked: Boolean get() = notificationsRefused || (mealRemindersOn && !notificationsAllowed)
}

data class SettingsUiState(
    val theme: ThemeChoice = ThemeChoice.System,
    val reminders: RemindersUiState = RemindersUiState(),
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
    meals: Flow<List<Meal>> = flowOf(emptyList()),
    /** Whether Android lets the app show notifications. Read again on each [onNotificationsChecked]. */
    private val canNotify: () -> Boolean = { true },
    /** Tells the reminders the permission may have changed, so their alarm is set again. */
    private val onPermissionChanged: () -> Unit = {},
) : ViewModel() {
    private data class Local(
        val isReading: Boolean = false,
        val prepared: PreparedImport? = null,
        val dialog: SettingsDialog? = null,
        val restored: Boolean = false,
    )

    private val local = MutableStateFlow(Local())
    private var readJob: Job? = null
    private val allowed = MutableStateFlow(canNotify())
    private val refused = MutableStateFlow(false)

    private val reminders = combine(
        settings.remindersOn,
        settings.lateNightNote,
        meals.map { usualTimes(it, today()).toList().sortedBy { (band, _) -> band.ordinal } },
        allowed,
        refused,
    ) { on, lateNight, usual, allowed, refused ->
        RemindersUiState(on, allowed, refused && !allowed, usual, lateNight)
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(settings.theme, settings.lastBackup, backup.state, local, reminders) { theme, last, state, local, reminders ->
            SettingsUiState(
                theme = theme,
                reminders = reminders,
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

    /** Meal reminders on or off. On needs notifications, which the screen asks for first. */
    fun onMealRemindersChange(on: Boolean) {
        appScope.launch { settings.setRemindersOn(on) }
    }

    /** The answer to the notification request: on if allowed, off and sent to settings if refused. */
    fun onNotificationsAnswered(granted: Boolean) {
        refused.value = !granted
        onNotificationsChecked()
        onMealRemindersChange(granted)
    }

    /** Reads the permission again, as on returning from Android's settings. */
    fun onNotificationsChecked() {
        allowed.value = canNotify()
        onPermissionChanged()
    }

    fun onLateNightNoteChange(on: Boolean) {
        appScope.launch { settings.setLateNightNote(on) }
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
                    meals = container.mealDao.observeAll(),
                    canNotify = { container.reminders.canNotify },
                    onPermissionChanged = container.reminders::onPermissionChanged,
                )
            }
        }
    }
}
