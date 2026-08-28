package com.amitray.goodscroll.reminder

/** Records what the ViewModel asked for, without touching WorkManager. */
class FakeReminderScheduler : ReminderScheduler {

    sealed interface Call {
        data class Schedule(val bookmarkId: Long, val deadlineMillis: Long) : Call

        data class Cancel(val bookmarkId: Long) : Call
    }

    val calls = mutableListOf<Call>()

    /** Hook for asserting what the database already looked like when scheduling happened. */
    var onSchedule: (bookmarkId: Long, deadlineMillis: Long) -> Unit = { _, _ -> }

    override fun schedule(bookmarkId: Long, deadlineMillis: Long) {
        calls += Call.Schedule(bookmarkId, deadlineMillis)
        onSchedule(bookmarkId, deadlineMillis)
    }

    override fun cancel(bookmarkId: Long) {
        calls += Call.Cancel(bookmarkId)
    }
}
