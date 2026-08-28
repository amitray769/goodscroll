package com.amitray.goodscroll

import android.content.Context
import android.content.Intent

/**
 * The contract for launching [MainActivity] straight onto one bookmark.
 *
 * Two paths use it: tapping a reminder notification, and tapping "Read now" on the share
 * confirmation. Both must land the user on the bookmark in question, so the action and extra live in
 * one place rather than being spelled out at each call site.
 *
 * [MainActivity] reads it back with [bookmarkIdFrom] in both `onCreate` and `onNewIntent`, since a
 * warm process is handed the new intent rather than being recreated.
 */
object OpenBookmarkIntent {

    /** Distinguishes an "open this bookmark" launch from the launcher icon. */
    const val ACTION_OPEN_BOOKMARK = "com.amitray.goodscroll.action.OPEN_BOOKMARK"

    /** `Long` extra holding [com.amitray.goodscroll.data.local.Bookmark.id]. */
    const val EXTRA_BOOKMARK_ID = "com.amitray.goodscroll.extra.BOOKMARK_ID"

    fun createIntent(context: Context, bookmarkId: Long): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_BOOKMARK
            putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
            // CLEAR_TOP keeps a single reader task instead of stacking one activity per launch.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    /** The bookmark [intent] wants opened, or null when it is an ordinary launch. */
    fun bookmarkIdFrom(intent: Intent?): Long? {
        if (intent?.action != ACTION_OPEN_BOOKMARK) return null
        return intent.getLongExtra(EXTRA_BOOKMARK_ID, NO_BOOKMARK_ID).takeIf { it != NO_BOOKMARK_ID }
    }

    private const val NO_BOOKMARK_ID = -1L
}
