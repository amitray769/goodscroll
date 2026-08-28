package com.amitray.goodscroll.ui.reminder

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amitray.goodscroll.R
import com.amitray.goodscroll.ui.theme.GoodScrollTheme
import java.util.Calendar
import java.util.TimeZone

/**
 * Two-step date-then-time picker for a bookmark's reading reminder.
 *
 * Self-contained on purpose: it owns no view model and reads no shared state, so a reader screen
 * only has to show it and hand the chosen epoch millis back to
 * [com.amitray.goodscroll.ui.reader.ReaderViewModel.setReminder].
 *
 * @param initialDeadline the reminder already set on the bookmark, or null for a new one. When
 * non-null a "remove reminder" action appears.
 * @param onConfirm receives the chosen wall-clock time as epoch millis.
 * @param onClear the user removed an existing reminder.
 * @param onDismiss the user backed out without changing anything.
 * @param nowMillis injectable clock, so previews and tests are deterministic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPickerDialog(
    initialDeadline: Long?,
    onConfirm: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val startingPoint = initialDeadline ?: defaultDeadline(nowMillis)
    var step by rememberSaveable { mutableStateOf(ReminderPickerStep.Date) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = toUtcDateMillis(startingPoint),
        selectableDates = remember(nowMillis) { DatesFrom(nowMillis) },
    )
    val startingClock = remember(startingPoint) {
        Calendar.getInstance().apply { timeInMillis = startingPoint }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = startingClock.get(Calendar.HOUR_OF_DAY),
        initialMinute = startingClock.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )

    val selectedDate = datePickerState.selectedDateMillis

    when (step) {
        ReminderPickerStep.Date -> DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { step = ReminderPickerStep.Time },
                    enabled = selectedDate != null,
                ) {
                    Text(stringResource(R.string.reminder_picker_next))
                }
            },
            dismissButton = { SecondaryActions(initialDeadline, onClear, onDismiss) },
        ) {
            DatePicker(
                state = datePickerState,
                title = { PickerTitle(stringResource(R.string.reminder_picker_pick_date)) },
            )
        }

        ReminderPickerStep.Time -> {
            val chosen = toEpochMillis(
                utcDateMillis = selectedDate ?: toUtcDateMillis(startingPoint),
                hour = timePickerState.hour,
                minute = timePickerState.minute,
            )
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.reminder_picker_pick_time)) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TimePicker(state = timePickerState)
                        if (chosen <= nowMillis) {
                            // Not an error: the scheduler clamps overdue delays to zero, so this
                            // reminder simply arrives immediately.
                            Text(
                                text = stringResource(R.string.reminder_picker_past_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onConfirm(chosen) }) {
                        Text(stringResource(R.string.reminder_picker_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { step = ReminderPickerStep.Date }) {
                        Text(stringResource(R.string.reminder_picker_back))
                    }
                },
            )
        }
    }
}

@Composable
private fun SecondaryActions(
    initialDeadline: Long?,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (initialDeadline != null) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.reminder_picker_clear))
            }
        }
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.reminder_picker_cancel))
        }
    }
}

@Composable
private fun PickerTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
    )
}

private enum class ReminderPickerStep { Date, Time }

/** Only today onwards: a reminder for last week is never what the user meant to tap. */
@OptIn(ExperimentalMaterial3Api::class)
private class DatesFrom(nowMillis: Long) : SelectableDates {

    private val earliestUtcDay = toUtcDateMillis(nowMillis)
    private val earliestYear = Calendar.getInstance()
        .apply { timeInMillis = nowMillis }
        .get(Calendar.YEAR)

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= earliestUtcDay

    override fun isSelectableYear(year: Int): Boolean = year >= earliestYear
}

/** A gentle default for a fresh reminder: an hour from now, on the minute. */
private fun defaultDeadline(nowMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = nowMillis + DEFAULT_OFFSET_MILLIS
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * [DatePicker] speaks UTC midnight of the selected day, while reminders are local wall-clock
 * times, so the two conversions below are deliberately explicit.
 *
 * `java.util.Calendar` rather than `java.time`, because minSdk is 24 and core library desugaring
 * is not enabled.
 */
internal fun toUtcDateMillis(localEpochMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localEpochMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

internal fun toEpochMillis(utcDateMillis: Long, hour: Int, minute: Int): Long {
    val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDateMillis
    }
    return Calendar.getInstance().apply {
        clear()
        set(
            utcDay.get(Calendar.YEAR),
            utcDay.get(Calendar.MONTH),
            utcDay.get(Calendar.DAY_OF_MONTH),
            hour,
            minute,
        )
    }.timeInMillis
}

private const val DEFAULT_OFFSET_MILLIS = 60L * 60L * 1000L

@Preview(name = "New reminder, date step", showBackground = true)
@Composable
private fun ReminderPickerDatePreview() {
    GoodScrollTheme {
        ReminderPickerDialog(
            initialDeadline = null,
            onConfirm = {},
            onClear = {},
            onDismiss = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Preview(name = "Existing reminder, date step", showBackground = true)
@Composable
private fun ReminderPickerExistingPreview() {
    GoodScrollTheme {
        ReminderPickerDialog(
            initialDeadline = PREVIEW_NOW + 3L * 24 * 60 * 60 * 1000,
            onConfirm = {},
            onClear = {},
            onDismiss = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

/** Fixed instant so previews do not shift with the wall clock: 2024-06-01T09:00Z. */
private const val PREVIEW_NOW = 1_717_232_400_000L
