package com.xtt.toolbox.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNoticePolicyTest {
    private val notes = ReleaseNotesCatalog.forVersion(4)

    @Test
    fun firstInstallShowsCurrentReleaseNotes() {
        assertNotNull(notes)
        assertTrue(ReleaseNoticePolicy.shouldShowLocal(4, 0, notes))
    }

    @Test
    fun upgradeShowsOnceAndSameVersionDoesNotRepeat() {
        assertTrue(ReleaseNoticePolicy.shouldShowLocal(4, 3, notes))
        assertFalse(ReleaseNoticePolicy.shouldShowLocal(4, 4, notes))
        assertFalse(ReleaseNoticePolicy.shouldShowLocal(4, 5, notes))
    }

    @Test
    fun localNoticeNeedsCatalogContent() {
        assertFalse(ReleaseNoticePolicy.shouldShowLocal(4, 0, null))
    }

    @Test
    fun remoteNoticeOnlyForNewerTargetAndOnlyOnce() {
        assertTrue(ReleaseNoticePolicy.shouldShowRemote(4, 5, 0))
        assertTrue(ReleaseNoticePolicy.shouldShowRemote(4, 5, 4))
        assertFalse(ReleaseNoticePolicy.shouldShowRemote(4, 4, 0))
        assertFalse(ReleaseNoticePolicy.shouldShowRemote(4, 3, 0))
        assertFalse(ReleaseNoticePolicy.shouldShowRemote(4, 5, 5))
        assertFalse(ReleaseNoticePolicy.shouldShowRemote(5, 5, 4))
    }

    @Test
    fun localNoticeIsEligibleBeforeRemoteNotice() {
        assertTrue(ReleaseNoticePolicy.shouldShowLocal(4, 3, notes))
        // AppRoot 只在本地公告关闭后才调用远端检查；策略函数本身不提前记录远端版本。
        assertTrue(ReleaseNoticePolicy.shouldShowRemote(4, 5, 0))
    }
}
