package com.amitray.goodscroll.data.repository

import com.amitray.goodscroll.data.local.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-in for the real repository. Applies the same ordering and duplicate-url rules as
 * the DAO so ViewModel tests exercise realistic behaviour without Room.
 */
class FakeBookmarkRepository : BookmarkRepository {

    private val bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())

    private var nextId = 1L

    var now: Long = 1_000L

    fun setBookmarks(vararg values: Bookmark) {
        bookmarks.value = values.toList()
    }

    fun currentBookmarks(): List<Bookmark> = bookmarks.value

    override fun observeBookmarks(sortOrder: SortOrder): Flow<List<Bookmark>> =
        bookmarks.map { list ->
            when (sortOrder) {
                SortOrder.NEWEST_FIRST -> list.sortedByDescending { it.timestamp }
                SortOrder.OLDEST_FIRST -> list.sortedBy { it.timestamp }
            }
        }

    override fun observeBookmark(id: Long): Flow<Bookmark?> =
        bookmarks.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun getBookmark(id: Long): Bookmark? =
        bookmarks.value.firstOrNull { it.id == id }

    override suspend fun getBookmarkByUrl(url: String): Bookmark? =
        bookmarks.value.firstOrNull { it.url == url }

    override suspend fun addBookmark(
        url: String,
        title: String,
        excerpt: String?,
        imageUrl: String?,
    ): AddBookmarkResult {
        val existing = getBookmarkByUrl(url)
        if (existing != null) return AddBookmarkResult.Duplicate(existing.id)

        val bookmark = Bookmark(
            id = nextId++,
            url = url,
            title = title,
            timestamp = now,
            excerpt = excerpt,
            imageUrl = imageUrl,
        )
        bookmarks.value += bookmark
        return AddBookmarkResult.Added(bookmark.id)
    }

    override suspend fun deleteBookmark(id: Long) {
        bookmarks.value = bookmarks.value.filterNot { it.id == id }
    }

    override suspend fun markAsRead(id: Long) = update(id) { it.copy(isRead = true) }

    override suspend fun setReminder(id: Long, epochMillis: Long) =
        update(id) { it.copy(reminderDeadline = epochMillis, reminderFired = false) }

    override suspend fun clearReminder(id: Long) =
        update(id) { it.copy(reminderDeadline = null, reminderFired = false) }

    override suspend fun markReminderFired(id: Long) = update(id) { it.copy(reminderFired = true) }

    override suspend fun getPendingReminders(): List<Bookmark> = bookmarks.value
        .filter { it.reminderDeadline != null && !it.reminderFired }
        .sortedBy { it.reminderDeadline }

    private fun update(id: Long, transform: (Bookmark) -> Bookmark) {
        bookmarks.value = bookmarks.value.map { if (it.id == id) transform(it) else it }
    }
}
