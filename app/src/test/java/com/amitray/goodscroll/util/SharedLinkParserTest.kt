package com.amitray.goodscroll.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The share extra is the messiest input in the app, so the parser is tested far more heavily than
 * anything else: every case here was seen in the wild in some browser or social app.
 */
class SharedLinkParserTest {

    @Test
    fun `plain url is returned unchanged`() {
        assertEquals(
            "https://example.com/article",
            SharedLinkParser.extract("https://example.com/article"),
        )
    }

    @Test
    fun `url is pulled out of surrounding words`() {
        assertEquals(
            "https://example.com/article",
            SharedLinkParser.extract("Great read: https://example.com/article really worth it"),
        )
    }

    @Test
    fun `leading title and trailing newlines are ignored`() {
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("  The Headline\n\nhttps://example.com/a\n\n  "),
        )
    }

    @Test
    fun `first url wins when the text contains several`() {
        assertEquals(
            "https://example.com/first",
            SharedLinkParser.extract("https://example.com/first and https://other.com/second"),
        )
    }

    @Test
    fun `unsupported scheme is skipped in favour of a later web url`() {
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("ftp://files.example.com/x then https://example.com/a"),
        )
    }

    @Test
    fun `mailto and other non web links are rejected`() {
        assertNull(SharedLinkParser.extract("mailto:someone@example.com"))
        assertNull(SharedLinkParser.extract("ftp://files.example.com/x"))
        assertNull(SharedLinkParser.extract("javascript:alert(1)"))
    }

    @Test
    fun `non url text is rejected`() {
        assertNull(SharedLinkParser.extract("just some thoughts I had today"))
        assertNull(SharedLinkParser.extract(""))
        assertNull(SharedLinkParser.extract("   \n  "))
        assertNull(SharedLinkParser.extract(null))
    }

    @Test
    fun `filenames are not mistaken for domains`() {
        assertNull(SharedLinkParser.extract("please review README.md before merging"))
        assertNull(SharedLinkParser.extract("the crash is in config.json somewhere"))
    }

    @Test
    fun `scheme less domains resolve to https`() {
        assertEquals("https://example.com", SharedLinkParser.extract("example.com"))
        assertEquals("https://www.example.com/a", SharedLinkParser.extract("www.example.com/a"))
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("worth a look: example.com/a"),
        )
    }

    @Test
    fun `http is preserved rather than upgraded`() {
        assertEquals("http://example.com/a", SharedLinkParser.extract("http://example.com/a"))
    }

    @Test
    fun `tracking parameters are stripped`() {
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract(
                "https://example.com/a?utm_source=twitter&utm_medium=social&utm_campaign=x"
            ),
        )
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("https://example.com/a?fbclid=abc123"),
        )
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("https://example.com/a?gclid=abc&igshid=def&mc_eid=ghi"),
        )
    }

    @Test
    fun `meaningful parameters survive alongside tracking ones`() {
        assertEquals(
            "https://example.com/watch?v=abc123&t=42",
            SharedLinkParser.extract("https://example.com/watch?v=abc123&utm_source=share&t=42"),
        )
    }

    @Test
    fun `two shares of the same article normalise to one url`() {
        val fromTwitter = SharedLinkParser.extract(
            "Look at this https://Example.com/story/?utm_source=twitter#comments"
        )
        val fromBrowser = SharedLinkParser.extract("https://example.com/story")

        assertEquals("https://example.com/story", fromTwitter)
        assertEquals(fromBrowser, fromTwitter)
    }

    @Test
    fun `trailing slashes and plain fragments are dropped`() {
        assertEquals("https://example.com", SharedLinkParser.extract("https://example.com/"))
        assertEquals("https://example.com/a", SharedLinkParser.extract("https://example.com/a/"))
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("https://example.com/a#section-2"),
        )
    }

    @Test
    fun `single page app routes in the fragment are kept`() {
        assertEquals(
            "https://example.com/app#!/story/42",
            SharedLinkParser.extract("https://example.com/app#!/story/42"),
        )
        assertEquals(
            "https://example.com#/reader",
            SharedLinkParser.extract("https://example.com/#/reader"),
        )
    }

    @Test
    fun `sentence punctuation around the url is trimmed`() {
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("Read this (https://example.com/a)."),
        )
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("\"https://example.com/a\""),
        )
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("<https://example.com/a>"),
        )
    }

    @Test
    fun `balanced brackets inside the path are preserved`() {
        assertEquals(
            "https://en.example.org/wiki/Foo_(bar)",
            SharedLinkParser.extract("https://en.example.org/wiki/Foo_(bar)"),
        )
    }

    @Test
    fun `host is lower cased and default ports are dropped`() {
        assertEquals("https://example.com/a", SharedLinkParser.extract("HTTPS://Example.COM/a"))
        assertEquals("https://example.com/a", SharedLinkParser.extract("https://example.com:443/a"))
        assertEquals("http://example.com/a", SharedLinkParser.extract("http://example.com:80/a"))
        assertEquals(
            "https://example.com:8443/a",
            SharedLinkParser.extract("https://example.com:8443/a"),
        )
    }

    @Test
    fun `credentials in the authority are dropped`() {
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("https://user:secret@example.com/a"),
        )
    }

    @Test
    fun `malformed hosts are rejected`() {
        assertNull(SharedLinkParser.normalise("https://"))
        assertNull(SharedLinkParser.normalise("https://localhost/a"))
        assertNull(SharedLinkParser.normalise("https:// example.com"))
    }

    @Test
    fun `path case and encoding are preserved`() {
        assertEquals(
            "https://example.com/A/B%20C?q=Hello%20World",
            SharedLinkParser.extract("https://example.com/A/B%20C?q=Hello%20World"),
        )
    }

    @Test
    fun `empty query is removed entirely`() {
        assertEquals("https://example.com/a", SharedLinkParser.extract("https://example.com/a?"))
        assertEquals(
            "https://example.com/a",
            SharedLinkParser.extract("https://example.com/a?&utm_term=&"),
        )
    }

    @Test
    fun `source domain drops the www prefix`() {
        assertEquals("example.com", SharedLinkParser.sourceDomain("https://www.example.com/a?b=c"))
        assertEquals("news.example.co.uk", SharedLinkParser.sourceDomain("http://news.example.co.uk"))
        assertNull(SharedLinkParser.sourceDomain("not a url"))
    }
}
