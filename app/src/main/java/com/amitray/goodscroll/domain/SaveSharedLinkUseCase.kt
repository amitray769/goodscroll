package com.amitray.goodscroll.domain

import com.amitray.goodscroll.data.remote.MetadataFetcher
import com.amitray.goodscroll.data.repository.AddBookmarkResult
import com.amitray.goodscroll.data.repository.BookmarkRepository
import com.amitray.goodscroll.di.IoDispatcher
import com.amitray.goodscroll.util.SharedLinkParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** What happened to a link the user shared into the app. */
sealed interface SaveSharedLinkResult {

    data class Saved(val id: Long, val title: String, val url: String) : SaveSharedLinkResult

    /** The link was already in the library; [id] lets the reader jump straight to it. */
    data class AlreadySaved(val id: Long, val title: String, val url: String) : SaveSharedLinkResult

    /** Nothing that looks like a web link was in the shared text. */
    data object NoLinkFound : SaveSharedLinkResult
}

/**
 * Turns the raw contents of a share intent into a bookmark.
 *
 * Deliberately *save then enrich*: the row is written with a fallback title before any network call
 * happens, so sharing is instant, works offline and can never lose a link to a slow or failing
 * fetch. The scrape then runs in the background and fills in the real title, excerpt and image.
 *
 * The enrichment coroutine runs in an application scoped scope rather than `viewModelScope` because
 * the share confirmation closes after roughly a second, long before a page has been downloaded.
 * Moving this to a `@HiltWorker` later would additionally buy retry-when-online; it is kept out of
 * WorkManager for now so that wiring stays owned by one place.
 */
@Singleton
class SaveSharedLinkUseCase @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val metadataFetcher: MetadataFetcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val enrichmentScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    suspend operator fun invoke(sharedText: String?, subject: String? = null): SaveSharedLinkResult {
        val url = SharedLinkParser.extract(sharedText) ?: return SaveSharedLinkResult.NoLinkFound
        val fallbackTitle = fallbackTitle(url, subject, sharedText)

        return when (val result = bookmarkRepository.addBookmark(url, fallbackTitle)) {
            is AddBookmarkResult.Added -> {
                enrich(result.id, url, fallbackTitle)
                SaveSharedLinkResult.Saved(result.id, fallbackTitle, url)
            }

            is AddBookmarkResult.Duplicate -> SaveSharedLinkResult.AlreadySaved(
                id = result.id,
                title = bookmarkRepository.getBookmark(result.id)?.title ?: fallbackTitle,
                url = url,
            )
        }
    }

    private fun enrich(id: Long, url: String, fallbackTitle: String) {
        enrichmentScope.launch {
            val metadata = metadataFetcher.fetch(url) ?: return@launch
            bookmarkRepository.updateMetadata(
                id = id,
                title = metadata.title ?: fallbackTitle,
                excerpt = metadata.excerpt,
                imageUrl = metadata.imageUrl,
            )
        }
    }

    /**
     * The best title available without touching the network: the subject most apps attach, then any
     * text the user shared alongside the link, then the domain. Never blank, because the reading
     * card would otherwise show an empty headline until the scrape finishes.
     */
    private fun fallbackTitle(url: String, subject: String?, sharedText: String?): String {
        subject?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.take(MAX_TITLE_LENGTH) }

        val textWithoutUrls = sharedText.orEmpty()
            .replace(URL_LIKE, " ")
            .replace(WHITESPACE, " ")
            .trim()
        if (textWithoutUrls.length >= MIN_TITLE_LENGTH) return textWithoutUrls.take(MAX_TITLE_LENGTH)

        return SharedLinkParser.sourceDomain(url) ?: url
    }

    private companion object {
        const val MIN_TITLE_LENGTH = 3
        const val MAX_TITLE_LENGTH = 200

        val URL_LIKE = Regex("""\S+\.\S+""")
        val WHITESPACE = Regex("""\s+""")
    }
}
