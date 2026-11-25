package com.stanley.foodjournal.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stanley.foodjournal.FoodJournalApplication
import kotlinx.coroutines.launch

/** The reminder alarm. It shows the reminder if still due, then sets the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as FoodJournalApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.reminders.onAlarm(intent)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Sets the reminder alarm again after what clears or shifts it: a restart, an update of the app,
 * or a change of the clock or time zone.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = (context.applicationContext as FoodJournalApplication).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.reminders.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}

/** The system broadcasts the manifest lists for [RescheduleReceiver]; anything else is ignored. */
private val ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
)
