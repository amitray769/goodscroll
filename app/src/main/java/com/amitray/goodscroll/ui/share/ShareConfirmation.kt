package com.amitray.goodscroll.ui.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amitray.goodscroll.R
import com.amitray.goodscroll.ui.theme.GoodScrollTheme

/**
 * The card shown when a link is shared into the app.
 *
 * Deliberately small and short lived: the point of sharing to Good Scroll is to get the link out of
 * the way and carry on, so this confirms the save and gets out of the user's face rather than
 * dragging them into the reader.
 */
@Composable
fun ShareConfirmation(
    status: ShareStatus,
    onReadNow: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        var visible by remember { mutableStateOf(false) }
        androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut(),
        ) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    when (status) {
                        ShareStatus.Idle, ShareStatus.Saving -> SavingContent()

                        is ShareStatus.Saved -> OutcomeContent(
                            icon = Icons.Filled.Check,
                            title = stringResource(R.string.share_saved_title),
                            detail = status.title,
                            domain = status.domain,
                        )

                        is ShareStatus.AlreadySaved -> OutcomeContent(
                            icon = Icons.Filled.BookmarkAdded,
                            title = stringResource(R.string.share_already_saved_title),
                            detail = status.title,
                            domain = status.domain,
                        )

                        ShareStatus.NoLinkFound -> OutcomeContent(
                            icon = Icons.Filled.LinkOff,
                            title = stringResource(R.string.share_no_link_title),
                            detail = stringResource(R.string.share_no_link_body),
                            domain = null,
                        )
                    }

                    if (status.isFinished) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            val bookmarkId = status.bookmarkId
                            if (bookmarkId != null) {
                                TextButton(onClick = onDismiss) {
                                    Text(text = stringResource(R.string.share_done))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { onReadNow(bookmarkId) }) {
                                    Text(text = stringResource(R.string.share_read_now))
                                }
                            } else {
                                Button(onClick = onDismiss) {
                                    Text(text = stringResource(R.string.share_close))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavingContent(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.share_saving),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun OutcomeContent(
    icon: ImageVector,
    title: String,
    detail: String,
    domain: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (domain != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ShareConfirmationSavedPreview() {
    GoodScrollTheme(dynamicColor = false) {
        ShareConfirmation(
            status = ShareStatus.Saved(
                id = 1L,
                title = "The Art of Not Doomscrolling",
                domain = "example.com",
            ),
            onReadNow = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun ShareConfirmationSavingPreview() {
    GoodScrollTheme(dynamicColor = false) {
        ShareConfirmation(status = ShareStatus.Saving, onReadNow = {}, onDismiss = {})
    }
}

@Preview
@Composable
private fun ShareConfirmationNoLinkPreview() {
    GoodScrollTheme(dynamicColor = false) {
        ShareConfirmation(status = ShareStatus.NoLinkFound, onReadNow = {}, onDismiss = {})
    }
}
