package com.amitray.goodscroll.reminder

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reminders run on WorkManager rather than exact alarms.
 *
 * A "read this article" nudge does not need second-level punctuality, and WorkManager buys three
 * things an [android.app.AlarmManager] implementation would have to rebuild by hand: the queue is
 * persisted across process death and reboots, Doze deferral is handled for us, and — the reason
 * that matters commercially — the app needs no exact-alarm permission, which is subject to Play
 * Store policy review. The cost is delivery inside a maintenance window rather than to the second.
 */
@Singleton
class WorkManagerReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val timeProvider: TimeProvider,
) : ReminderScheduler {

    override fun schedule(bookmarkId: Long, deadlineMillis: Long) {
        val delay = ReminderScheduler.delayMillis(deadlineMillis, timeProvider.nowMillis())
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_BOOKMARK_ID to bookmarkId))
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
            .addTag(TAG_REMINDER)
            .build()

        workManager.enqueueUniqueWork(
            ReminderScheduler.uniqueWorkName(bookmarkId),
            // REPLACE, so moving a reminder overwrites the old slot instead of firing twice.
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(bookmarkId: Long) {
        workManager.cancelUniqueWork(ReminderScheduler.uniqueWorkName(bookmarkId))
    }

    private companion object {
        /** Lets every reminder be inspected or cancelled as a group in debugging tools. */
        const val TAG_REMINDER = "reminder"

        const val BACKOFF_DELAY_MINUTES = 5L
    }
}
