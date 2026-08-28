package com.amitray.goodscroll.ui.reader

import com.amitray.goodscroll.data.local.Bookmark
import com.amitray.goodscroll.data.repository.FakeBookmarkRepository
import com.amitray.goodscroll.data.repository.SortOrder
import com.amitray.goodscroll.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeBookmarkRepository
    private lateinit var viewModel: ReaderViewModel

    @Before
    fun setUp() {
        repository = FakeBookmarkRepository()
        viewModel = ReaderViewModel(repository)
    }

    @Test
    fun `initial state is loading before anything is collected`() {
        val initial = viewModel.uiState.value

        assertTrue(initial.isLoading)
        assertTrue(initial.bookmarks.isEmpty())
        assertFalse("loading must not look like the empty state", initial.isEmpty)
        assertEquals(SortOrder.NEWEST_FIRST, initial.sortOrder)
    }

    @Test
    fun `empty database resolves to the empty state, not loading`() = runTest {
        collectUiState()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `bookmarks are exposed newest first once loaded`() = runTest {
        repository.setBookmarks(old, mid, new)

        collectUiState()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isEmpty)
        assertEquals(listOf("New", "Mid", "Old"), state.bookmarks.map { it.title })
    }

    @Test
    fun `changing sort order re-emits the list in the new order`() = runTest {
        repository.setBookmarks(old, mid, new)
        collectUiState()

        viewModel.onSortOrderChanged(SortOrder.OLDEST_FIRST)

        val state = viewModel.uiState.value
        assertEquals(SortOrder.OLDEST_FIRST, state.sortOrder)
        assertEquals(listOf("Old", "Mid", "New"), state.bookmarks.map { it.title })

        viewModel.onSortOrderChanged(SortOrder.NEWEST_FIRST)
        assertEquals(listOf("New", "Mid", "Old"), viewModel.uiState.value.bookmarks.map { it.title })
    }

    @Test
    fun `adding a new url stores it and emits a bookmark added event`() = runTest {
        collectUiState()

        viewModel.addBookmark(url = "https://example.com/a", title = "A")

        val event = viewModel.events.first()
        assertTrue(event is ReaderEvent.BookmarkAdded)
        assertEquals(listOf("A"), viewModel.uiState.value.bookmarks.map { it.title })
    }

    @Test
    fun `re-adding a saved url emits already saved with the existing id`() = runTest {
        repository.setBookmarks(old)
        collectUiState()
        val events = collectEvents()

        viewModel.addBookmark(url = old.url, title = "Different title")

        assertEquals(listOf(ReaderEvent.BookmarkAlreadySaved(old.id)), events)
        assertEquals(1, viewModel.uiState.value.bookmarks.size)
        assertEquals("Old", viewModel.uiState.value.bookmarks.single().title)
    }

    @Test
    fun `deleting removes the bookmark and emits an event`() = runTest {
        repository.setBookmarks(old, mid)
        collectUiState()
        val events = collectEvents()

        viewModel.deleteBookmark(old.id)

        assertEquals(listOf(ReaderEvent.BookmarkDeleted), events)
        assertEquals(listOf("Mid"), viewModel.uiState.value.bookmarks.map { it.title })
    }

    @Test
    fun `marking as read updates the bookmark`() = runTest {
        repository.setBookmarks(old)
        collectUiState()

        viewModel.markAsRead(old.id)

        assertTrue(viewModel.uiState.value.bookmarks.single().isRead)
    }

    @Test
    fun `setting and clearing a reminder persists the deadline and emits events`() = runTest {
        repository.setBookmarks(old)
        collectUiState()
        val events = collectEvents()

        viewModel.setReminder(old.id, epochMillis = 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, viewModel.uiState.value.bookmarks.single().reminderDeadline)

        viewModel.clearReminder(old.id)

        assertNull(viewModel.uiState.value.bookmarks.single().reminderDeadline)
        assertEquals(
            listOf(ReaderEvent.ReminderSet(1_700_000_000_000L), ReaderEvent.ReminderCleared),
            events,
        )
    }

    /**
     * Keeps `WhileSubscribed` sharing alive for the test. Collected on an unconfined dispatcher so
     * emissions land before the assertions that follow, without manual scheduler advancing.
     */
    private fun TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private fun TestScope.collectEvents(): List<ReaderEvent> {
        val received = mutableListOf<ReaderEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.toList(received)
        }
        return received
    }

    private companion object {
        val old = Bookmark(id = 1L, url = "https://example.com/old", title = "Old", timestamp = 100L)
        val mid = Bookmark(id = 2L, url = "https://example.com/mid", title = "Mid", timestamp = 200L)
        val new = Bookmark(id = 3L, url = "https://example.com/new", title = "New", timestamp = 300L)
    }
}
