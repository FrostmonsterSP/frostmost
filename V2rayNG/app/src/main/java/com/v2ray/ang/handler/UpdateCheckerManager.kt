package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val url = if (includePreRelease) {
            AppConfig.APP_API_URL
        } else {
            AppConfig.APP_API_URL.concatUrl("latest")
        }

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        val latestRelease = if (includePreRelease) {
            JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
                ?.firstOrNull()
                ?: throw IllegalStateException("No pre-release found")
        } else {
            JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
        }
        if (latestRelease == null) {
            return@withContext CheckUpdateResult(hasUpdate = false)
        }

        val latestVersion = latestRelease.tagName.removePrefix("v")
        LogUtil.i(
            AppConfig.TAG,
            "Found new version: $latestVersion (current: ${BuildConfig.VERSION_NAME})"
        )

        return@withContext if (compareVersions(latestVersion, BuildConfig.VERSION_NAME) > 0) {
            val downloadUrl = getDownloadUrl(latestRelease, Build.SUPPORTED_ABIS[0])
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = downloadUrl,
                isPreRelease = latestRelease.prerelease
            )
        } else {
            CheckUpdateResult(hasUpdate = false)
        }
    }

    internal fun compareVersions(version1: String, version2: String): Int {
        val v1 = parseVersion(version1)
        val v2 = parseVersion(version2)

        for (i in 0 until maxOf(v1.numbers.size, v2.numbers.size)) {
            val num1 = v1.numbers.getOrElse(i) { 0 }
            val num2 = v2.numbers.getOrElse(i) { 0 }
            if (num1 != num2) return if (num1 > num2) 1 else -1
        }

        // Same numbers: a plain release outranks its own pre-release, and among
        // pre-releases the larger suffix wins (rc2 > rc1). Shorter suffixes sort
        // first, so 0.9.0rc < 0.9.0rc1 < 0.9.0.
        if (v1.preRelease == v2.preRelease) return 0
        if (v1.preRelease.isEmpty()) return 1
        if (v2.preRelease.isEmpty()) return -1
        return v1.preRelease.compareTo(v2.preRelease).coerceIn(-1, 1)
    }

    /**
     * Parses a release tag or version name such as `0.9.0rc`, `v0.9.0-rc1` or `2.3.15`.
     *
     * Only the leading digits of each dot-separated segment are compared numerically; anything
     * after them (`rc`, `-rc1`, `beta`) is kept as a pre-release marker. The previous
     * implementation called `toInt()` on the whole segment, which threw
     * `NumberFormatException` on any version carrying a suffix and made update checks fail.
     */
    internal fun parseVersion(version: String): ParsedVersion {
        val normalized = version.trim().removePrefix("v").removePrefix("V")
        val numbers = mutableListOf<Int>()
        val preRelease = StringBuilder()

        // The pre-release marker starts at the first segment that is not purely numeric,
        // and everything from there on belongs to it, so `1.0.0-rc.1` keeps "rc.1".
        var inPreRelease = false
        normalized.split(".").forEach { segment ->
            if (inPreRelease) {
                preRelease.append(segment)
                return@forEach
            }
            val digits = segment.takeWhile { it.isDigit() }
            if (digits.isEmpty() || digits.length != segment.length) {
                inPreRelease = true
                preRelease.append(segment.removePrefix(digits))
            }
            numbers += digits.toIntOrNull() ?: 0
        }

        return ParsedVersion(numbers, preRelease.toString().trimStart('-', '+', '_', ' '))
    }

    internal data class ParsedVersion(val numbers: List<Int>, val preRelease: String)

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String {
        val fDroid = "fdroid"

        val assetsByAbi = release.assets.filter {
            (it.name.contains(abi, true))
        }

        val asset = if (BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)) {
            assetsByAbi.firstOrNull { it.name.contains(fDroid) }
        } else {
            assetsByAbi.firstOrNull { !it.name.contains(fDroid) }
        }

        return asset?.browserDownloadUrl
            ?: throw IllegalStateException("No compatible APK found")
    }
}
