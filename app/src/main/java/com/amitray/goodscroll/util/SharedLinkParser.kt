package com.amitray.goodscroll.util

/**
 * Pulls a canonical url out of whatever another app puts in `Intent.EXTRA_TEXT`.
 *
 * Shared text is rarely a bare link: browsers and social apps send `"Some headline
 * https://example.com/x?utm_source=share"`, sometimes across several lines. Normalisation matters
 * because the bookmarks table has a unique index on the raw url string, so `?utm_source=twitter`
 * would otherwise save a second copy of a link the user already has.
 *
 * Deliberately pure Kotlin (no `android.net.Uri`) so it can be unit tested on the JVM, and hand
 * rolled rather than built on `java.net.URI` because that class rejects hosts and paths that real
 * shared links contain (underscores, unencoded characters).
 */
object SharedLinkParser {

    /** Matches a url that carries its own scheme, e.g. `https://example.com/a`. */
    private val SCHEME_URL = Regex("""[a-zA-Z][a-zA-Z0-9+.-]*://\S+""")

    /** Matches a scheme-less but unambiguous url, e.g. `www.example.com/a`. */
    private val WWW_URL = Regex("""(?<![\w.@-])www\.\S+""", RegexOption.IGNORE_CASE)

    /** A whole-string domain such as `example.com` or `example.co.uk/a?b=c`. */
    private val BARE_DOMAIN = Regex(
        """^[a-z0-9](?:[a-z0-9_-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9_-]*[a-z0-9])?)*""" +
            """\.[a-z]{2,}(?::\d{1,5})?(?:[/?#]\S*)?$""",
        RegexOption.IGNORE_CASE,
    )

    private val HOST = Regex("""^[a-z0-9](?:[a-z0-9_-]*[a-z0-9])?(?:\.[a-z0-9_-]+)*\.[a-z]{2,}$""")

    private val SUPPORTED_SCHEMES = setOf("http", "https")

    /**
     * Query parameters that only exist to attribute the click. Removing them is what makes
     * re-sharing the same article from two different places land on one bookmark.
     */
    private val TRACKING_PARAMETERS = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid",
        "igshid", "igsh", "mc_cid", "mc_eid", "mkt_tok", "vero_id", "oly_enc_id", "oly_anon_id",
        "_hsenc", "_hsmi", "hsctatracking", "ref_src", "ref_url", "s_kwcid", "trk", "trkcampaign",
    )

    private const val TRACKING_PREFIX = "utm_"

    /**
     * Top level domains common enough that `something.tld` inside a sentence is far more likely to
     * be a link than a filename. Keeps `README.md` and `config.json` from being saved as bookmarks.
     */
    private val COMMON_TLDS = setOf(
        "com", "org", "net", "edu", "gov", "int", "mil", "io", "co", "ai", "app", "dev", "me",
        "news", "blog", "info", "xyz", "uk", "us", "ca", "au", "de", "fr", "es", "it", "nl", "in",
        "jp", "cn", "br", "ru", "ch", "se", "no", "fi", "dk", "pl", "ie", "nz", "za", "eu",
    )

    /** Punctuation that usually belongs to the sentence around the link, not to the link. */
    private const val TRAILING_NOISE = ".,;:!?\"'“”‘’«»…"

    /**
     * Extracts and normalises the first usable link in [sharedText], or null when there is none.
     */
    fun extract(sharedText: String?): String? {
        if (sharedText == null) return null
        return candidates(sharedText).firstNotNullOfOrNull { candidate -> normalise(candidate) }
    }

    /**
     * Canonicalises [rawUrl]: forces a supported scheme, lower cases scheme and host, drops
     * credentials, default ports, tracking parameters, empty queries, trailing slashes and
     * fragments that are not part of a client side route. Returns null when the input is not a web
     * link we can save.
     */
    fun normalise(rawUrl: String): String? {
        val trimmed = trimNoise(rawUrl)
        if (trimmed.isEmpty()) return null

        val separator = trimmed.indexOf("://")
        val scheme: String
        val remainder: String
        if (separator >= 0) {
            scheme = trimmed.substring(0, separator).lowercase()
            remainder = trimmed.substring(separator + 3)
        } else {
            // Scheme-less input such as `example.com/a`: https is the safe modern default.
            if (trimmed.contains(':') && !trimmed.substringBefore(':').contains('.')) return null
            scheme = "https"
            remainder = trimmed
        }
        if (scheme !in SUPPORTED_SCHEMES) return null

        val authorityEnd = remainder.indexOfFirst { it in "/?#" }
            .let { if (it == -1) remainder.length else it }
        val authority = remainder.substring(0, authorityEnd)
        val rest = remainder.substring(authorityEnd)

        // Credentials in a shared link are either a phishing attempt or an accident; drop them.
        val hostAndPort = authority.substringAfterLast('@')
        val host = hostAndPort.substringBefore(':').lowercase().trimEnd('.')
        if (!HOST.matches(host)) return null

        val port = hostAndPort.substringAfter(':', missingDelimiterValue = "")
            .takeIf { it.isNotEmpty() && it != defaultPort(scheme) }
        if (port != null && port.toIntOrNull()?.takeIf { it in 1..65535 } == null) return null

        val fragment = rest.substringAfter('#', missingDelimiterValue = "")
        val beforeFragment = rest.substringBefore('#')
        val path = normalisePath(beforeFragment.substringBefore('?'))
        val query = normaliseQuery(beforeFragment.substringAfter('?', missingDelimiterValue = ""))

        return buildString {
            append(scheme).append("://").append(host)
            if (port != null) append(':').append(port)
            append(path)
            if (query.isNotEmpty()) append('?').append(query)
            // A `#!`/`#/` fragment is a route on single page sites, so dropping it loses the page.
            if (fragment.startsWith("!") || fragment.startsWith("/")) append('#').append(fragment)
        }
    }

    /** The bare domain shown on the reading card, e.g. `theguardian.com`. */
    fun sourceDomain(url: String): String? {
        val host = url.substringAfter("://", missingDelimiterValue = url)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
        return host.removePrefix("www.").takeIf { it.isNotEmpty() && HOST.matches(host) }
    }

    /**
     * Every substring of [sharedText] that could be a link, best first. Explicit schemes win over
     * `www.` prefixes, which win over bare domains, so a caption like `example.com is down, see
     * https://status.example.com` still bookmarks the page the user meant.
     */
    private fun candidates(sharedText: String): Sequence<String> = sequence {
        yieldAll(SCHEME_URL.findAll(sharedText).map { it.value })
        yieldAll(WWW_URL.findAll(sharedText).map { it.value })
        // Without a scheme or `www.` prefix, `report.md` looks exactly like a domain, so a bare
        // domain inside a sentence is only trusted when its suffix is a well known tld.
        yieldAll(
            sharedText.splitToSequence(' ', '\n', '\r', '\t')
                .map(::trimNoise)
                .filter { BARE_DOMAIN.matches(it) && hasCommonTld(it) }
        )
        // The user may have shared nothing but the domain, in which case any tld is believable.
        yield(trimNoise(sharedText))
    }.filter { it.isNotEmpty() }

    private fun hasCommonTld(candidate: String): Boolean = candidate
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringBefore(':')
        .substringAfterLast('.')
        .lowercase() in COMMON_TLDS

    private fun trimNoise(value: String): String {
        var result = value.trim().trim('\u200b')
        while (result.isNotEmpty()) {
            val last = result.last()
            val unbalanced = (last == ')' && !result.contains('(')) ||
                (last == ']' && !result.contains('[')) ||
                (last == '}' && !result.contains('{')) ||
                (last == '>' && !result.contains('<'))
            if (last in TRAILING_NOISE || unbalanced) {
                result = result.dropLast(1)
            } else {
                break
            }
        }
        return result.removePrefix("<").trim()
    }

    private fun defaultPort(scheme: String) = if (scheme == "https") "443" else "80"

    private fun normalisePath(path: String): String {
        val withoutTrailingSlashes = path.trimEnd('/')
        return if (withoutTrailingSlashes == "/") "" else withoutTrailingSlashes
    }

    private fun normaliseQuery(query: String): String = query
        .split('&')
        .filter { parameter ->
            if (parameter.isEmpty()) return@filter false
            val name = parameter.substringBefore('=').lowercase()
            name.isNotEmpty() &&
                !name.startsWith(TRACKING_PREFIX) &&
                name !in TRACKING_PARAMETERS
        }
        .joinToString("&")
}
