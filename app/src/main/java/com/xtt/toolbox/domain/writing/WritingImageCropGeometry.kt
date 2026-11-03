package com.xtt.toolbox.domain.writing

/** 裁剪矩形使用已纠正 EXIF 方向的源图像素坐标，right/bottom 为不包含端点。 */
data class WritingImageCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

enum class WritingImageCropHandle {
    MOVE,
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    RIGHT,
    BOTTOM_RIGHT,
    BOTTOM,
    BOTTOM_LEFT,
    LEFT,
}

data class FloatRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * 纯几何裁剪：全部坐标在源图像素空间，预览只负责显示。
 * 这样切换比例、移动和缩放都不会以缩小后的预览图作为最终裁剪源。
 */
object WritingImageCropGeometry {
    const val MIN_CROP_SIZE = 16

    fun full(width: Int, height: Int): WritingImageCropRect =
        WritingImageCropRect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))

    fun clamp(
        rect: WritingImageCropRect,
        sourceWidth: Int,
        sourceHeight: Int,
        minimum: Int = MIN_CROP_SIZE,
    ): WritingImageCropRect {
        val width = sourceWidth.coerceAtLeast(1)
        val height = sourceHeight.coerceAtLeast(1)
        val minSize = minimum.coerceAtMost(minOf(width, height)).coerceAtLeast(1)
        var left = rect.left.coerceIn(0, width - minSize)
        var top = rect.top.coerceIn(0, height - minSize)
        var right = rect.right.coerceIn(left + minSize, width)
        var bottom = rect.bottom.coerceIn(top + minSize, height)
        if (right - left < minSize) {
            left = (right - minSize).coerceAtLeast(0)
            right = (left + minSize).coerceAtMost(width)
        }
        if (bottom - top < minSize) {
            top = (bottom - minSize).coerceAtLeast(0)
            bottom = (top + minSize).coerceAtMost(height)
        }
        return WritingImageCropRect(left, top, right, bottom)
    }

    fun move(
        rect: WritingImageCropRect,
        deltaX: Float,
        deltaY: Float,
        sourceWidth: Int,
        sourceHeight: Int,
    ): WritingImageCropRect {
        val dx = deltaX.toInt()
        val dy = deltaY.toInt()
        val left = (rect.left + dx).coerceIn(0, sourceWidth - rect.width)
        val top = (rect.top + dy).coerceIn(0, sourceHeight - rect.height)
        return WritingImageCropRect(left, top, left + rect.width, top + rect.height)
    }

    fun resize(
        rect: WritingImageCropRect,
        handle: WritingImageCropHandle,
        deltaX: Float,
        deltaY: Float,
        sourceWidth: Int,
        sourceHeight: Int,
        aspectRatio: Float?,
        minimum: Int = MIN_CROP_SIZE,
    ): WritingImageCropRect {
        if (handle == WritingImageCropHandle.MOVE) {
            return move(rect, deltaX, deltaY, sourceWidth, sourceHeight)
        }
        val minSize = minimum.coerceAtLeast(1)
        var left = rect.left
        var top = rect.top
        var right = rect.right
        var bottom = rect.bottom
        val dx = deltaX.toInt()
        val dy = deltaY.toInt()

        when (handle) {
            WritingImageCropHandle.TOP_LEFT -> { left += dx; top += dy }
            WritingImageCropHandle.TOP -> top += dy
            WritingImageCropHandle.TOP_RIGHT -> { right += dx; top += dy }
            WritingImageCropHandle.RIGHT -> right += dx
            WritingImageCropHandle.BOTTOM_RIGHT -> { right += dx; bottom += dy }
            WritingImageCropHandle.BOTTOM -> bottom += dy
            WritingImageCropHandle.BOTTOM_LEFT -> { left += dx; bottom += dy }
            WritingImageCropHandle.LEFT -> left += dx
            WritingImageCropHandle.MOVE -> Unit
        }

        left = left.coerceIn(0, sourceWidth - minSize)
        top = top.coerceIn(0, sourceHeight - minSize)
        right = right.coerceIn(left + minSize, sourceWidth)
        bottom = bottom.coerceIn(top + minSize, sourceHeight)

        if (aspectRatio == null || aspectRatio <= 0f) {
            return clamp(WritingImageCropRect(left, top, right, bottom), sourceWidth, sourceHeight, minSize)
        }

        val anchorLeft = when (handle) {
            WritingImageCropHandle.LEFT, WritingImageCropHandle.TOP_LEFT,
            WritingImageCropHandle.BOTTOM_LEFT -> right
            else -> left
        }
        val anchorTop = when (handle) {
            WritingImageCropHandle.TOP, WritingImageCropHandle.TOP_LEFT,
            WritingImageCropHandle.TOP_RIGHT -> bottom
            else -> top
        }
        var width = (right - left).coerceAtLeast(minSize)
        var height = (bottom - top).coerceAtLeast(minSize)
        if (handle == WritingImageCropHandle.TOP || handle == WritingImageCropHandle.BOTTOM) {
            height = (bottom - top).coerceAtLeast(minSize)
            width = (height * aspectRatio).toInt().coerceAtLeast(minSize)
        } else if (handle == WritingImageCropHandle.LEFT || handle == WritingImageCropHandle.RIGHT) {
            width = (right - left).coerceAtLeast(minSize)
            height = (width / aspectRatio).toInt().coerceAtLeast(minSize)
        } else if (width / height.toFloat() > aspectRatio) {
            width = (height * aspectRatio).toInt().coerceAtLeast(minSize)
        } else {
            height = (width / aspectRatio).toInt().coerceAtLeast(minSize)
        }
        val newLeft = if (handle == WritingImageCropHandle.LEFT ||
            handle == WritingImageCropHandle.TOP_LEFT ||
            handle == WritingImageCropHandle.BOTTOM_LEFT
        ) {
            anchorLeft - width
        } else {
            anchorLeft
        }
        val newTop = if (handle == WritingImageCropHandle.TOP ||
            handle == WritingImageCropHandle.TOP_LEFT ||
            handle == WritingImageCropHandle.TOP_RIGHT
        ) {
            anchorTop - height
        } else {
            anchorTop
        }
        return clamp(
            WritingImageCropRect(
                newLeft.coerceIn(0, sourceWidth - 1),
                newTop.coerceIn(0, sourceHeight - 1),
                (newLeft + width).coerceIn(1, sourceWidth),
                (newTop + height).coerceIn(1, sourceHeight),
            ),
            sourceWidth,
            sourceHeight,
            minSize,
        )
    }

    /**
     * 切换比例时保持裁剪中心，并在当前裁剪区域内取最大可用矩形。
     * ratio 为 width / height；null 表示自由裁剪。
     */
    fun withAspect(
        rect: WritingImageCropRect,
        ratio: Float?,
        sourceWidth: Int,
        sourceHeight: Int,
        minimum: Int = MIN_CROP_SIZE,
    ): WritingImageCropRect {
        if (ratio == null || ratio <= 0f) {
            return clamp(rect, sourceWidth, sourceHeight, minimum)
        }
        var width = rect.width.toFloat()
        var height = rect.height.toFloat()
        if (width / height > ratio) {
            width = height * ratio
        } else {
            height = width / ratio
        }
        val centerX = rect.centerX
        val centerY = rect.centerY
        val left = (centerX - width / 2f).toInt().coerceAtLeast(0)
        val top = (centerY - height / 2f).toInt().coerceAtLeast(0)
        return clamp(
            WritingImageCropRect(
                left,
                top,
                (left + width.toInt()).coerceAtMost(sourceWidth),
                (top + height.toInt()).coerceAtMost(sourceHeight),
            ),
            sourceWidth,
            sourceHeight,
            minimum,
        )
    }

    /** 计算 ContentScale.Fit 的图片显示区域，和 Compose Image 使用同一比例。 */
    fun fitRect(
        containerWidth: Float,
        containerHeight: Float,
        sourceWidth: Int,
        sourceHeight: Int,
    ): FloatRect {
        if (containerWidth <= 0f || containerHeight <= 0f || sourceWidth <= 0 || sourceHeight <= 0) {
            return FloatRect(0f, 0f, 0f, 0f)
        }
        val scale = minOf(
            containerWidth / sourceWidth,
            containerHeight / sourceHeight,
        )
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        return FloatRect(
            left = (containerWidth - width) / 2f,
            top = (containerHeight - height) / 2f,
            width = width,
            height = height,
        )
    }

    fun sourceToDisplay(
        rect: WritingImageCropRect,
        image: FloatRect,
        sourceWidth: Int,
        sourceHeight: Int,
    ): FloatRect = FloatRect(
        left = image.left + rect.left.toFloat() / sourceWidth * image.width,
        top = image.top + rect.top.toFloat() / sourceHeight * image.height,
        width = rect.width.toFloat() / sourceWidth * image.width,
        height = rect.height.toFloat() / sourceHeight * image.height,
    )

    /** 显示像素移动量映射到源图像素；不会把预览缩放带进最终裁剪。 */
    fun displayDeltaToSource(
        deltaX: Float,
        deltaY: Float,
        image: FloatRect,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Float, Float> {
        if (image.width <= 0f || image.height <= 0f) return 0f to 0f
        return (deltaX / image.width * sourceWidth) to (deltaY / image.height * sourceHeight)
    }

    fun handleAt(
        pointX: Float,
        pointY: Float,
        cropDisplay: FloatRect,
        touchRadius: Float,
    ): WritingImageCropHandle? {
        val left = cropDisplay.left
        val top = cropDisplay.top
        val right = cropDisplay.right
        val bottom = cropDisplay.bottom
        val nearLeft = kotlin.math.abs(pointX - left) <= touchRadius
        val nearRight = kotlin.math.abs(pointX - right) <= touchRadius
        val nearTop = kotlin.math.abs(pointY - top) <= touchRadius
        val nearBottom = kotlin.math.abs(pointY - bottom) <= touchRadius
        return when {
            nearLeft && nearTop -> WritingImageCropHandle.TOP_LEFT
            nearRight && nearTop -> WritingImageCropHandle.TOP_RIGHT
            nearLeft && nearBottom -> WritingImageCropHandle.BOTTOM_LEFT
            nearRight && nearBottom -> WritingImageCropHandle.BOTTOM_RIGHT
            nearTop -> WritingImageCropHandle.TOP
            nearRight -> WritingImageCropHandle.RIGHT
            nearBottom -> WritingImageCropHandle.BOTTOM
            nearLeft -> WritingImageCropHandle.LEFT
            pointX in left..right && pointY in top..bottom -> WritingImageCropHandle.MOVE
            else -> null
        }
    }
}
