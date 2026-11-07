package com.v2ray.ang.handler

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.Utils
import java.net.URI

/** What a piece of imported text is. */
sealed interface ImportKind {
    data class Subscription(val url: String) : ImportKind
    data object Config : ImportKind
    data object Routing : ImportKind
    data object Invalid : ImportKind
}

/** A validated address taken from a deep link. */
data class DeepLinkRequest(val url: String)

/** Pure classification of imported input and deep links; no storage, no Android types. */
object ImportClassifier {
    private const val HOST_INSTALL_SUB = "install-sub"
    private const val HOST_INSTALL_CONFIG = "install-config"

    // Link schemes importBatchConfig parses (AngConfigManager.configFmtParsers plus v2rayn://).
    // hysteria:// is deliberately absent: parseConfig does not handle it.
    private val configSchemes: List<String> = listOf(
        AppConfig.VMESS, AppConfig.SHADOWSOCKS, AppConfig.SOCKS, AppConfig.SOCKS4, AppConfig.SOCKS5,
        AppConfig.TROJAN, AppConfig.VLESS, AppConfig.WIREGUARD, AppConfig.HYSTERIA2, AppConfig.HY2,
        AppConfig.V2RAYNFMTS,
    )

    /**
     * @param decode base64 decoder returning an empty string on failure; injectable because
     * the default one needs the Android framework.
     */
    fun classify(text: String, decode: (String) -> String = Utils::decode): ImportKind {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ImportKind.Invalid

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return classifyJson(trimmed)

        classifyLines(trimmed)?.let { return it }

        // Same order as importBatchConfig: a base64 payload holding links or URLs.
        val decoded = decode(trimmed).trim()
        if (decoded.isNotEmpty()) classifyLines(decoded)?.let { return it }
        return ImportKind.Invalid
    }

    /**
     * install-sub accepts only http(s); install-config accepts http(s) and protocol links.
     * Anything else (including javascript:) is rejected.
     */
    fun parseDeepLink(host: String?, urlParam: String?): DeepLinkRequest? {
        val url = urlParam?.trim()
        if (url.isNullOrEmpty()) return null
        val accepted = when (host) {
            HOST_INSTALL_SUB -> isHttpUrl(url)
            HOST_INSTALL_CONFIG -> isHttpUrl(url) || hasConfigLink(url)
            else -> false
        }
        return if (accepted) DeepLinkRequest(url) else null
    }

    private fun isConfigLink(line: String): Boolean =
        configSchemes.any { line.startsWith(it, ignoreCase = it == AppConfig.V2RAYNFMTS) }

    private fun hasConfigLink(text: String): Boolean = text.lineSequence().any { isConfigLink(it.trim()) }

    /** Null when no line is an http(s) URL or a config link. */
    private fun classifyLines(text: String): ImportKind? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size == 1 && isHttpUrl(lines[0])) return ImportKind.Subscription(lines[0])
        // Several lines, or a URL among other text: Config keeps batch import (links and
        // subscription URLs in one paste) working through importBatchConfig.
        if (lines.any { isHttpUrl(it) || isConfigLink(it) }) return ImportKind.Config
        return null
    }

    private fun isHttpUrl(value: String): Boolean {
        if (value.any { it.isWhitespace() }) return false
        return try {
            val uri = URI(value)
            (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun classifyJson(text: String): ImportKind {
        val root = try {
            JsonParser.parseString(text)
        } catch (_: Exception) {
            return ImportKind.Invalid
        }
        return when {
            root is JsonObject -> ImportKind.Config
            root is JsonArray && isRoutingArray(root) -> ImportKind.Routing
            root is JsonArray -> ImportKind.Config
            else -> ImportKind.Invalid
        }
    }

    private fun isRoutingArray(array: JsonArray): Boolean =
        array.size() > 0 && array.all { it is JsonObject && it.has("outboundTag") }
}
