package com.stanley.foodjournal.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stanley.foodjournal.backup.BackupFixture
import com.stanley.foodjournal.backup.BackupProblem
import com.stanley.foodjournal.backup.awaitTrue
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.Place
import com.stanley.foodjournal.settings.ThemeChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class SettingsViewModelTest {
    private val f = BackupFixture()
    // Another phone, which exported its backup on Oct 31.
    private val other = BackupFixture(now = LocalDateTime.of(2026, 10, 31, 22, 0))
    private val store = ViewModelStore()
    private val applied: MutableList<ThemeChoice> = Collections.synchronizedList(mutableListOf())
    private val today = LocalDate.of(2026, 11, 4)

    @After
    fun tearDown() {
        runBlocking { withContext(Dispatchers.Main) { store.clear() } }
        f.close()
        other.close()
    }

    @Test
    fun aChosenThemeIsStoredAndHandedToAndroid() = runBlocking {
        val vm = viewModel()
        assertEquals(ThemeChoice.System, vm.state { true }.theme)

        main { vm.onSelectTheme(ThemeChoice.Dark) }

        assertEquals(ThemeChoice.Dark, vm.state { it.theme == ThemeChoice.Dark }.theme)
        awaitTrue { applied == listOf(ThemeChoice.Dark) }
        assertEquals(ThemeChoice.Dark, f.settings.theme.first())
    }

    @Test
    fun theLastBackupShowsWithItsYear() = runBlocking {
        val vm = viewModel()
        assertEquals(null, vm.state { true }.lastBackup)

        f.settings.setLastBackup(LocalDate.of(2026, 10, 2))

        assertEquals("Oct 2, 2026", vm.state { it.lastBackup != null }.lastBackup)
    }

    @Test
    fun anImportAsksFirstThenReplacesTheMeals() = runBlocking {
        f.db.mealDao().insert(Meal(eatenAt = LocalDateTime.of(2026, 11, 1, 9, 0), name = "Toast", price = 60))
        val zip = backupOnAnotherPhone()
        val vm = viewModel()

        main { vm.onImportPicked(Uri.fromFile(zip)) }

        val asked = vm.state { it.dialog != null }.dialog
        assertEquals(SettingsDialog.ConfirmImport(mealCount = 2, lastMeal = "Oct 30", currentMeals = 1), asked)
        assertEquals(1, f.db.backupDao().mealCount())

        main { vm.onConfirmImport() }

        assertTrue(vm.state { it.restored }.restored)
        assertEquals(listOf("Soup", "Latte"), f.db.backupDao().snapshot().meals.map { it.name })
        assertEquals("Oct 31, 2026", vm.state { it.lastBackup == "Oct 31, 2026" }.lastBackup)
        main { vm.onRestoredHandled() }
        assertFalse(vm.state { true }.restored)
    }

    @Test
    fun cancellingAnImportDeletesWhatWasRead() = runBlocking {
        val zip = backupOnAnotherPhone()
        val vm = viewModel()
        main { vm.onImportPicked(Uri.fromFile(zip)) }
        vm.state { it.dialog is SettingsDialog.ConfirmImport }
        assertTrue(f.photos.importFolder.exists())

        main { vm.onDismissDialog() }

        assertEquals(null, vm.state { it.dialog == null }.dialog)
        awaitTrue { !f.photos.importFolder.exists() }
        assertEquals(0, f.db.backupDao().mealCount())
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() = runBlocking {
        val notZip = f.file("photo.jpg").apply { writeText("not a backup") }
        val vm = viewModel()

        main { vm.onImportPicked(Uri.fromFile(notZip)) }

        assertEquals(SettingsDialog.ImportRefused(BackupProblem.NotABackup), vm.state { it.dialog != null }.dialog)
        assertFalse(vm.state { true }.isReading)
    }

    /** The other phone's backup: two meals, the last on Oct 30. */
    private suspend fun backupOnAnotherPhone() = f.file("other.zip").also { zip ->
        val place = other.db.placeDao().insert(Place(name = "Kiosk"))
        other.db.mealDao().insert(Meal(eatenAt = LocalDateTime.of(2026, 10, 28, 12, 0), name = "Soup", price = 180, placeId = place))
        other.db.mealDao().insert(Meal(eatenAt = LocalDateTime.of(2026, 10, 30, 9, 0), photo = other.savePhoto("p.jpg"), name = "Latte", price = 140))
        other.backup.writeBackup(zip.outputStream())
    }

    private suspend fun viewModel(): SettingsViewModel = main {
        val factory = viewModelFactory {
            initializer {
                SettingsViewModel(f.settings, f.backup, applyTheme = { applied += it }, appScope = f.scope, version = "0.1", today = { today })
            }
        }
        ViewModelProvider.create(store, factory)["settings-${System.nanoTime()}", SettingsViewModel::class]
    }

    private suspend fun SettingsViewModel.state(until: (SettingsUiState) -> Boolean): SettingsUiState =
        withTimeout(5_000) { uiState.first(until) }

    private suspend fun <T> main(block: () -> T): T = withContext(Dispatchers.Main) { block() }
}
