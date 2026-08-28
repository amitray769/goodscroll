package com.amitray.goodscroll.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amitray.goodscroll.R
import com.amitray.goodscroll.data.local.Bookmark
import com.amitray.goodscroll.data.repository.SortOrder
import com.amitray.goodscroll.ui.theme.GoodScrollTheme

/** Identifies the pager in ui tests. */
const val READER_PAGER_TAG = "reader_pager"

/**
 * The whole reading experience, with no dependency on a ViewModel so it can be previewed and tested
 * with any state. [ReaderRoute] supplies the real state.
 *
 * @param focusedBookmarkId when set, the pager animates to that bookmark; used after a share so the
 *   user lands on the link they just sent in.
 * @param onSetReminderClick reminder scheduling belongs to a later step. While this is null the
 *   overflow menu simply does not offer the action, so there is no dead UI; passing a lambda from
 *   [ReaderRoute] is all that is needed to turn it on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uiState: ReaderUiState,
    onSortOrderChanged: (SortOrder) -> Unit,
    onBookmarkSettled: (Bookmark) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onOpenArticle: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    focusedBookmarkId: Long? = null,
    onFocusHandled: () -> Unit = {},
    onSetReminderClick: ((Bookmark) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { uiState.bookmarks.size })
    val currentBookmark = uiState.bookmarks.getOrNull(pagerState.currentPage)
    var pendingDeletion by remember { mutableStateOf<Bookmark?>(null) }

    // settledPage rather than currentPage: a page counts as read once the fling has come to rest,
    // not while it is flying past under the user's thumb.
    LaunchedEffect(pagerState, uiState.bookmarks) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            uiState.bookmarks.getOrNull(page)
                ?.takeUnless { it.isRead }
                ?.let(onBookmarkSettled)
        }
    }

    LaunchedEffect(focusedBookmarkId, uiState.bookmarks) {
        val index = uiState.bookmarks.indexOfFirst { it.id == focusedBookmarkId }
        if (focusedBookmarkId != null && index >= 0) {
            pagerState.animateScrollToPage(index)
            onFocusHandled()
        }
    }

    // Reordering the library invalidates the current position, so start the new order at the top.
    var appliedSortOrder by remember { mutableStateOf(uiState.sortOrder) }
    LaunchedEffect(uiState.sortOrder) {
        if (appliedSortOrder != uiState.sortOrder) {
            appliedSortOrder = uiState.sortOrder
            pagerState.scrollToPage(0)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            ReaderTopBar(
                sortOrder = uiState.sortOrder,
                currentBookmark = currentBookmark,
                onSortOrderChanged = onSortOrderChanged,
                onDeleteClick = { pendingDeletion = it },
                onSetReminderClick = onSetReminderClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> LoadingState()

                uiState.isEmpty -> EmptyLibrary()

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> uiState.bookmarks[page].id },
                        // The spec's gesture: dragging right moves forward. HorizontalPager does
                        // the opposite by default, and reverseLayout is the one flag that flips
                        // both the drag and the fling, so page index still matches list order.
                        reverseLayout = true,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        pageSpacing = 12.dp,
                        beyondViewportPageCount = 1,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(READER_PAGER_TAG),
                    ) { page ->
                        BookmarkPage(
                            bookmark = uiState.bookmarks[page],
                            position = stringResource(
                                R.string.reader_position,
                                page + 1,
                                uiState.bookmarks.size,
                            ),
                            onOpenArticle = onOpenArticle,
                        )
                    }

                    if (uiState.bookmarks.size > 1) {
                        Text(
                            text = stringResource(R.string.reader_swipe_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }

    pendingDeletion?.let { bookmark ->
        DeleteConfirmationDialog(
            bookmark = bookmark,
            onConfirm = {
                pendingDeletion = null
                onDeleteBookmark(bookmark.id)
            },
            onDismiss = { pendingDeletion = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    sortOrder: SortOrder,
    currentBookmark: Bookmark?,
    onSortOrderChanged: (SortOrder) -> Unit,
    onDeleteClick: (Bookmark) -> Unit,
    onSetReminderClick: ((Bookmark) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = stringResource(R.string.reader_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        actions = {
            TextButton(
                onClick = {
                    onSortOrderChanged(
                        when (sortOrder) {
                            SortOrder.NEWEST_FIRST -> SortOrder.OLDEST_FIRST
                            SortOrder.OLDEST_FIRST -> SortOrder.NEWEST_FIRST
                        }
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(R.string.reader_action_sort),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when (sortOrder) {
                        SortOrder.NEWEST_FIRST -> stringResource(R.string.reader_sort_newest_first)
                        SortOrder.OLDEST_FIRST -> stringResource(R.string.reader_sort_oldest_first)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            IconButton(
                onClick = { menuExpanded = true },
                enabled = currentBookmark != null,
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.reader_action_more),
                )
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (onSetReminderClick != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reader_action_remind)) },
                        leadingIcon = {
                            Icon(Icons.Filled.NotificationsNone, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            currentBookmark?.let(onSetReminderClick)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_action_delete)) },
                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        currentBookmark?.let(onDeleteClick)
                    },
                )
            }
        },
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.reader_loading),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 72.dp),
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    bookmark: Bookmark,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_delete_title)) },
        text = { Text(stringResource(R.string.reader_delete_message, bookmark.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.reader_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_delete_cancel))
            }
        },
    )
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ReaderScreenPreview() {
    GoodScrollTheme(dynamicColor = false) {
        ReaderScreen(
            uiState = ReaderUiState(bookmarks = previewBookmarks, isLoading = false),
            onSortOrderChanged = {},
            onBookmarkSettled = {},
            onDeleteBookmark = {},
            onOpenArticle = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ReaderScreenEmptyPreview() {
    GoodScrollTheme(dynamicColor = false) {
        ReaderScreen(
            uiState = ReaderUiState(bookmarks = emptyList(), isLoading = false),
            onSortOrderChanged = {},
            onBookmarkSettled = {},
            onDeleteBookmark = {},
            onOpenArticle = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun ReaderScreenLoadingPreview() {
    GoodScrollTheme(dynamicColor = false) {
        ReaderScreen(
            uiState = ReaderUiState(),
            onSortOrderChanged = {},
            onBookmarkSettled = {},
            onDeleteBookmark = {},
            onOpenArticle = {},
        )
    }
}
