package com.amitray.goodscroll.data.remote

import com.amitray.goodscroll.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Scrapes Open Graph tags with jsoup, falling back to the classic `<title>` and description tags.
 *
 * Everything here is best effort: the bookmark is already saved by the time this runs, so any
 * failure (offline, timeout, paywall, html that is really a pdf) simply leaves the fallback title
 * in place instead of surfacing an error.
 */
@Singleton
class JsoupMetadataFetcher @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MetadataFetcher {

    override suspend fun fetch(url: String): LinkMetadata? = withContext(ioDispatcher) {
        val document = try {
            Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .maxBodySize(MAX_BODY_BYTES)
                .followRedirects(true)
                .ignoreContentType(false)
                .get()
        } catch (error: Exception) {
            // jsoup throws IOException for network problems but also unchecked errors for odd
            // content types, and losing metadata must never be louder than a debug log.
            return@withContext null
        }

        LinkMetadata(
            title = document.firstNonBlank(
                "meta[property=og:title]" to CONTENT,
                "meta[name=twitter:title]" to CONTENT,
            ) ?: document.title().cleaned(),
            excerpt = document.firstNonBlank(
                "meta[property=og:description]" to CONTENT,
                "meta[name=description]" to CONTENT,
                "meta[name=twitter:description]" to CONTENT,
            ) ?: document.firstParagraphs(),
            imageUrl = document.firstNonBlank(
                "meta[property=og:image]" to ABSOLUTE_CONTENT,
                "meta[name=twitter:image]" to ABSOLUTE_CONTENT,
            ),
        ).takeUnless { it.isEmpty }
    }

    private fun Document.firstNonBlank(vararg selectors: Pair<String, String>): String? = selectors
        .firstNotNullOfOrNull { (selector, attribute) ->
            selectFirst(selector)?.attr(attribute)?.cleaned()
        }

    /** A readable stand-in for pages that ship no description at all. */
    private fun Document.firstParagraphs(): String? = select("article p, main p, p")
        .asSequence()
        .map { it.text().trim() }
        .filter { it.length >= MIN_PARAGRAPH_LENGTH }
        .take(MAX_PARAGRAPHS)
        .joinToString("\n\n")
        .cleaned()

    private fun String?.cleaned(): String? = this
        ?.replace(WHITESPACE, " ")
        ?.trim()
        ?.take(MAX_EXCERPT_LENGTH)
        ?.takeIf { it.isNotEmpty() }

    private companion object {
        /**
         * A real browser string: several publishers serve an error page or a consent wall to
         * anything that identifies itself as a scraper.
         */
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36 GoodScroll/1.0"

        const val TIMEOUT_MILLIS = 10_000
        const val MAX_BODY_BYTES = 1024 * 1024
        const val MAX_EXCERPT_LENGTH = 600
        const val MIN_PARAGRAPH_LENGTH = 80
        const val MAX_PARAGRAPHS = 3

        const val CONTENT = "content"

        /** Resolves relative `og:image` values against the page url. */
        const val ABSOLUTE_CONTENT = "abs:content"

        val WHITESPACE = Regex("""\s+""")
    }
}
