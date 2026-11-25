package com.stanley.foodjournal.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.stanley.foodjournal.MainActivity
import com.stanley.foodjournal.R
import com.stanley.foodjournal.data.Meal
import com.stanley.foodjournal.data.MealDao
import com.stanley.foodjournal.logic.BlockDay
import com.stanley.foodjournal.logic.Reminder
import com.stanley.foodjournal.logic.TimeBand
import com.stanley.foodjournal.logic.blockDayOf
import com.stanley.foodjournal.logic.formatClock
import com.stanley.foodjournal.logic.nextReminder
import com.stanley.foodjournal.logic.reminderStillDue
import com.stanley.foodjournal.logic.usualTimes
import com.stanley.foodjournal.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Meal reminders. One inexact alarm is kept set for the next reminder, worked out from the meals'
 * usual times; it is set again whenever the meals, the switch, or the permission change, and after
 * a restart or a change of clock. When it fires, the reminder shows only if its block is still
 * empty, and it is taken down once that block is logged.
 */
class MealReminders(
    private val context: Context,
    private val mealDao: MealDao,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** Bumped when the notification permission may have changed, so the alarm is set again. */
    private val permissionChecks = MutableStateFlow(0)

    /** Follows the meals, the switch, and the permission for as long as the app runs. */
    fun start() {
        scope.launch {
            combine(mealDao.observeAll(), settings.remindersOn, permissionChecks) { meals, on, _ -> meals to on }
                .collectLatest { (meals, on) -> update(meals, on) }
        }
    }

    /** Sets the alarm again from what is stored now, as after a restart. */
    suspend fun reschedule() = update(mealDao.observeAll().first(), settings.remindersOn.first())

    /** Reads the permission again, as after its dialog or a return from Android's settings. */
    fun onPermissionChanged() {
        permissionChecks.value++
    }

    /** Whether Android lets the app show notifications at all. */
    val canNotify: Boolean
        get() = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            notifications.areNotificationsEnabled()

    /** The alarm has fired: shows its reminder if still due, then sets the next one. */
    suspend fun onAlarm(intent: Intent) {
        val reminder = reminderFrom(intent)
        val meals = mealDao.observeAll().first()
        val on = settings.remindersOn.first()
        if (reminder != null && on && canNotify && reminderStillDue(reminder.block, reminder.at, meals, now())) {
            show(reminder)
        }
        update(meals, on)
    }

    private fun update(meals: List<Meal>, on: Boolean) {
        takeDownLogged(meals, on)
        val time = now()
        val next = if (on && canNotify) nextReminder(meals, usualTimes(meals, time.toLocalDate()), time) else null
        if (next == null) {
            alarms.cancel(alarmIntent(null))
        } else {
            // Inexact, so no exact-alarm permission is needed; it may come a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), alarmIntent(next))
        }
    }

    /** Removes reminders whose block has since been logged, or all of them once reminders are off. */
    private fun takeDownLogged(meals: List<Meal>, on: Boolean) {
        val shown = notifications.activeNotifications.filter { it.id == NOTIFICATION_ID }
        if (shown.isEmpty()) return
        val logged = meals.mapTo(HashSet()) { blockDayOf(it.eatenAt) }
        for (notification in shown) {
            val block = notification.tag?.let(::blockFromTag)
            if (!on || block == null || block in logged) notifications.cancel(notification.tag, NOTIFICATION_ID)
        }
    }

    private fun show(reminder: Reminder) {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_ADD_MEAL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text, context.getString(reminder.block.band.label), formatClock(reminder.usual)))
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        try {
            notifications.notify(tagOf(reminder.block), NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Reminder not shown", e)
        }
    }

    private fun alarmIntent(reminder: Reminder?): PendingIntent {
        val intent = reminder?.let(::intentFor) ?: Intent(context, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** What the alarm for [reminder] carries to [onAlarm]. */
    internal fun intentFor(reminder: Reminder): Intent = Intent(context, ReminderReceiver::class.java)
        .putExtra(EXTRA_BAND, reminder.block.band.name)
        .putExtra(EXTRA_DAY, reminder.block.day.toEpochDay())
        .putExtra(EXTRA_AT, reminder.at.toString())
        .putExtra(EXTRA_USUAL, reminder.usual.toString())

    private fun reminderFrom(intent: Intent): Reminder? = runCatching {
        Reminder(
            block = BlockDay(TimeBand.valueOf(intent.getStringExtra(EXTRA_BAND)!!), LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_DAY, 0))),
            at = LocalDateTime.parse(intent.getStringExtra(EXTRA_AT)!!),
            usual = LocalTime.parse(intent.getStringExtra(EXTRA_USUAL)!!),
        )
    }.getOrNull()

    private companion object {
        const val CHANNEL = "meal_reminders"
        const val NOTIFICATION_ID = 1
        const val EXTRA_BAND = "band"
        const val EXTRA_DAY = "day"
        const val EXTRA_AT = "at"
        const val EXTRA_USUAL = "usual"
        const val TAG = "FoodJournal"

        /** A shown reminder's tag names its block, "Lunch:2026-11-26", so it can be taken down later. */
        fun tagOf(block: BlockDay) = "${block.band.name}:${block.day}"

        fun blockFromTag(tag: String): BlockDay? = runCatching {
            val (band, day) = tag.split(':')
            BlockDay(TimeBand.valueOf(band), LocalDate.parse(day))
        }.getOrNull()
    }
}

/** The block's name, as Spending's time of day writes it. */
val TimeBand.label: Int
    get() = when (this) {
        TimeBand.Breakfast -> R.string.spending_breakfast
        TimeBand.Lunch -> R.string.spending_lunch
        TimeBand.Afternoon -> R.string.spending_afternoon
        TimeBand.Dinner -> R.string.spending_dinner
        TimeBand.Late -> R.string.spending_late
    }
