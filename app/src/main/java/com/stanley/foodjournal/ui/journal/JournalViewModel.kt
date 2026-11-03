package com.stanley.foodjournal.ui.journal

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stanley.foodjournal.appContainer
import com.stanley.foodjournal.backup.BackupFailure
import com.stanley.foodjournal.backup.BackupManager
import com.stanley.foodjournal.backup.BackupWork
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.logic.Week
import com.stanley.foodjournal.logic.formatDay
import com.stanley.foodjournal.logic.formatPrice
import com.stanley.foodjournal.logic.formatWeekRange
import com.stanley.foodjournal.logic.formatWeekday
import com.stanley.foodjournal.logic.groupIntoWeeks
import com.stanley.foodjournal.logic.needsBackupReminder
import com.stanley.foodjournal.logic.weekStart
import com.stanley.foodjournal.photos.PhotoStore
import com.stanley.foodjournal.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import java.time.LocalDate

/**
 * One meal's tile in a week's strip. A meal without a photo shows its [name] instead. [photo] is
 * the full photo, used when the thumbnail can't be read.
 */
data class JournalTile(
    val mealId: Long,
    val thumb: File?,
    val name: String?,
    val weekday: String,
    val photo: File? = null,
)

/**
 * A week's row: its header, "Oct 26 – Nov 1 · 2,340", and its tiles, Monday to Sunday.
 * [isCurrent] marks the week that holds today, whose strip opens at its newest meal.
 */
data class JournalWeek(
    val start: LocalDate,
    val range: String,
    val total: String,
    val tiles: List<JournalTile>,
    val isCurrent: Boolean = false,
)

/**
 * The quiet line at the top of the Journal once a backup is due: the last one's day, "Oct 2", or
 * null if there has been none, and whether an export is running now.
 */
data class BackupReminder(val lastBackup: String?, val isExporting: Boolean = false)

data class JournalUiState(
    val isLoading: Boolean = true,
    val weeks: List<JournalWeek> = emptyList(),
    val reminder: BackupReminder? = null,
    /** Why an export started here didn't happen, until it has been shown. */
    val exportFailure: BackupFailure? = null,
) {
    val isEmpty: Boolean get() = !isLoading && weeks.isEmpty()
}

class JournalViewModel(
    mealDao: MealDao,
    private val photos: PhotoStore,
    settings: AppSettings,
    private val backup: BackupManager,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private class Meals(val firstDay: LocalDate?, val weeks: List<JournalWeek>)

    private val meals = mealDao.observeAll().map { meals ->
        Meals(firstDay = meals.firstOrNull()?.eatenAt?.toLocalDate(), weeks = groupIntoWeeks(meals).map { it.toRow() })
    }

    val uiState: StateFlow<JournalUiState> = combine(meals, settings.lastBackup, backup.state) { meals, last, state ->
        val today = today()
        JournalUiState(
            isLoading = false,
            weeks = meals.weeks,
            reminder = if (needsBackupReminder(last, meals.firstDay, today)) {
                BackupReminder(lastBackup = last?.let { formatDay(it, today) }, isExporting = state.work == BackupWork.Exporting)
            } else {
                null
            },
            exportFailure = state.failure?.takeIf { it != BackupFailure.RestoreFailed },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    /** Starts the export the reminder offers, to [uri] from Android's save dialog. */
    fun export(uri: Uri) = backup.export(uri)

    fun onFailureShown() = backup.clearFailure()

    private fun Week.toRow() = JournalWeek(
        start = start,
        range = formatWeekRange(start, today()),
        total = formatPrice(total),
        tiles = meals.map { it.toTile() },
        isCurrent = start == weekStart(today()),
    )

    private fun Meal.toTile() = JournalTile(
        mealId = id,
        thumb = photo?.let(photos::thumbFile),
        name = name,
        weekday = formatWeekday(eatenAt.toLocalDate()),
        photo = photo?.let(photos::photoFile),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer
                JournalViewModel(container.mealDao, container.photoStore, container.settings, container.backup)
            }
        }
    }
}
