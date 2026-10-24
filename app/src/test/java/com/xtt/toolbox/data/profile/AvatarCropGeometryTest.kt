package com.xtt.toolbox.data.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarCropGeometryTest {
    @Test
    fun landscapeIsCenteredWithSquareSourceWindow() {
        val geometry = AvatarCrop.geometry(
            sourceWidth = 4000,
            sourceHeight = 2000,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
            viewportPx = 1000f,
        )
        assertEquals(1000f, geometry.left, 0.01f)
        assertEquals(0f, geometry.top, 0.01f)
        assertEquals(2000f, geometry.side, 0.01f)
    }

    @Test
    fun portraitIsCenteredWithSquareSourceWindow() {
        val geometry = AvatarCrop.geometry(
            sourceWidth = 2000,
            sourceHeight = 4000,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
            viewportPx = 1000f,
        )
        assertEquals(0f, geometry.left, 0.01f)
        assertEquals(1000f, geometry.top, 0.01f)
        assertEquals(2000f, geometry.side, 0.01f)
    }

    @Test
    fun zoomShrinksSourceWindow() {
        val normal = AvatarCrop.geometry(4000, 2000, 1f, 0f, 0f, 1000f)
        val zoomed = AvatarCrop.geometry(4000, 2000, 2f, 0f, 0f, 1000f)
        assertEquals(normal.side / 2f, zoomed.side, 0.01f)
    }

    @Test
    fun panMovesWindowWithoutLeavingImage() {
        val shifted = AvatarCrop.geometry(4000, 2000, 1f, -500f, 0f, 1000f)
        assertEquals(2000f, shifted.left, 0.01f)
        assertEquals(0f, shifted.top, 0.01f)
        assertTrue(shifted.left >= 0f && shifted.top >= 0f)
        assertTrue(shifted.left + shifted.side <= 4000f)
        assertTrue(shifted.top + shifted.side <= 2000f)
    }
}
