package com.amitray.goodscroll.data.repository

import com.amitray.goodscroll.data.local.Bookmark
import kotlinx.coroutines.flow.Flow

/** Outcome of trying to save a shared link. */
sealed interface AddBookmarkResult {
    /** The link was new; [id] is the row it was stored as. */
    data class Added(val id: Long) : AddBookmarkResult

    /** The link was already saved; [id] is the existing row, so the caller can jump to it. */
    data class Duplicate(val id: Long) : AddBookmarkResult
}

/**
 * Single source of truth over the bookmark table. Kept deliberately thin: it owns threading and
 * the duplicate-url policy, and otherwise delegates to the DAO.
 */
interface BookmarkRepository {

    fun observeBookmarks(sortOrder: SortOrder): Flow<List<Bookmark>>

    fun observeBookmark(id: Long): Flow<Bookmark?>

    suspend fun getBookmark(id: Long): Bookmark?

    suspend fun getBookmarkByUrl(url: String): Bookmark?

    suspend fun addBookmark(
        url: String,
        title: String,
        excerpt: String? = null,
        imageUrl: String? = null,
    ): AddBookmarkResult

    /** Fills in the title, excerpt and image scraped from the page after the link was saved. */
    suspend fun updateMetadata(id: Long, title: String, excerpt: String?, imageUrl: String?)

    suspend fun deleteBookmark(id: Long)

    suspend fun markAsRead(id: Long)

    suspend fun setReminder(id: Long, epochMillis: Long)

    suspend fun clearReminder(id: Long)

    /** Called by the reminder worker once the notification has been posted. */
    suspend fun markReminderFired(id: Long)

    /** Reminders that have been set but not delivered yet, used to reschedule after a reboot. */
    suspend fun getPendingReminders(): List<Bookmark>
}
