package com.example

import com.example.util.BrowserCookie
import com.example.util.CookieFormat
import com.example.util.CookieTransferManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CookieTransferManagerTest {

    @Test
    fun `json serialize and parse round trip preserves metadata`() {
        val input = listOf(
            BrowserCookie(
                domain = ".google.com",
                name = "SID",
                value = "abc123token",
                path = "/",
                secure = true,
                httpOnly = true,
                expirationDate = 1893456000L,
                sameSite = "lax"
            ),
            BrowserCookie(
                domain = "accounts.google.com",
                name = "__Host-GAPS",
                value = "hostval",
                path = "/",
                secure = true,
                httpOnly = true,
                expirationDate = null,
                sameSite = null
            )
        )

        val serialized = CookieTransferManager.serializeCookies(input, CookieFormat.JSON)
        val preview = CookieTransferManager.parseCookies(serialized)

        assertNull(preview.error)
        assertEquals(CookieFormat.JSON, preview.detectedFormat)
        assertEquals(2, preview.domainCount)
        assertEquals(2, preview.cookies.size)

        val first = preview.cookies[0]
        assertEquals(".google.com", first.domain)
        assertEquals("SID", first.name)
        assertEquals("abc123token", first.value)
        assertTrue(first.secure)
        assertTrue(first.httpOnly)
        assertEquals(1893456000L, first.expirationDate)
        assertEquals("lax", first.sameSite)

        val second = preview.cookies[1]
        assertEquals("accounts.google.com", second.domain)
        assertEquals("__Host-GAPS", second.name)
        assertNull(second.expirationDate)
    }

    @Test
    fun `json domainCount counts distinct normalized domains`() {
        val raw = """
            [
              {"domain": ".google.com", "hostOnly": false, "name": "A", "value": "1"},
              {"domain": "google.com", "hostOnly": true, "name": "B", "value": "2"},
              {"domain": ".youtube.com", "hostOnly": false, "name": "C", "value": "3"}
            ]
        """.trimIndent()

        val preview = CookieTransferManager.parseCookies(raw)
        assertNull(preview.error)
        assertEquals(3, preview.cookies.size)
        assertEquals(2, preview.domainCount)
    }

    @Test
    fun `wrapped json object with cookies array parses cleanly`() {
        val raw = """
            {
              "cookies": [
                {"url": "https://dcsplayer.blogspot.com/watch", "name": "PREF", "value": "hd=1", "secure": true}
              ]
            }
        """.trimIndent()

        val preview = CookieTransferManager.parseCookies(raw)
        assertNull(preview.error)
        assertEquals(CookieFormat.JSON, preview.detectedFormat)
        assertEquals(1, preview.cookies.size)
        assertEquals("dcsplayer.blogspot.com", preview.cookies[0].domain)
        assertEquals("PREF", preview.cookies[0].name)
        assertEquals("hd=1", preview.cookies[0].value)
    }

    @Test
    fun `netscape cookies txt serialize and parse round trip handles httpOnly`() {
        val input = listOf(
            BrowserCookie(
                domain = ".google.com",
                name = "HSID",
                value = "xyz987",
                path = "/",
                secure = false,
                httpOnly = true,
                expirationDate = 1800000000L
            ),
            BrowserCookie(
                domain = "blogger.com",
                name = "VISITOR",
                value = "v1",
                path = "/app",
                secure = true,
                httpOnly = false,
                expirationDate = null
            )
        )

        val txt = CookieTransferManager.serializeCookies(input, CookieFormat.NETSCAPE)
        assertTrue(txt.contains("#HttpOnly_.google.com\tTRUE\t/\tFALSE\t1800000000\tHSID\txyz987"))
        assertTrue(txt.contains("blogger.com\tFALSE\t/app\tTRUE\t0\tVISITOR\tv1"))

        val preview = CookieTransferManager.parseCookies(txt)
        assertNull(preview.error)
        assertEquals(CookieFormat.NETSCAPE, preview.detectedFormat)
        assertEquals(2, preview.cookies.size)

        val hsid = preview.cookies[0]
        assertEquals(".google.com", hsid.domain)
        assertTrue(hsid.httpOnly)
        assertFalse(hsid.secure)
        assertEquals(1800000000L, hsid.expirationDate)

        val visitor = preview.cookies[1]
        assertEquals("blogger.com", visitor.domain)
        assertFalse(visitor.httpOnly)
        assertTrue(visitor.secure)
        assertEquals("/app", visitor.path)
        assertNull(visitor.expirationDate)
    }

    @Test
    fun `buildSetCookieHeader enforces hostOnly and __Host- and SameSite rules`() {
        val domainCookie = BrowserCookie(
            domain = ".google.com",
            name = "SID",
            value = "token",
            path = "/",
            secure = true,
            httpOnly = true,
            expirationDate = 1893456000L,
            sameSite = "no_restriction"
        )
        val domainHeader = CookieTransferManager.buildSetCookieHeader(domainCookie)
        assertTrue(domainHeader.contains("SID=token"))
        assertTrue(domainHeader.contains("; Domain=.google.com"))
        assertTrue(domainHeader.contains("; Secure"))
        assertTrue(domainHeader.contains("; HttpOnly"))
        assertTrue(domainHeader.contains("; SameSite=None"))
        assertTrue(domainHeader.contains("; Expires="))

        val hostPrefixCookie = BrowserCookie(
            domain = ".accounts.google.com",
            name = "__Host-1PSID",
            value = "secret",
            path = "/",
            secure = false,
            httpOnly = true,
            expirationDate = null,
            sameSite = null
        )
        val hostHeader = CookieTransferManager.buildSetCookieHeader(hostPrefixCookie)
        assertFalse(hostHeader.contains("Domain="))
        assertTrue(hostHeader.contains("; Secure"))
        assertTrue(hostHeader.contains("; HttpOnly"))
    }

    @Test
    fun `invalid cookie text returns helpful error`() {
        val badJson = CookieTransferManager.parseCookies("[{not valid json")
        assertNotNull(badJson.error)
        assertTrue(badJson.cookies.isEmpty())

        val badText = CookieTransferManager.parseCookies("just some random words")
        assertNotNull(badText.error)
        assertTrue(badText.cookies.isEmpty())

        val blank = CookieTransferManager.parseCookies("   ")
        assertNull(blank.error)
        assertTrue(blank.cookies.isEmpty())
    }
}
