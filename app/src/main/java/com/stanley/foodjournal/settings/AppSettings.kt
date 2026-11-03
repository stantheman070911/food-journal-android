package com.stanley.foodjournal.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The app's few stored settings. Phase 6 adds the last backup date. */
class AppSettings(context: Context) {
    private val store = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("settings")
    }

    /** Whether location has ever been asked for. It is asked once only. */
    suspend fun locationAsked(): Boolean = store.data.map { it[LOCATION_ASKED] ?: false }.first()

    suspend fun markLocationAsked() {
        store.edit { it[LOCATION_ASKED] = true }
    }

    private companion object {
        val LOCATION_ASKED = booleanPreferencesKey("locationAsked")
    }
}
