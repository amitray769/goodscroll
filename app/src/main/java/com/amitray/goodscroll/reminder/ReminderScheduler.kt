package com.amitray.goodscroll.reminder

/**
 * Schedules the background job that posts a bookmark's reminder notification.
 *
 * Reminders are keyed purely by bookmark id: there is no stored request id, because every
 * operation goes through [uniqueWorkName], so re-setting or cancelling a reminder only needs the
 * bookmark it belongs to.
 */
interface ReminderScheduler {

    /**
     * Schedules (or replaces) the reminder for [bookmarkId] to fire at [deadlineMillis], an epoch
     * millis wall-clock time. Call this after the deadline has been persisted: the database is the
     * source of truth and the worker re-reads it before notifying.
     */
    fun schedule(bookmarkId: Long, deadlineMillis: Long)

    /** Drops any reminder scheduled for [bookmarkId]. Safe to call when nothing is scheduled. */
    fun cancel(bookmarkId: Long)

    companion object {

        /** One slot per bookmark, so scheduling twice replaces rather than duplicates. */
        fun uniqueWorkName(bookmarkId: Long): String = "reminder-$bookmarkId"

        /**
         * How long to wait before firing the reminder for [deadlineMillis], given [nowMillis].
         *
         * Clamped at zero so an already-overdue deadline — the reminder was set for a moment that
         * has passed, or the device was off when it came due — fires as soon as possible instead
         * of being dropped or, worse, scheduled into the past.
         */
        fun delayMillis(deadlineMillis: Long, nowMillis: Long): Long =
            (deadlineMillis - nowMillis).coerceAtLeast(0L)
    }
}
