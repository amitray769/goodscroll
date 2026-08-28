package com.amitray.goodscroll.reminder

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.amitray.goodscroll.data.repository.BookmarkRepository
import com.amitray.goodscroll.notification.ReminderNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Posts one bookmark's reminder notification.
 *
 * The work is scheduled long before it runs, so by the time it does the bookmark may have been
 * deleted, its reminder cleared, or the reminder already delivered. The database is re-read here
 * and each of those cases is a silent no-op — a notification for a link the user has thrown away
 * is worse than a missing one.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val bookmarkRepository: BookmarkRepository,
    private val reminderNotifier: ReminderNotifier,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val bookmarkId = inputData.getLong(KEY_BOOKMARK_ID, NO_BOOKMARK_ID)
        // Only reachable if the work was enqueued wrong; retrying cannot fix bad input.
        if (bookmarkId == NO_BOOKMARK_ID) return Result.failure()

        return try {
            val bookmark = bookmarkRepository.getBookmark(bookmarkId)
            when {
                // Deleted, cleared or already delivered: nothing left to do, and re-running would
                // not change that, so this is success rather than failure.
                bookmark == null -> Result.success()
                bookmark.reminderDeadline == null -> Result.success()
                bookmark.reminderFired -> Result.success()

                else -> {
                    // Marked fired even when the notification was suppressed for a missing
                    // POST_NOTIFICATIONS grant: the moment has passed, and leaving it pending
                    // would re-deliver it on every future reschedule.
                    reminderNotifier.notifyReminder(bookmark)
                    bookmarkRepository.markReminderFired(bookmarkId)
                    Result.success()
                }
            }
        } catch (cancellation: CancellationException) {
            // WorkManager stopping us, not a failure: let it propagate so the job is not retried
            // as if the work itself had broken.
            throw cancellation
        } catch (error: Exception) {
            Log.w(TAG, "Reminder for bookmark $bookmarkId failed on attempt $runAttemptCount", error)
            // Database or notification-manager trouble is usually transient (disk pressure, the
            // process being torn down mid-write), so let WorkManager back off and try again.
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_BOOKMARK_ID = "bookmark_id"

        private const val NO_BOOKMARK_ID = -1L

        /** Enough to ride out a transient failure; a reminder is stale after that anyway. */
        private const val MAX_ATTEMPTS = 3

        private const val TAG = "ReminderWorker"
    }
}
