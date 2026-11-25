package com.stanley.foodjournal.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stanley.foodjournal.data.FoodJournalDatabase
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.logic.BlockDay
import com.stanley.foodjournal.logic.Reminder
import com.stanley.foodjournal.logic.TimeBand
import com.stanley.foodjournal.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** The alarm's work: a reminder shows only while its block is empty, and goes once it is logged. */
@RunWith(AndroidJUnit4::class)
class MealRemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val folder = File(context.cacheDir, "reminders-test-${System.nanoTime()}")
    private lateinit var db: FoodJournalDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var settings: AppSettings

    private val today = LocalDate.now()
    private val lunch = Reminder(BlockDay(TimeBand.Lunch, today), today.atTime(13, 30), LocalTime.of(12, 30))

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, FoodJournalDatabase::class.java).build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        settings = AppSettings(PreferenceDataStoreFactory.create(scope = scope) { File(folder, "settings.preferences_pb") })
        notifications.cancelAll()
    }

    @After
    fun tearDown() {
        notifications.cancelAll()
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
        folder.deleteRecursively()
    }

    @Test
    fun aDueReminderShowsAndGoesOnceItsBlockIsLogged() = runBlocking {
        settings.setRemindersOn(true)
        val reminders = reminders()

        reminders.onAlarm(reminders.intentFor(lunch))
        awaitTrue { shownTags() == listOf("Lunch:$today") }

        db.mealDao().insert(Meal(eatenAt = today.atTime(13, 40), name = "Soup", price = 120))
        reminders.reschedule()
        awaitTrue { shownTags().isEmpty() }
    }

    @Test
    fun aLoggedBlockOrRemindersOffShowsNothing() = runBlocking {
        db.mealDao().insert(Meal(eatenAt = today.atTime(12, 5), name = "Soup", price = 120))
        settings.setRemindersOn(true)
        reminders().onAlarm(reminders().intentFor(lunch))
        delay(500)
        assertTrue(shownTags().isEmpty())

        settings.setRemindersOn(false)
        val other = lunch.copy(block = BlockDay(TimeBand.Dinner, today), at = today.atTime(20, 0))
        reminders().onAlarm(reminders().intentFor(other))
        delay(500)
        assertTrue(shownTags().isEmpty())
    }

    /** Reminders with the clock just past [lunch]'s time. */
    private fun reminders() = MealReminders(context, db.mealDao(), settings, scope, now = { lunch.at.plusMinutes(1) })

    /** Android posts and removes notifications a moment after it is asked to. */
    private suspend fun awaitTrue(condition: () -> Boolean) {
        withTimeout(3_000) {
            while (!condition()) delay(50)
        }
    }

    private fun shownTags(): List<String> = notifications.activeNotifications.mapNotNull { it.tag }
}
