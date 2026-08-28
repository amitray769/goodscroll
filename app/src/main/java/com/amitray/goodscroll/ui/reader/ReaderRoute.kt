package com.amitray.goodscroll.ui.reader

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amitray.goodscroll.R
import com.amitray.goodscroll.data.local.Bookmark
import com.amitray.goodscroll.ui.reminder.ReminderPickerDialog
import com.amitray.goodscroll.ui.reminder.rememberNotificationPermissionRequester
import kotlinx.coroutines.launch

/**
 * Supplies [ReaderScreen] with real state. Split from the screen so the screen stays previewable and
 * testable without Hilt or a database.
 *
 * @param focusedBookmarkId the bookmark to scroll to on arrival, set when the user has just shared a
 *   link or tapped a reminder notification.
 */
@Composable
fun ReaderRoute(
    modifier: Modifier = Modifier,
    focusedBookmarkId: Long? = null,
    onFocusHandled: () -> Unit = {},
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermission = rememberNotificationPermissionRequester()

    // Held by id, not by value, so the dialog always shows the current deadline and closes by itself
    // if the bookmark is deleted from under it.
    var reminderTargetId by remember { mutableStateOf<Long?>(null) }
    val reminderTarget = uiState.bookmarks.firstOrNull { it.id == reminderTargetId }

    val addedMessage = stringResource(R.string.snackbar_bookmark_added)
    val alreadySavedMessage = stringResource(R.string.snackbar_bookmark_already_saved)
    val deletedMessage = stringResource(R.string.snackbar_bookmark_deleted)
    val reminderSetMessage = stringResource(R.string.snackbar_reminder_set)
    val reminderClearedMessage = stringResource(R.string.snackbar_reminder_cleared)
    val cannotOpenMessage = stringResource(R.string.snackbar_cannot_open_article)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is ReaderEvent.BookmarkAdded -> addedMessage
                is ReaderEvent.BookmarkAlreadySaved -> alreadySavedMessage
                ReaderEvent.BookmarkDeleted -> deletedMessage
                is ReaderEvent.ReminderSet -> reminderSetMessage
                ReaderEvent.ReminderCleared -> reminderClearedMessage
            }
            // Replace rather than queue: these are status confirmations, and a user deleting several
            // reads in a row should not have to wait out a backlog of stale snackbars.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    ReaderScreen(
        uiState = uiState,
        onSortOrderChanged = viewModel::onSortOrderChanged,
        onBookmarkSettled = { viewModel.markAsRead(it.id) },
        onDeleteBookmark = viewModel::deleteBookmark,
        onOpenArticle = { bookmark ->
            if (!context.openInBrowser(bookmark)) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(cannotOpenMessage)
                }
            }
        },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        focusedBookmarkId = focusedBookmarkId,
        onFocusHandled = onFocusHandled,
        onSetReminderClick = { reminderTargetId = it.id },
    )

    reminderTarget?.let { bookmark ->
        ReminderPickerDialog(
            initialDeadline = bookmark.reminderDeadline,
            onConfirm = { deadline ->
                reminderTargetId = null
                viewModel.setReminder(bookmark.id, deadline)
                // Asked for here rather than at startup: the prompt only makes sense once the user
                // has actually asked to be reminded of something.
                notificationPermission.requestIfNeeded()
            },
            onClear = {
                reminderTargetId = null
                viewModel.clearReminder(bookmark.id)
            },
            onDismiss = { reminderTargetId = null },
        )
    }
}

private fun Context.openInBrowser(bookmark: Bookmark): Boolean =
    try {
        startActivity(Intent(Intent.ACTION_VIEW, bookmark.url.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
