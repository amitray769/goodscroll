package com.amitray.goodscroll.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTest {

    private lateinit var database: GoodScrollDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GoodScrollDatabase::class.java)
            .build()
        dao = database.bookmarkDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertedBookmarkIsReadBack() = runTest {
        val id = dao.insert(bookmark(url = "https://example.com/a", title = "A", timestamp = 100L))

        val stored = dao.getBookmarkById(id)

        assertNotNull(stored)
        assertEquals("https://example.com/a", stored?.url)
        assertEquals("A", stored?.title)
        assertEquals(100L, stored?.timestamp)
        assertFalse(stored!!.isRead)
        assertNull(stored.reminderDeadline)
        assertEquals(stored, dao.getBookmarkByUrl("https://example.com/a"))
    }

    @Test
    fun observedListsAreOrderedNewestAndOldestFirst() = runTest {
        dao.insert(bookmark(url = "https://example.com/old", title = "Old", timestamp = 100L))
        dao.insert(bookmark(url = "https://example.com/mid", title = "Mid", timestamp = 200L))
        dao.insert(bookmark(url = "https://example.com/new", title = "New", timestamp = 300L))

        val newestFirst = dao.observeBookmarksNewestFirst().first().map { it.title }
        val oldestFirst = dao.observeBookmarksOldestFirst().first().map { it.title }

        assertEquals(listOf("New", "Mid", "Old"), newestFirst)
        assertEquals(listOf("Old", "Mid", "New"), oldestFirst)
        assertEquals(newestFirst, oldestFirst.reversed())
    }

    @Test
    fun insertingDuplicateUrlIsIgnoredAndKeepsOriginalRow() = runTest {
        val originalId =
            dao.insert(bookmark(url = "https://example.com/a", title = "First", timestamp = 100L))
        dao.setRead(originalId, isRead = true)

        val duplicateRowId =
            dao.insert(bookmark(url = "https://example.com/a", title = "Second", timestamp = 500L))

        assertEquals(-1L, duplicateRowId)
        val all = dao.observeBookmarksNewestFirst().first()
        assertEquals(1, all.size)
        assertEquals(originalId, all.single().id)
        assertEquals("First", all.single().title)
        assertTrue("read state must survive a re-share", all.single().isRead)
    }

    @Test
    fun reminderCanBeSetClearedAndMarkedFired() = runTest {
        val id = dao.insert(bookmark(url = "https://example.com/a", title = "A", timestamp = 100L))

        dao.setReminderDeadline(id, deadline = 1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, dao.getBookmarkById(id)?.reminderDeadline)
        assertEquals(listOf(id), dao.getPendingReminders().map { it.id })

        dao.markReminderFired(id)
        assertTrue(dao.getBookmarkById(id)!!.reminderFired)
        assertTrue("fired reminders must not be rescheduled", dao.getPendingReminders().isEmpty())

        dao.setReminderDeadline(id, deadline = 1_800_000_000_000L)
        assertFalse("setting a new reminder resets the fired flag", dao.getBookmarkById(id)!!.reminderFired)

        dao.setReminderDeadline(id, deadline = null)
        assertNull(dao.getBookmarkById(id)?.reminderDeadline)
        assertTrue(dao.getPendingReminders().isEmpty())
    }

    @Test
    fun observeBookmarkEmitsNullAfterDelete() = runTest {
        val id = dao.insert(bookmark(url = "https://example.com/a", title = "A", timestamp = 100L))
        assertNotNull(dao.observeBookmark(id).first())

        dao.deleteById(id)

        assertNull(dao.observeBookmark(id).first())
        assertTrue(dao.observeBookmarksNewestFirst().first().isEmpty())
    }

    private fun bookmark(url: String, title: String, timestamp: Long) = Bookmark(
        url = url,
        title = title,
        timestamp = timestamp,
    )
}
