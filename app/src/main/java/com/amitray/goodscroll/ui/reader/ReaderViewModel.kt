package com.amitray.goodscroll.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amitray.goodscroll.data.repository.AddBookmarkResult
import com.amitray.goodscroll.data.repository.BookmarkRepository
import com.amitray.goodscroll.data.repository.SortOrder
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
            eventChannel.send(ReaderEvent.BookmarkDeleted)
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch { bookmarkRepository.markAsRead(id) }
    }

    fun setReminder(id: Long, epochMillis: Long) {
        viewModelScope.launch {
            bookmarkRepository.setReminder(id, epochMillis)
            // TODO(Step 6): schedule the WorkManager reminder for this bookmark here.
            eventChannel.send(ReaderEvent.ReminderSet(epochMillis))
        }
    }

    fun clearReminder(id: Long) {
        viewModelScope.launch {
            bookmarkRepository.clearReminder(id)
            // TODO(Step 6): cancel the scheduled WorkManager reminder for this bookmark here.
            eventChannel.send(ReaderEvent.ReminderCleared)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
