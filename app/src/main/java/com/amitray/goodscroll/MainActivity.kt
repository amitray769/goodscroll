package com.amitray.goodscroll

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.amitray.goodscroll.ui.reader.ReaderRoute
import com.amitray.goodscroll.ui.theme.GoodScrollTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Hosts the reading experience.
 *
 * Launched three ways: from the launcher, from the share confirmation's "Read now", and from a
 * reminder notification. The last two attach a bookmark id so the pager opens on the right read,
 * which is why the intent is inspected in both [onCreate] and [onNewIntent] — the activity is
 * `singleTop`, so a warm process is handed the new intent rather than being recreated.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var focusedBookmarkId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        focusedBookmarkId = bookmarkIdFrom(intent)

        setContent {
            GoodScrollTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ReaderRoute(
                        focusedBookmarkId = focusedBookmarkId,
                        onFocusHandled = { focusedBookmarkId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bookmarkIdFrom(intent)?.let { focusedBookmarkId = it }
    }

    companion object {
        /** Distinguishes an "open this bookmark" launch from the launcher icon. */
        const val ACTION_OPEN_BOOKMARK = "com.amitray.goodscroll.action.OPEN_BOOKMARK"

        /** `Long` extra holding [com.amitray.goodscroll.data.local.Bookmark.id]. */
        const val EXTRA_BOOKMARK_ID = "com.amitray.goodscroll.extra.BOOKMARK_ID"

        private const val NO_BOOKMARK_ID = -1L

        fun openBookmarkIntent(context: Context, bookmarkId: Long): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_BOOKMARK
                putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
                // CLEAR_TOP keeps one reader task rather than stacking an activity per share.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        /** The bookmark [intent] wants opened, or null when it is an ordinary launch. */
        fun bookmarkIdFrom(intent: Intent?): Long? {
            if (intent?.action != ACTION_OPEN_BOOKMARK) return null
            return intent.getLongExtra(EXTRA_BOOKMARK_ID, NO_BOOKMARK_ID)
                .takeIf { it != NO_BOOKMARK_ID }
        }
    }
}
