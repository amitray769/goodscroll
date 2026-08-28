package com.amitray.goodscroll.ui.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amitray.goodscroll.MainActivity
import com.amitray.goodscroll.ui.theme.GoodScrollTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * The app's entry in the system share sheet.
 *
 * A separate, transparent activity rather than a mode of [MainActivity] on purpose: sharing a link
 * while scrolling should save it and hand the user straight back to whatever they were reading. If
 * the share target were the reader itself, every shared link would yank the user out of their feed
 * and into the app, which is the opposite of what an anti-doomscrolling tool should do.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: ShareViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The save is kicked off from the ViewModel rather than a composable so that it survives
        // configuration changes and cannot be restarted by recomposition.
        handleShare(intent)

        setContent {
            val status by viewModel.status.collectAsStateWithLifecycle()

            GoodScrollTheme {
                ShareConfirmation(
                    status = status,
                    onReadNow = { bookmarkId ->
                        startActivity(MainActivity.openBookmarkIntent(this, bookmarkId))
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }

            // Saves get out of the way on their own; a failure stays until acknowledged so the user
            // is never left believing a link was saved when it was not.
            if (status is ShareStatus.Saved || status is ShareStatus.AlreadySaved) {
                LaunchedEffect(status) {
                    delay(AUTO_DISMISS_MILLIS)
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        viewModel.onLinkShared(
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT),
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
        )
    }

    private companion object {
        /** Long enough to read the confirmation and reach for "Read now", short enough to feel instant. */
        const val AUTO_DISMISS_MILLIS = 2_200L
    }
}
