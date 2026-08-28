package com.amitray.goodscroll.ui.reader

import com.amitray.goodscroll.data.local.Bookmark
import com.amitray.goodscroll.data.repository.SortOrder

/**
 * Everything the reading screen renders, as one immutable snapshot.
 */
data class ReaderUiState(
    val bookmarks: List<Bookmark> = emptyList(),
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val isLoading: Boolean = true,
) {
    /** True only once the database has answered and there is genuinely nothing saved. */
    val isEmpty: Boolean get() = !isLoading && bookmarks.isEmpty()
}

/**
 * One-off things the screen should react to once, such as showing a snackbar. Modelled as typed
 * events rather than strings so the wording lives in the UI layer's string resources.
 */
sealed interface ReaderEvent {
    data class BookmarkAdded(val id: Long) : ReaderEvent

    /** The shared url was already saved; [id] is the existing bookmark. */
    data class BookmarkAlreadySaved(val id: Long) : ReaderEvent

    data object BookmarkDeleted : ReaderEvent

    data class ReminderSet(val epochMillis: Long) : ReaderEvent

    data object ReminderCleared : ReaderEvent
}
