package com.amitray.goodscroll.notification

import android.content.Context
import android.content.Intent
import com.amitray.goodscroll.MainActivity

/**
 * The contract between a reminder notification and the activity it opens.
 *
 * Tapping a reminder must land the user on the bookmark the reminder was set for, so the
 * notification launches [MainActivity] with the bookmark id attached. The activity reads it back
 * with [bookmarkIdFrom] from both `onCreate`'s intent and `onNewIntent`, since a warm app is
 * re-delivered the intent rather than recreated.
 */
object ReminderDeepLink {

    /** Distinguishes a reminder launch from the launcher icon or a share. */
    const val ACTION_OPEN_BOOKMARK = "com.amitray.goodscroll.action.OPEN_BOOKMARK"

    /** `Long` extra holding [com.amitray.goodscroll.data.local.Bookmark.id]. */
    const val EXTRA_BOOKMARK_ID = "com.amitray.goodscroll.extra.BOOKMARK_ID"

    fun createIntent(context: Context, bookmarkId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_BOOKMARK
            putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
            // CLEAR_TOP keeps a single reader task instead of stacking one activity per reminder.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** The bookmark a reminder wants opened, or null when [intent] is not a reminder launch. */
    fun bookmarkIdFrom(intent: Intent?): Long? {
        if (intent?.action != ACTION_OPEN_BOOKMARK) return null
        return intent.getLongExtra(EXTRA_BOOKMARK_ID, NO_BOOKMARK_ID).takeIf { it != NO_BOOKMARK_ID }
    }

    private const val NO_BOOKMARK_ID = -1L
}
