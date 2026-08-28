package com.amitray.goodscroll.notification

import com.amitray.goodscroll.data.local.Bookmark

/** Posts the "time to read this" notification for a bookmark whose reminder has come due. */
interface ReminderNotifier {

    /**
     * Shows the reminder for [bookmark].
     *
     * @return true when the notification was handed to the system, false when it was suppressed
     * because the user has not granted `POST_NOTIFICATIONS`. Callers should treat false as a
     * delivered-but-invisible reminder rather than a failure worth retrying: the permission will
     * not appear on its own while a background worker is running.
     */
    fun notifyReminder(bookmark: Bookmark): Boolean
}
