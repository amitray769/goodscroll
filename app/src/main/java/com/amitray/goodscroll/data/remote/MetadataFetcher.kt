package com.amitray.goodscroll.data.remote

/** What a page tells us about itself; every field is optional because most pages are sloppy. */
data class LinkMetadata(
    val title: String? = null,
    val excerpt: String? = null,
    val imageUrl: String? = null,
) {
    val isEmpty: Boolean get() = title == null && excerpt == null && imageUrl == null
}

/**
 * Reads the title, summary and hero image of a shared page.
 *
 * An interface so the save path can be unit tested without a network, and so this can later be
 * swapped for a WorkManager backed implementation that retries when connectivity returns.
 */
interface MetadataFetcher {

    /** Returns the page's metadata, or null when it could not be read for any reason. */
    suspend fun fetch(url: String): LinkMetadata?
}
