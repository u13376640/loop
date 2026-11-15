package com.example.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

enum class CookieFormat(
    val label: String,
    val extension: String,
    val mimeType: String
) {
    JSON("JSON", "json", "application/json"),
    NETSCAPE("cookies.txt", "txt", "text/plain")
}

enum class CookieImportMode {
    MERGE,
    REPLACE
}

data class BrowserCookie(
    val domain: String,
    val name: String,
    val value: String,
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expirationDate: Long? = null,
    val sameSite: String? = null
)

data class CookieExportResult(
    val cookies: List<BrowserCookie>,
    val domainCount: Int,
    val serializedText: String
)

data class CookieImportPreview(
    val cookies: List<BrowserCookie>,
    val detectedFormat: CookieFormat?,
    val domainCount: Int,
    val error: String? = null
)

object CookieTransferManager {

    // Core domains commonly involved in Google / Blogger / WebView sessions
    private val DEFAULT_DISCOVERY_DOMAINS = listOf(
        "google.com",
        ".google.com",
        "accounts.google.com",
        "myaccount.google.com",
        "youtube.com",
        ".youtube.com",
        "blogger.com",
        ".blogger.com",
        "blogspot.com",
        ".blogspot.com"
    )

    // Difference in seconds between Windows epoch (1601-01-01) and Unix epoch (1970-01-01)
    private const val CHROMIUM_EPOCH_OFFSET_SECONDS = 11644473600L

    private data class CookieKey(
        val domain: String,
        val path: String,
        val name: String
    )

    suspend fun exportCookies(
        context: Context,
        format: CookieFormat,
        trackedUrls: Collection<String>
    ): CookieExportResult = withContext(Dispatchers.IO) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        val cookieMap = LinkedHashMap<CookieKey, BrowserCookie>()

        // 1. Read rich cookie metadata from Chromium's WebView Cookies SQLite store
        val dbCookies = readWebViewCookieDatabase(context)
        for (cookie in dbCookies) {
            val key = CookieKey(cookie.domain.lowercase(Locale.ROOT), cookie.path, cookie.name)
            cookieMap[key] = cookie
        }

        // 2. Build candidate hosts from SQLite domains + tracked visited URLs + core Google/Blogger hosts
        val candidateHosts = LinkedHashSet<String>()
        for (cookie in dbCookies) {
            val clean = cookie.domain.trimStart('.')
            if (clean.isNotBlank()) candidateHosts.add(clean)
        }
        for (url in trackedUrls) {
            extractHost(url)?.let { host ->
                candidateHosts.add(host)
                extractRootDomain(host)?.let { root -> candidateHosts.add(root) }
            }
        }
        for (domain in DEFAULT_DISCOVERY_DOMAINS) {
            candidateHosts.add(domain.trimStart('.'))
        }

        // 3. Query live CookieManager values for every candidate host
        // (fills in values if DB uses encrypted_value or wasn't readable)
        for (host in candidateHosts) {
            val header = try {
                cookieManager.getCookie("https://$host/")
            } catch (_: Exception) {
                null
            } ?: continue

            val pairs = parseCookieHeaderPairs(header)
            for ((name, value) in pairs) {
                val matchingKey = cookieMap.keys.firstOrNull { key ->
                    key.name == name && hostMatchesDomain(host, key.domain)
                }
                if (matchingKey != null) {
                    val existing = cookieMap[matchingKey]
                    if (existing != null && existing.value.isEmpty() && value.isNotEmpty()) {
                        cookieMap[matchingKey] = existing.copy(value = value)
                    }
                } else {
                    val key = CookieKey(host.lowercase(Locale.ROOT), "/", name)
                    if (!cookieMap.containsKey(key)) {
                        cookieMap[key] = BrowserCookie(
                            domain = host.lowercase(Locale.ROOT),
                            name = name,
                            value = value,
                            path = "/",
                            secure = true,
                            httpOnly = false,
                            expirationDate = null,
                            sameSite = null
                        )
                    }
                }
            }
        }

        val allCookies = cookieMap.values
            .filter { it.name.isNotBlank() && it.domain.isNotBlank() }
            .sortedWith(compareBy({ it.domain.trimStart('.') }, { it.name }))

        val domainCount = allCookies.map { it.domain.trimStart('.').lowercase(Locale.ROOT) }.toSet().size
        val serialized = serializeCookies(allCookies, format)

        CookieExportResult(
            cookies = allCookies,
            domainCount = domainCount,
            serializedText = serialized
        )
    }

    fun serializeCookies(cookies: List<BrowserCookie>, format: CookieFormat): String {
        return when (format) {
            CookieFormat.JSON -> serializeAsJson(cookies)
            CookieFormat.NETSCAPE -> serializeAsNetscape(cookies)
        }
    }

    private fun serializeAsJson(cookies: List<BrowserCookie>): String {
        val array = JSONArray()
        for (cookie in cookies) {
            val obj = JSONObject().apply {
                put("domain", cookie.domain)
                put("hostOnly", !cookie.domain.startsWith("."))
                put("httpOnly", cookie.httpOnly)
                put("name", cookie.name)
                put("path", cookie.path.ifBlank { "/" })
                put("sameSite", cookie.sameSite ?: "unspecified")
                put("secure", cookie.secure)
                val isSession = cookie.expirationDate == null || cookie.expirationDate <= 0L
                put("session", isSession)
                if (!isSession) {
                    put("expirationDate", cookie.expirationDate)
                }
                put("value", cookie.value)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    private fun serializeAsNetscape(cookies: List<BrowserCookie>): String {
        val sb = StringBuilder()
        sb.appendLine("# Netscape HTTP Cookie File")
        sb.appendLine("# https://curl.haxx.se/docs/http-cookies.html")
        sb.appendLine("# Exported by Loop")
        sb.appendLine()
        for (cookie in cookies) {
            val domainPrefix = if (cookie.httpOnly) "#HttpOnly_" else ""
            val domainField = "$domainPrefix${cookie.domain}"
            val includeSubdomains = if (cookie.domain.startsWith(".")) "TRUE" else "FALSE"
            val path = cookie.path.ifBlank { "/" }
            val secure = if (cookie.secure) "TRUE" else "FALSE"
            val expiry = (cookie.expirationDate ?: 0L).coerceAtLeast(0L).toString()
            val cleanName = cookie.name.replace("\t", " ").replace("\n", "")
            val cleanValue = cookie.value.replace("\t", " ").replace("\n", "")
            sb.appendLine("$domainField\t$includeSubdomains\t$path\t$secure\t$expiry\t$cleanName\t$cleanValue")
        }
        return sb.toString()
    }

    fun parseCookies(rawText: String): CookieImportPreview {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return CookieImportPreview(
                cookies = emptyList(),
                detectedFormat = null,
                domainCount = 0,
                error = null
            )
        }

        return if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            parseJsonCookies(trimmed)
        } else {
            parseNetscapeCookies(trimmed)
        }
    }

    private fun parseJsonCookies(rawJson: String): CookieImportPreview {
        return try {
            val array = if (rawJson.startsWith("[")) {
                JSONArray(rawJson)
            } else {
                val root = JSONObject(rawJson)
                when {
                    root.has("cookies") -> root.getJSONArray("cookies")
                    root.has("data") -> root.getJSONArray("data")
                    else -> return CookieImportPreview(
                        cookies = emptyList(),
                        detectedFormat = CookieFormat.JSON,
                        domainCount = 0,
                        error = "JSON object must contain a 'cookies' array."
                    )
                }
            }

            val parsed = ArrayList<BrowserCookie>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue

                var domain = obj.optString("domain", "").trim()
                if (domain.isEmpty()) {
                    val urlField = obj.optString("url", "")
                    domain = extractHost(urlField).orEmpty()
                }
                if (domain.isEmpty()) continue

                val value = obj.optString("value", "")
                val path = obj.optString("path", "/").ifBlank { "/" }
                val secure = obj.optBoolean("secure", false)
                val httpOnly = obj.optBoolean("httpOnly", false)
                val hostOnly = if (obj.has("hostOnly")) obj.optBoolean("hostOnly", false) else !domain.startsWith(".")

                val normalizedDomain = if (hostOnly) domain.trimStart('.') else {
                    if (domain.startsWith(".")) domain else ".$domain"
                }

                val expirationDate: Long? = when {
                    obj.has("expirationDate") && !obj.isNull("expirationDate") -> {
                        val exp = obj.optDouble("expirationDate", 0.0).toLong()
                        if (exp > 0L) exp else null
                    }
                    obj.has("expires") && !obj.isNull("expires") -> {
                        val exp = obj.optDouble("expires", 0.0).toLong()
                        if (exp > 0L) exp else null
                    }
                    else -> null
                }

                val sameSite = obj.optString("sameSite", "").takeIf {
                    it.isNotBlank() && it != "unspecified" && it != "null"
                }

                parsed.add(
                    BrowserCookie(
                        domain = normalizedDomain,
                        name = name,
                        value = value,
                        path = path,
                        secure = secure,
                        httpOnly = httpOnly,
                        expirationDate = expirationDate,
                        sameSite = sameSite
                    )
                )
            }

            if (parsed.isEmpty()) {
                CookieImportPreview(
                    cookies = emptyList(),
                    detectedFormat = CookieFormat.JSON,
                    domainCount = 0,
                    error = "No valid cookies found in JSON payload."
                )
            } else {
                val domains = parsed.map { it.domain.trimStart('.').lowercase(Locale.ROOT) }.toSet().size
                CookieImportPreview(
                    cookies = parsed,
                    detectedFormat = CookieFormat.JSON,
                    domainCount = domains
                )
            }
        } catch (e: Exception) {
            CookieImportPreview(
                cookies = emptyList(),
                detectedFormat = CookieFormat.JSON,
                domainCount = 0,
                error = "Invalid JSON format: ${e.localizedMessage ?: "Parse error"}"
            )
        }
    }

    private fun parseNetscapeCookies(rawText: String): CookieImportPreview {
        val lines = rawText.split(Regex("\\r?\\n"))
        val parsed = ArrayList<BrowserCookie>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            var isHttpOnly = false
            val effectiveLine = when {
                line.startsWith("#HttpOnly_", ignoreCase = true) -> {
                    isHttpOnly = true
                    line.substring("#HttpOnly_".length)
                }
                line.startsWith("#") -> continue
                else -> line
            }

            val parts = effectiveLine.split("\t")
            if (parts.size < 6) continue

            val domain = parts[0].trim()
            val includeSubdomains = parts[1].trim().equals("TRUE", ignoreCase = true)
            val path = parts[2].trim().ifBlank { "/" }
            val secure = parts[3].trim().equals("TRUE", ignoreCase = true)
            val expiry = parts[4].trim().toLongOrNull()?.takeIf { it > 0L }
            val name = parts[5].trim()
            val value = if (parts.size >= 7) parts[6] else ""

            if (domain.isEmpty() || name.isEmpty()) continue

            val normalizedDomain = if (includeSubdomains && !domain.startsWith(".")) {
                ".$domain"
            } else {
                domain
            }

            parsed.add(
                BrowserCookie(
                    domain = normalizedDomain,
                    name = name,
                    value = value,
                    path = path,
                    secure = secure,
                    httpOnly = isHttpOnly,
                    expirationDate = expiry,
                    sameSite = null
                )
            )
        }

        return if (parsed.isEmpty()) {
            CookieImportPreview(
                cookies = emptyList(),
                detectedFormat = null,
                domainCount = 0,
                error = "Unrecognized cookie data. Paste valid JSON or Netscape cookies.txt text."
            )
        } else {
            val domains = parsed.map { it.domain.trimStart('.').lowercase(Locale.ROOT) }.toSet().size
            CookieImportPreview(
                cookies = parsed,
                detectedFormat = CookieFormat.NETSCAPE,
                domainCount = domains
            )
        }
    }

    suspend fun importCookies(
        cookies: List<BrowserCookie>,
        mode: CookieImportMode
    ): Int = withContext(Dispatchers.Main) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        if (mode == CookieImportMode.REPLACE) {
            suspendCancellableCoroutine { cont ->
                cookieManager.removeAllCookies {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            cookieManager.flush()
        }

        var importedCount = 0
        for (cookie in cookies) {
            val cleanHost = cookie.domain.trimStart('.')
            if (cleanHost.isEmpty() || cookie.name.isEmpty()) continue

            val targetPath = cookie.path.ifBlank { "/" }
            val targetUrl = "https://$cleanHost$targetPath"
            val setCookieHeader = buildSetCookieHeader(cookie)

            cookieManager.setCookie(targetUrl, setCookieHeader)
            importedCount++
        }

        cookieManager.flush()
        importedCount
    }

    fun buildSetCookieHeader(cookie: BrowserCookie): String {
        val sb = StringBuilder()
        sb.append("${cookie.name}=${cookie.value}")

        val targetPath = cookie.path.ifBlank { "/" }
        sb.append("; Path=$targetPath")

        // Include Domain attribute only for domain-scoped cookies (leading dot),
        // preserving host-only cookies and complying with __Host- prefix rules.
        val isHostPrefix = cookie.name.startsWith("__Host-")
        if (!isHostPrefix && cookie.domain.startsWith(".")) {
            sb.append("; Domain=${cookie.domain}")
        }

        if (cookie.expirationDate != null && cookie.expirationDate > 0L) {
            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val expiresStr = dateFormat.format(Date(cookie.expirationDate * 1000L))
            sb.append("; Expires=$expiresStr")
        }

        val requiresSecure = cookie.secure ||
            cookie.name.startsWith("__Secure-") ||
            cookie.name.startsWith("__Host-") ||
            cookie.sameSite.equals("no_restriction", ignoreCase = true) ||
            cookie.sameSite.equals("none", ignoreCase = true)

        if (requiresSecure) {
            sb.append("; Secure")
        }

        if (cookie.httpOnly) {
            sb.append("; HttpOnly")
        }

        when (cookie.sameSite?.lowercase(Locale.ROOT)) {
            "no_restriction", "none" -> sb.append("; SameSite=None")
            "lax" -> sb.append("; SameSite=Lax")
            "strict" -> sb.append("; SameSite=Strict")
        }

        return sb.toString()
    }

    private fun readWebViewCookieDatabase(context: Context): List<BrowserCookie> {
        val dataDir = context.applicationInfo.dataDir?.let { File(it) } ?: context.filesDir.parentFile
        val candidates = listOf(
            File(dataDir, "app_webview/Default/Cookies"),
            File(dataDir, "app_webview/Cookies")
        )
        val dbFile = candidates.firstOrNull { it.exists() && it.canRead() } ?: return emptyList()

        val result = ArrayList<BrowserCookie>()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery(
                "SELECT host_key, name, value, path, expires_utc, is_secure, is_httponly, samesite FROM cookies",
                null
            )
            cursor.use { c ->
                val idxHost = c.getColumnIndex("host_key")
                val idxName = c.getColumnIndex("name")
                val idxValue = c.getColumnIndex("value")
                val idxPath = c.getColumnIndex("path")
                val idxExpires = c.getColumnIndex("expires_utc")
                val idxSecure = c.getColumnIndex("is_secure")
                val idxHttpOnly = c.getColumnIndex("is_httponly")
                val idxSameSite = c.getColumnIndex("samesite")

                while (c.moveToNext()) {
                    val host = if (idxHost >= 0) c.getString(idxHost).orEmpty() else ""
                    val name = if (idxName >= 0) c.getString(idxName).orEmpty() else ""
                    if (host.isBlank() || name.isBlank()) continue

                    val value = if (idxValue >= 0) c.getString(idxValue).orEmpty() else ""
                    val path = if (idxPath >= 0) c.getString(idxPath).orEmpty().ifBlank { "/" } else "/"
                    val expiresUtc = if (idxExpires >= 0) c.getLong(idxExpires) else 0L
                    val unixExpiry = if (expiresUtc > 0L) {
                        val seconds = (expiresUtc / 1_000_000L) - CHROMIUM_EPOCH_OFFSET_SECONDS
                        if (seconds > 0L) seconds else null
                    } else {
                        null
                    }
                    val secure = idxSecure >= 0 && c.getInt(idxSecure) != 0
                    val httpOnly = idxHttpOnly >= 0 && c.getInt(idxHttpOnly) != 0
                    val sameSite = if (idxSameSite >= 0) {
                        when (c.getInt(idxSameSite)) {
                            0 -> "no_restriction"
                            1 -> "lax"
                            2 -> "strict"
                            else -> null
                        }
                    } else null

                    result.add(
                        BrowserCookie(
                            domain = host,
                            name = name,
                            value = value,
                            path = path,
                            secure = secure,
                            httpOnly = httpOnly,
                            expirationDate = unixExpiry,
                            sameSite = sameSite
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Fall back cleanly to CookieManager-only extraction
        } finally {
            try {
                db?.close()
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun parseCookieHeaderPairs(header: String): List<Pair<String, String>> {
        if (header.isBlank()) return emptyList()
        return header.split(";").mapNotNull { part ->
            val trimmed = part.trim()
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex <= 0) return@mapNotNull null
            val name = trimmed.substring(0, eqIndex).trim()
            val value = trimmed.substring(eqIndex + 1).trim()
            if (name.isEmpty()) null else name to value
        }
    }

    fun extractHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            URI(url).host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractRootDomain(host: String): String? {
        val parts = host.split(".")
        return if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            null
        }
    }

    private fun hostMatchesDomain(host: String, domain: String): Boolean {
        val cleanHost = host.lowercase(Locale.ROOT)
        val cleanDomain = domain.trimStart('.').lowercase(Locale.ROOT)
        return cleanHost == cleanDomain || cleanHost.endsWith(".$cleanDomain")
    }
}
