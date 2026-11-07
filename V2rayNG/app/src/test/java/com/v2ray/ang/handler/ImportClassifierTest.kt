package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Base64

class ImportClassifierTest {
    // android.util.Base64 is unavailable in JVM unit tests; mirror Utils.decode's "empty on failure".
    private val decode: (String) -> String = {
        runCatching { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }.getOrDefault("")
    }

    private fun classify(text: String) = ImportClassifier.classify(text, decode)

    @Test
    fun classifiesInput() {
        assertEquals(ImportKind.Subscription("https://a.b/sub"), classify(" https://a.b/sub "))
        assertEquals(ImportKind.Config, classify("vless://id@h:443#n"))
        assertEquals(ImportKind.Routing, classify("[{\"outboundTag\":\"direct\",\"domain\":[\"a\"]}]"))
        assertEquals(ImportKind.Config, classify("{\"outbounds\":[]}"))
        assertEquals(ImportKind.Invalid, classify("привет, вот мой пароль"))
        assertEquals(ImportKind.Invalid, classify(""))
    }

    @Test
    fun base64OfLinksIsConfig() {
        val encoded = Base64.getEncoder().encodeToString("vless://id@h:443#n\ntrojan://p@h:1#m".toByteArray())
        assertEquals(ImportKind.Config, classify(encoded))
    }

    @Test
    fun arrayWithoutRoutingRulesIsConfig() {
        assertEquals(ImportKind.Config, classify("[{\"outbounds\":[]}]"))
        assertEquals(ImportKind.Invalid, classify("[not json"))
    }

    @Test
    fun deepLinkRejectsBadInput() {
        assertNull(ImportClassifier.parseDeepLink("install-sub", null))
        assertNull(ImportClassifier.parseDeepLink("install-sub", "javascript:alert(1)"))
        assertNull(ImportClassifier.parseDeepLink("other", "https://a.b"))
        assertEquals("https://a.b", ImportClassifier.parseDeepLink("install-config", "https://a.b")?.url)
    }

    @Test
    fun installSubAcceptsOnlyHttp() {
        assertEquals("http://a.b/s", ImportClassifier.parseDeepLink("install-sub", "http://a.b/s")?.url)
        assertNull(ImportClassifier.parseDeepLink("install-sub", "vless://id@h:443#n"))
        assertNull(ImportClassifier.parseDeepLink("install-sub", "ftp://a.b"))
    }

    @Test
    fun installConfigAcceptsProtocolLinksButNotScripts() {
        assertEquals(
            "vless://id@h:443#n",
            ImportClassifier.parseDeepLink("install-config", "vless://id@h:443#n")?.url
        )
        assertNull(ImportClassifier.parseDeepLink("install-config", "javascript:alert(1)"))
        assertNull(ImportClassifier.parseDeepLink("install-config", "  "))
    }
}
