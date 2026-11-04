package com.stanley.foodjournal.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.logic.RecapOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AppSettingsTest {
    private val folder = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "settings-test-${System.nanoTime()}")
    private val file = File(folder, "settings.preferences_pb")

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    @Test
    fun theThemeStartsAtSystemAndThereIsNoBackupYet() = runBlocking {
        withSettings { settings ->
            assertEquals(ThemeChoice.System, settings.theme.first())
            assertNull(settings.lastBackup.first())
        }
    }

    @Test
    fun choicesAreKeptForTheNextLaunch() = runBlocking {
        withSettings { settings ->
            settings.setTheme(ThemeChoice.Dark)
            settings.setLastBackup(LocalDate.of(2026, 10, 2))
        }

        withSettings { settings ->
            assertEquals(ThemeChoice.Dark, settings.theme.first())
            assertEquals(LocalDate.of(2026, 10, 2), settings.lastBackup.first())
        }
    }

    @Test
    fun aRecapShowsEveryFigureUntilAChoiceIsMadeAndThenKeepsIt() = runBlocking {
        val chosen = RecapOptions(meals = false, comparison = false)
        withSettings { settings ->
            assertEquals(RecapOptions(), settings.recapOptions.first())
            settings.setRecapOptions(chosen)
        }

        withSettings { settings -> assertEquals(chosen, settings.recapOptions.first()) }
    }

    /** One DataStore at a time per file, as in the app: each launch opens it afresh. */
    private suspend fun withSettings(block: suspend (AppSettings) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(AppSettings(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }
}
