package com.amitray.goodscroll.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    /**
     * Inserts a bookmark, ignoring the insert when [Bookmark.url] is already saved. Ignoring rather
     * than replacing keeps the existing row's id, read state and reminder intact.
     *
     * @return the new row id, or -1 when the url was already present.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC, id DESC")
    fun observeBookmarksNewestFirst(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY timestamp ASC, id ASC")
    fun observeBookmarksOldestFirst(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    fun observeBookmark(id: Long): Flow<Bookmark?>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun getBookmarkById(id: Long): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE url = :url")
    suspend fun getBookmarkByUrl(url: String): Bookmark?

    /**
     * Every bookmark whose reminder has not been delivered yet, soonest first. Overdue reminders
     * are included on purpose: after a reboot their alarms are gone, so they still need to be
     * rescheduled (immediately) rather than silently dropped.
     */
    @Query(
        """
        SELECT * FROM bookmarks
        WHERE reminderDeadline IS NOT NULL AND reminderFired = 0
        ORDER BY reminderDeadline ASC
        """
    )
    suspend fun getPendingReminders(): List<Bookmark>

    /** Sets a new reminder, or clears it with a null [deadline]. Resets the delivered flag. */
    @Query("UPDATE bookmarks SET reminderDeadline = :deadline, reminderFired = 0 WHERE id = :id")
    suspend fun setReminderDeadline(id: Long, deadline: Long?)

    @Query("UPDATE bookmarks SET reminderFired = 1 WHERE id = :id")
    suspend fun markReminderFired(id: Long)

    @Query("UPDATE bookmarks SET isRead = :isRead WHERE id = :id")
    suspend fun setRead(id: Long, isRead: Boolean)

    /**
     * Overwrites the scraped fields once the shared page has been read. Saving happens first with a
     * fallback title so a flaky network can never lose a bookmark; this fills in the details after.
     */
    @Query(
        "UPDATE bookmarks SET title = :title, excerpt = :excerpt, imageUrl = :imageUrl WHERE id = :id"
    )
    suspend fun updateMetadata(id: Long, title: String, excerpt: String?, imageUrl: String?)
}
