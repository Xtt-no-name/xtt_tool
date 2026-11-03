package com.xtt.toolbox.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class UpdateCheckerTest {
    @Test
    fun comparesPatchAndMinorVersions() {
        assertTrue(UpdateChecker.compareVersions("1.0.1", "1.0")!! > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.10", "1.0.2")!! > 0)
        assertTrue(UpdateChecker.compareVersions("1.1.0", "1.0.9")!! > 0)
        assertEquals(0, UpdateChecker.compareVersions("1.0.1", "1.0.1")!!)
        assertEquals(0, UpdateChecker.compareVersions("v1.0", "1.0.0")!!)
    }

    @Test
    fun rejectsInvalidVersions() {
        assertNull(UpdateChecker.compareVersions("1.0-beta", "1.0"))
        assertNull(UpdateChecker.parseVersion("v"))
        assertNull(UpdateChecker.parseVersion("1..0"))
        assertNull(UpdateChecker.parseVersion("01.a.1"))
    }

    @Test
    fun allowsOnlyThisRepositoryReleaseDownloads() {
        assertTrue(
            UpdateChecker.isAllowedDownloadUrl(
                "https://github.com/Xtt-no-name/xtt_tool/releases/download/v1.0.1/xtt-toolbox-1.0.1.apk",
            ),
        )
        assertTrue(
            UpdateChecker.isAllowedReleaseUrl(
                "https://github.com/Xtt-no-name/xtt_tool/releases/tag/v1.0.1",
            ),
        )
    }

    @Test
    fun rejectsLookalikeOrInsecureUrls() {
        assertFalse(
            UpdateChecker.isAllowedDownloadUrl(
                "http://github.com/Xtt-no-name/xtt_tool/releases/download/v1.0.1/xtt-toolbox-1.0.1.apk",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedDownloadUrl(
                "https://github.com/Other/xtt_tool/releases/download/v1.0.1/xtt-toolbox-1.0.1.apk",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedDownloadUrl(
                "https://github.com/Xtt-no-name/xtt_tool/releases/tag/v1.0.1",
            ),
        )
        assertFalse(
            UpdateChecker.isAllowedReleaseUrl(
                "https://evil.example/Xtt-no-name/xtt_tool/releases/tag/v1.0.1",
            ),
        )
    }

    @Test
    fun comparesReleaseVersionCodeInsteadOfVersionNameOnly() {
        val json = releaseJson(tag = "v9.9.9", versionCode = 4)

        val newer = UpdateChecker.parseLatestRelease(json, "1.0.2", 3)
        assertTrue(newer is UpdateCheckResult.Available)
        assertEquals(4, (newer as UpdateCheckResult.Available).info.latestVersionCode)

        val same = UpdateChecker.parseLatestRelease(json, "1.0.3", 4)
        assertTrue(same is UpdateCheckResult.UpToDate)

        val newerLocal = UpdateChecker.parseLatestRelease(json, "1.0.4", 5)
        assertTrue(newerLocal is UpdateCheckResult.UpToDate)
    }

    @Test
    fun rejectsReleaseWithoutVersionCode() {
        val json = releaseJson(tag = "v1.0.4", versionCode = null)
        assertTrue(UpdateChecker.parseLatestRelease(json, "1.0.3", 4) is UpdateCheckResult.Failure)
    }

    @Test
    fun rejectsDraftAndPrerelease() {
        val draft = releaseJson(tag = "v1.0.4", versionCode = 5, draft = true)
        val prerelease = releaseJson(tag = "v1.0.4", versionCode = 5, prerelease = true)
        assertTrue(UpdateChecker.parseLatestRelease(draft, "1.0.3", 4) is UpdateCheckResult.Failure)
        assertTrue(UpdateChecker.parseLatestRelease(prerelease, "1.0.3", 4) is UpdateCheckResult.Failure)
    }

    private fun releaseJson(
        tag: String,
        versionCode: Int?,
        prerelease: Boolean = false,
        draft: Boolean = false,
    ): String = JSONObject().apply {
        put("tag_name", tag)
        put("html_url", "https://github.com/Xtt-no-name/xtt_tool/releases/tag/$tag")
        put("prerelease", prerelease)
        put("draft", draft)
        put(
            "body",
            buildString {
                append("## 更新\n")
                if (versionCode != null) append("versionCode：").append(versionCode).append('\n')
                append("这是测试 Release。")
            },
        )
        put(
            "assets",
            JSONArray().put(
                JSONObject().apply {
                    put("name", "xtt-toolbox-$tag.apk")
                    put(
                        "browser_download_url",
                        "https://github.com/Xtt-no-name/xtt_tool/releases/download/$tag/xtt-toolbox-$tag.apk",
                    )
                    put("digest", "sha256:abc123")
                },
            ),
        )
    }.toString()
}
