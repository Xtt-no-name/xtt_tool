package com.xtt.toolbox.domain.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinkPolicyTest {
    @Test
    fun acceptsHttpAndHttpsWithHost() {
        assertEquals("https://example.com/path?q=1", ExternalLinkPolicy.validate("https://example.com/path?q=1"))
        assertEquals("HTTP://EXAMPLE.COM", ExternalLinkPolicy.validate("HTTP://EXAMPLE.COM"))
        assertEquals("http://example.com:8080/a", ExternalLinkPolicy.validate("http://example.com:8080/a"))
    }

    @Test
    fun rejectsMissingHostMalformedAndUnsupportedSchemes() {
        listOf(
            null,
            "",
            "http:///only-path",
            "https:example.com",
            "https://",
            "https://exa mple.com",
            "http://[",
            "javascript:alert(1)",
            "file:///sdcard/a",
            "content://provider/a",
            "intent://scan/#Intent;scheme=zxing;end",
            "data:text/html,<b>x</b>",
            "ftp://example.com/a",
            "javascript%3Aalert(1)",
            "https://example.com/\u0000",
        ).forEach { value ->
            assertNull("应拒绝：$value", ExternalLinkPolicy.validate(value))
            assertFalse(ExternalLinkPolicy.isAllowed(value))
        }
    }
}
