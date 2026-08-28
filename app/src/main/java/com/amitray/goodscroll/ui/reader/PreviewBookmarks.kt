package com.amitray.goodscroll.ui.reader

import com.amitray.goodscroll.data.local.Bookmark

/** Fake library used by the `@Preview` composables in this package. */
internal val previewBookmarks: List<Bookmark> = listOf(
    Bookmark(
        id = 1L,
        url = "https://www.theatlantic.com/technology/archive/attention-span",
        title = "What we lost when we stopped reading to the end",
        timestamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000L,
        excerpt = "Infinite feeds trained a generation to skim. The habit is reversible, but only " +
            "by rebuilding the muscle that finishes a thought: fewer things, read fully, one at " +
            "a time, with nothing else competing for the same minute.",
    ),
    Bookmark(
        id = 2L,
        url = "https://example.org/blog/slow-web",
        title = "The slow web is still a good idea",
        timestamp = System.currentTimeMillis() - 26 * 60 * 60 * 1000L,
        excerpt = "A short argument for software that waits for you rather than pulling at you, " +
            "and for reading queues that end.",
        isRead = true,
    ),
    Bookmark(
        id = 3L,
        url = "https://news.ycombinator.com/item?id=1",
        title = "news.ycombinator.com",
        timestamp = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L,
    ),
)
