package com.stanley.foodjournal.settings

import android.app.UiModeManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.stanley.foodjournal.logic.RecapOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Light or dark: the system's choice, or always one of them. */
enum class ThemeChoice { System, Light, Dark }

/**
 * The few things the app remembers outside the database: the theme chosen in Settings, the day
 * of the last backup, and the figures last chosen for a recap. Kept in DataStore, in the file
 * "settings".
 */
class AppSettings(private val store: DataStore<Preferences>) {
    val theme: Flow<ThemeChoice> = store.data
        .map { prefs -> ThemeChoice.entries.firstOrNull { it.name == prefs[THEME] } ?: ThemeChoice.System }
        .distinctUntilChanged()

    /** The day of the last export, or of the backup last imported; null if there has been none. */
    val lastBackup: Flow<LocalDate?> = store.data
        .map { prefs -> prefs[LAST_BACKUP]?.let(LocalDate::ofEpochDay) }
        .distinctUntilChanged()

    /** The figures last chosen for a recap, shared or not; all of them until the first choice. */
    val recapOptions: Flow<RecapOptions> = store.data
        .map { prefs ->
            RecapOptions(
                meals = prefs[RECAP_MEALS] ?: true,
                places = prefs[RECAP_PLACES] ?: true,
                total = prefs[RECAP_TOTAL] ?: true,
                comparison = prefs[RECAP_COMPARISON] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setRecapOptions(options: RecapOptions) {
        store.edit {
            it[RECAP_MEALS] = options.meals
            it[RECAP_PLACES] = options.places
            it[RECAP_TOTAL] = options.total
            it[RECAP_COMPARISON] = options.comparison
        }
    }

    suspend fun setTheme(choice: ThemeChoice) {
        store.edit { it[THEME] = choice.name }
    }

    suspend fun setLastBackup(day: LocalDate) {
        store.edit { it[LAST_BACKUP] = day.toEpochDay() }
    }

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val LAST_BACKUP = longPreferencesKey("lastBackupDay")
        private val RECAP_MEALS = booleanPreferencesKey("recapMeals")
        private val RECAP_PLACES = booleanPreferencesKey("recapPlaces")
        private val RECAP_TOTAL = booleanPreferencesKey("recapTotal")
        private val RECAP_COMPARISON = booleanPreferencesKey("recapComparison")

        fun create(context: Context, scope: CoroutineScope) = AppSettings(
            PreferenceDataStoreFactory.create(scope = scope) { context.preferencesDataStoreFile("settings") },
        )
    }
}

/**
 * Hands [choice] to Android's per-app night mode, which Android keeps and applies before the app
 * draws, so a launch never flashes the other theme. Android has no way to read it back.
 */
fun applyNightMode(context: Context, choice: ThemeChoice) {
    val mode = when (choice) {
        ThemeChoice.System -> UiModeManager.MODE_NIGHT_AUTO
        ThemeChoice.Light -> UiModeManager.MODE_NIGHT_NO
        ThemeChoice.Dark -> UiModeManager.MODE_NIGHT_YES
    }
    context.getSystemService(UiModeManager::class.java).setApplicationNightMode(mode)
}
