package com.xtt.toolbox.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRulesTest {
    @Test
    fun trimsValidNickname() {
        assertEquals("xtt的工具箱", ProfileRules.normalizeNickname("  xtt的工具箱  "))
    }

    @Test
    fun rejectsBlankAndOverlongNickname() {
        assertNull(ProfileRules.normalizeNickname("   "))
        assertNull(ProfileRules.normalizeNickname("x".repeat(ProfileRules.MAX_NICKNAME_LENGTH + 1)))
    }

    @Test
    fun acceptsMaximumLength() {
        val name = "x".repeat(ProfileRules.MAX_NICKNAME_LENGTH)
        assertEquals(name, ProfileRules.normalizeNickname(name))
    }
}
