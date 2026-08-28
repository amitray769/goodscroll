package com.amitray.goodscroll.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amitray.goodscroll.data.repository.AddBookmarkResult
import com.amitray.goodscroll.data.repository.BookmarkRepository
import com.amitray.goodscroll.data.repository.SortOrder
import com.amitray.goodscroll.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)

    private val eventChannel = Channel<ReaderEvent>(Channel.BUFFERED)

    /** One-off events, delivered exactly once to the single collecting screen. */
    val events = eventChannel.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ReaderUiState> = sortOrder
        .flatMapLatest { order ->
            bookmarkRepository.observeBookmarks(order).map { bookmarks ->
                ReaderUiState(bookmarks = bookmarks, sortOrder = order, isLoading = false)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ReaderUiState(),
        )

    fun onSortOrderChanged(newSortOrder: SortOrder) {
        sortOrder.value = newSortOrder
    }

    /** Entry point for the share intent (Step 4) and any in-app add. */
    fun addBookmark(
        url: String,
        title: String,
        excerpt: String? = null,
        imageUrl: String? = null,
    ) {
        viewModelScope.launch {
            val event = when (
                val result = bookmarkRepository.addBookmark(url, title, excerpt, imageUrl)
            ) {
                is AddBookmarkResult.Added -> ReaderEvent.BookmarkAdded(result.id)
                is AddBookmarkResult.Duplicate -> ReaderEvent.BookmarkAlreadySaved(result.id)
            }
            eventChannel.send(event)
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(id)
            // The worker also no-ops on a missing bookmark, but dropping the work avoids waking
            // the device for a link that no longer exists.
            reminderScheduler.cancel(id)
            eventChannel.send(ReaderEvent.BookmarkDeleted)
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch { bookmarkRepository.markAsRead(id) }
    }

    /**
     * Persists first, then schedules: the database is the source of truth and the worker re-reads
     * it, so a reminder that is stored but somehow never scheduled is recoverable, while one that
     * fires without a stored deadline would notify about nothing.
     */
    fun setReminder(id: Long, epochMillis: Long) {
        viewModelScope.launch {
            bookmarkRepository.setReminder(id, epochMillis)
            reminderScheduler.schedule(id, epochMillis)
            eventChannel.send(ReaderEvent.ReminderSet(epochMillis))
        }
    }

    fun clearReminder(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.clearReminder(id)
            reminderScheduler.cancel(id)
            eventChannel.send(ReaderEvent.ReminderCleared)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
