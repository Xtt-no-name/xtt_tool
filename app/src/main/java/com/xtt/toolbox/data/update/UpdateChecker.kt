package com.xtt.toolbox.data.update

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseUrl: String,
    val apkUrl: String,
    val apkSha256: String?,
)

sealed interface UpdateCheckResult {
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
    ) : UpdateCheckResult

    data class Available(val info: UpdateInfo) : UpdateCheckResult

    data class Failure(val message: String) : UpdateCheckResult
}

/**
 * 只读检查本项目 GitHub 的正式 Release。
 *
 * 不下载 APK、不安装 APK，也不携带任何 Token；下载和安装交给浏览器及 Android 系统。
 */
object UpdateChecker {
    const val REPOSITORY = "Xtt-no-name/xtt_tool"

    private const val API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val RELEASE_URL_PREFIX = "https://github.com/$REPOSITORY/releases/"
    private const val DOWNLOAD_URL_PREFIX = "https://github.com/$REPOSITORY/releases/download/"
    private const val USER_AGENT = "xtt-toolbox-android-update-check"

    fun check(currentVersion: String): UpdateCheckResult {
        val body = try {
            requestLatestRelease()
        } catch (e: IOException) {
            return UpdateCheckResult.Failure("网络连接失败，请稍后重试")
        } catch (e: SecurityException) {
            return UpdateCheckResult.Failure("没有网络访问权限")
        }

        return try {
            parseLatestRelease(body, currentVersion)
        } catch (e: Exception) {
            UpdateCheckResult.Failure("GitHub Release 数据格式异常")
        }
    }

    internal fun parseLatestRelease(
        jsonText: String,
        currentVersion: String,
    ): UpdateCheckResult {
        val json = JSONObject(jsonText)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
            return UpdateCheckResult.Failure("最新发布不是稳定版本")
        }

        val latestVersion = json.optString("tag_name").removePrefix("v").trim()
        if (parseVersion(latestVersion) == null) {
            return UpdateCheckResult.Failure("无法识别最新版本号")
        }
        if (parseVersion(currentVersion) == null) {
            return UpdateCheckResult.Failure("无法识别当前版本号")
        }

        val releaseUrl = json.optString("html_url")
        if (!isAllowedReleaseUrl(releaseUrl)) {
            return UpdateCheckResult.Failure("Release 地址不属于本项目")
        }

        val assets = json.optJSONArray("assets")
            ?: return UpdateCheckResult.Failure("Release 中没有 APK 资产")
        var apkUrl: String? = null
        var apkSha256: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.startsWith("xtt-toolbox-") || !name.endsWith(".apk")) continue
            val candidate = asset.optString("browser_download_url")
            if (isAllowedDownloadUrl(candidate)) {
                apkUrl = candidate
                apkSha256 = asset.optString("digest")
                    .takeIf { it.startsWith("sha256:") }
                    ?.removePrefix("sha256:")
                break
            }
        }
        if (apkUrl == null) {
            return UpdateCheckResult.Failure("Release 中没有可用的 APK 下载地址")
        }

        return when (val comparison = compareVersions(latestVersion, currentVersion)) {
            null -> UpdateCheckResult.Failure("无法比较版本号")
            0, -1 -> UpdateCheckResult.UpToDate(currentVersion, latestVersion)
            else -> UpdateCheckResult.Available(
                UpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    releaseUrl = releaseUrl,
                    apkUrl = apkUrl,
                    apkSha256 = apkSha256,
                ),
            )
        }
    }

    /** 返回 1 表示 left 更新，0 表示相同，-1 表示 left 更旧；非法版本返回 null。 */
    internal fun compareVersions(left: String, right: String): Int? {
        val leftParts = parseVersion(left) ?: return null
        val rightParts = parseVersion(right) ?: return null
        val size = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until size) {
            val a = leftParts.getOrElse(index) { 0 }
            val b = rightParts.getOrElse(index) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    internal fun parseVersion(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v")
        if (normalized.isBlank() || normalized.contains('-') || normalized.contains('+')) {
            return null
        }
        val parts = normalized.split('.')
        if (parts.any { part -> part.isEmpty() || part.any { !it.isDigit() } }) return null
        val numbers = parts.mapNotNull { it.toIntOrNull() }
        return numbers.takeIf { it.size == parts.size }
    }

    internal fun isAllowedReleaseUrl(url: String): Boolean {
        return isAllowedHttpsGithubUrl(url, RELEASE_URL_PREFIX)
    }

    internal fun isAllowedDownloadUrl(url: String): Boolean {
        return isAllowedHttpsGithubUrl(url, DOWNLOAD_URL_PREFIX)
    }

    private fun isAllowedHttpsGithubUrl(url: String, prefix: String): Boolean {
        if (!url.startsWith(prefix)) return false
        return try {
            val parsed = URL(url)
            parsed.protocol == "https" &&
                parsed.host == "github.com" &&
                !parsed.path.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun requestLatestRelease(): String {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = when (code) {
                    403 -> "GitHub API 请求过于频繁，请稍后重试"
                    404 -> "没有找到正式 Release"
                    else -> "GitHub API 返回 HTTP $code"
                }
                throw IOException(message)
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
