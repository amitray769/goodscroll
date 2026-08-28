package com.amitray.goodscroll.ui.reader

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amitray.goodscroll.R
import com.amitray.goodscroll.data.local.Bookmark
import com.amitray.goodscroll.ui.theme.GoodScrollTheme
import com.amitray.goodscroll.util.SharedLinkParser

/**
 * One saved link, filling a single page of the reader.
 *
 * The body is the excerpt scraped when the link was shared rather than a `WebView`. A `WebView`
 * inside a `HorizontalPager` swallows horizontal drags, which breaks paging in exactly the gesture
 * this whole screen is built around; it also cannot render anything while offline. Showing stored
 * text keeps the page swipeable and readable on the tube, with an explicit "Open article" button
 * for the full page in the user's browser.
 */
@Composable
fun BookmarkPage(
    bookmark: Bookmark,
    position: String,
    onOpenArticle: (Bookmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            SourceLine(bookmark)

            Spacer(Modifier.height(16.dp))

            Text(
                text = bookmark.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            Text(
                text = bookmark.excerpt?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.reader_no_preview),
                style = MaterialTheme.typography.bodyLarge,
                color = if (bookmark.excerpt.isNullOrBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            Spacer(Modifier.height(28.dp))

            FilledTonalButton(onClick = { onOpenArticle(bookmark) }) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reader_open_article))
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = position,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SourceLine(bookmark: Bookmark, modifier: Modifier = Modifier) {
    val savedAt = remember(bookmark.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            bookmark.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = SharedLinkParser.sourceDomain(bookmark.url) ?: bookmark.url,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.reader_saved_prefix, savedAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (bookmark.isRead) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.reader_read_badge),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun BookmarkPagePreview() {
    GoodScrollTheme(dynamicColor = false) {
        BookmarkPage(
            bookmark = previewBookmarks.first(),
            position = "1 of 3",
            onOpenArticle = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun BookmarkPageWithoutExcerptPreview() {
    GoodScrollTheme(dynamicColor = false) {
        BookmarkPage(
            bookmark = previewBookmarks.last(),
            position = "3 of 3",
            onOpenArticle = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
