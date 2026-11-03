package com.stanley.foodjournal.ui.meal

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.stanley.foodjournal.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val DAY_MILLIS = 86_400_000L

/**
 * Edits when a meal was eaten: the date first, then the time. Days after [today] can't be
 * picked. Material's date picker works in UTC midnights, so dates convert as epoch days.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTimeDialogs(
    eatenAt: LocalDateTime,
    today: LocalDate,
    onDone: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    val day = pickedDay

    if (day == null) {
        val lastDay = today.toEpochDay()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = eatenAt.toLocalDate().toEpochDay() * DAY_MILLIS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / DAY_MILLIS <= lastDay

                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    pickedDay = (dateState.selectedDateMillis ?: return@TextButton) / DAY_MILLIS
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = dateState, showModeToggle = false)
        }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = eatenAt.hour,
            initialMinute = eatenAt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    onDone(LocalDate.ofEpochDay(day).atTime(LocalTime.of(timeState.hour, timeState.minute)))
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}
