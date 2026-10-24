package com.xtt.toolbox.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
