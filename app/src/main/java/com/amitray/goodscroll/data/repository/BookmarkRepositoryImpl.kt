package com.amitray.goodscroll.data.repository

import com.amitray.goodscroll.data.local.Bookmark
import com.amitray.goodscroll.data.local.BookmarkDao
import com.amitray.goodscroll.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BookmarkRepository {

    override fun observeBookmarks(sortOrder: SortOrder): Flow<List<Bookmark>> =
        when (sortOrder) {
            SortOrder.NEWEST_FIRST -> bookmarkDao.observeBookmarksNewestFirst()
            SortOrder.OLDEST_FIRST -> bookmarkDao.observeBookmarksOldestFirst()
        }.flowOn(ioDispatcher)

    override fun observeBookmark(id: Long): Flow<Bookmark?> =
        bookmarkDao.observeBookmark(id).flowOn(ioDispatcher)

    override suspend fun getBookmark(id: Long): Bookmark? = withContext(ioDispatcher) {
        bookmarkDao.getBookmarkById(id)
    }

    override suspend fun getBookmarkByUrl(url: String): Bookmark? = withContext(ioDispatcher) {
        bookmarkDao.getBookmarkByUrl(url)
    }

    override suspend fun addBookmark(
        url: String,
        title: String,
        excerpt: String?,
        imageUrl: String?,
    ): AddBookmarkResult = withContext(ioDispatcher) {
        val bookmark = Bookmark(
            url = url,
            title = title,
            timestamp = System.currentTimeMillis(),
            excerpt = excerpt,
            imageUrl = imageUrl,
        )
        when (val rowId = bookmarkDao.insert(bookmark)) {
            // The unique url index rejected the insert, so the link is already saved.
            -1L -> AddBookmarkResult.Duplicate(
                id = bookmarkDao.getBookmarkByUrl(url)?.id ?: NO_ID,
            )

            else -> AddBookmarkResult.Added(id = rowId)
        }
    }

    override suspend fun updateMetadata(
        id: Long,
        title: String,
        excerpt: String?,
        imageUrl: String?,
    ) = withContext(ioDispatcher) {
        bookmarkDao.updateMetadata(id, title, excerpt, imageUrl)
    }

    override suspend fun deleteBookmark(id: Long) = withContext(ioDispatcher) {
        bookmarkDao.deleteById(id)
    }

    override suspend fun markAsRead(id: Long) = withContext(ioDispatcher) {
        bookmarkDao.setRead(id, isRead = true)
    }

    override suspend fun setReminder(id: Long, epochMillis: Long) = withContext(ioDispatcher) {
        bookmarkDao.setReminderDeadline(id, epochMillis)
    }

    override suspend fun clearReminder(id: Long) = withContext(ioDispatcher) {
        bookmarkDao.setReminderDeadline(id, deadline = null)
    }

    override suspend fun markReminderFired(id: Long) = withContext(ioDispatcher) {
        bookmarkDao.markReminderFired(id)
    }

    override suspend fun getPendingReminders(): List<Bookmark> = withContext(ioDispatcher) {
        bookmarkDao.getPendingReminders()
    }

    private companion object {
        const val NO_ID = 0L
    }
}
