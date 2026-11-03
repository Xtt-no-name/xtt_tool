package com.xtt.toolbox.domain.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingImageCropGeometryTest {
    private val width = 4000
    private val height = 3000

    @Test
    fun startsWithFullOriginalImage() {
        val full = WritingImageCropGeometry.full(width, height)
        assertEquals(0, full.left)
        assertEquals(0, full.top)
        assertEquals(width, full.right)
        assertEquals(height, full.bottom)
        assertEquals(width, full.width)
        assertEquals(height, full.height)
    }

    @Test
    fun allRatiosStayInsideSourceAndKeepCenter() {
        val full = WritingImageCropGeometry.full(width, height)
        val ratios = listOf(1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f, width.toFloat() / height)
        ratios.forEach { ratio ->
            val result = WritingImageCropGeometry.withAspect(full, ratio, width, height)
            assertEquals(ratio, result.width.toFloat() / result.height, 0.02f)
            assertTrue(result.left >= 0 && result.top >= 0)
            assertTrue(result.right <= width && result.bottom <= height)
            assertEquals(width / 2f, result.centerX, 1.5f)
            assertEquals(height / 2f, result.centerY, 1.5f)
        }
    }

    @Test
    fun movingCropClampsToImage() {
        val crop = WritingImageCropRect(100, 200, 1100, 1000)
        val movedLeft = WritingImageCropGeometry.move(crop, -5000f, -5000f, width, height)
        assertEquals(0, movedLeft.left)
        assertEquals(0, movedLeft.top)
        assertEquals(crop.width, movedLeft.width)
        assertEquals(crop.height, movedLeft.height)

        val movedRight = WritingImageCropGeometry.move(crop, 5000f, 5000f, width, height)
        assertEquals(width, movedRight.right)
        assertEquals(height, movedRight.bottom)
    }

    @Test
    fun cornerAndEdgeResizeRespectMinimumAndBounds() {
        val crop = WritingImageCropRect(1000, 1000, 2000, 2000)
        val tiny = WritingImageCropGeometry.resize(
            crop,
            WritingImageCropHandle.TOP_LEFT,
            5000f,
            5000f,
            width,
            height,
            aspectRatio = null,
        )
        assertTrue(tiny.width >= WritingImageCropGeometry.MIN_CROP_SIZE)
        assertTrue(tiny.height >= WritingImageCropGeometry.MIN_CROP_SIZE)
        assertTrue(tiny.right <= width && tiny.bottom <= height)

        val corner = WritingImageCropGeometry.resize(
            crop,
            WritingImageCropHandle.BOTTOM_RIGHT,
            300f,
            200f,
            width,
            height,
            aspectRatio = null,
        )
        assertEquals(2300, corner.right)
        assertEquals(2200, corner.bottom)

        val edge = WritingImageCropGeometry.resize(
            crop,
            WritingImageCropHandle.RIGHT,
            -200f,
            0f,
            width,
            height,
            aspectRatio = null,
        )
        assertEquals(1800, edge.right)
        assertEquals(2000, edge.bottom)
    }

    @Test
    fun fixedRatioResizeKeepsRatio() {
        val crop = WritingImageCropGeometry.withAspect(
            WritingImageCropGeometry.full(width, height),
            16f / 9f,
            width,
            height,
        )
        val resized = WritingImageCropGeometry.resize(
            crop,
            WritingImageCropHandle.BOTTOM_RIGHT,
            -300f,
            -100f,
            width,
            height,
            aspectRatio = 16f / 9f,
        )
        assertEquals(16f / 9f, resized.width.toFloat() / resized.height, 0.02f)
        assertTrue(resized.right <= width && resized.bottom <= height)
    }

    @Test
    fun detectsHandlesAndMoveArea() {
        val display = FloatRect(100f, 50f, 400f, 300f)
        assertEquals(
            WritingImageCropHandle.TOP_LEFT,
            WritingImageCropGeometry.handleAt(104f, 54f, display, 24f),
        )
        assertEquals(
            WritingImageCropHandle.BOTTOM_RIGHT,
            WritingImageCropGeometry.handleAt(497f, 347f, display, 24f),
        )
        assertEquals(
            WritingImageCropHandle.MOVE,
            WritingImageCropGeometry.handleAt(250f, 200f, display, 24f),
        )
        assertNull(WritingImageCropGeometry.handleAt(900f, 900f, display, 24f))
    }

    @Test
    fun previewDisplayCoordinatesMapBackToSourcePixels() {
        val image = WritingImageCropGeometry.fitRect(1000f, 500f, width, height)
        assertEquals(666.67f, image.width, 0.1f)
        assertEquals(500f, image.height, 0.1f)
        assertEquals(166.67f, image.left, 0.1f)

        val displayCrop = WritingImageCropGeometry.sourceToDisplay(
            WritingImageCropRect(1000, 750, 3000, 2250),
            image,
            width,
            height,
        )
        assertEquals(333.33f, displayCrop.width, 0.2f)
        // 原图 4000x3000，显示区域 666.67x500：裁剪宽度 2000px 映射为 333.33px；
        // 高度 1500px 映射为 250px。
        assertEquals(250f, displayCrop.height, 0.2f)

        val (dx, dy) = WritingImageCropGeometry.displayDeltaToSource(
            66.67f,
            25f,
            image,
            width,
            height,
        )
        assertEquals(400f, dx, 0.5f)
        assertEquals(150f, dy, 0.5f)
    }

    @Test
    fun cropRectCanBeConstructedForUiConfirmation() {
        val crop = WritingImageCropRect(10, 20, 110, 220)
        assertNotNull(crop)
        assertEquals(100, crop.width)
        assertEquals(200, crop.height)
    }
}
