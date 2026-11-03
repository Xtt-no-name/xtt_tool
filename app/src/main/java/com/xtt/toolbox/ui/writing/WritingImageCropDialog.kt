package com.xtt.toolbox.ui.writing

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xtt.toolbox.data.importing.WritingImageCropSource
import com.xtt.toolbox.domain.writing.WritingImageCropGeometry
import com.xtt.toolbox.domain.writing.WritingImageCropHandle
import com.xtt.toolbox.domain.writing.WritingImageCropRect

/**
 * 写作编辑器专用图片裁剪。
 *
 * 与头像裁剪分离：不固定正方形，裁剪矩形在源图像素坐标中维护；preview 只画到屏幕，
 * 最终输出始终从 source 重新取样，因此缩放/预览不会反向裁剪原始图片。
 */
@Composable
fun WritingImageCropDialog(
    source: WritingImageCropSource,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: (WritingImageCropRect) -> Unit,
) {
    var crop by remember(source) {
        mutableStateOf(WritingImageCropGeometry.full(source.sourceWidth, source.sourceHeight))
    }
    var ratio by remember(source) {
        mutableStateOf(source.sourceWidth.toFloat() / source.sourceHeight)
    }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf<WritingImageCropHandle?>(null) }
    val density = LocalDensity.current
    val touchRadius = with(density) { 24.dp.toPx() }
    val imageRect = WritingImageCropGeometry.fitRect(
        viewport.width.toFloat(),
        viewport.height.toFloat(),
        source.sourceWidth,
        source.sourceHeight,
    )
    val cropDisplay = WritingImageCropGeometry.sourceToDisplay(
        crop,
        imageRect,
        source.sourceWidth,
        source.sourceHeight,
    )
    val currentCropDisplay by rememberUpdatedState(cropDisplay)
    val currentCrop by rememberUpdatedState(crop)
    val accent = MaterialTheme.colorScheme.primary

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel, enabled = !busy) { Text("取消") }
                    Text(
                        text = "裁剪图片",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(
                        onClick = { onConfirm(crop) },
                        enabled = !busy && crop.width > 0 && crop.height > 0,
                    ) {
                        Text(if (busy) "保存中…" else "使用此图片")
                    }
                }
                Text(
                    text = "拖动裁剪框；拖动边缘或四角调整范围。确认后从源图重新取样。",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .onSizeChanged { viewport = it }
                        .pointerInput(source, ratio, viewport) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    activeHandle = WritingImageCropGeometry.handleAt(
                                        pointX = position.x,
                                        pointY = position.y,
                                        cropDisplay = currentCropDisplay,
                                        touchRadius = touchRadius,
                                    )
                                },
                                onDrag = { change, dragAmount ->
                                    val handle = activeHandle ?: return@detectDragGestures
                                    change.consume()
                                    val (dx, dy) = WritingImageCropGeometry.displayDeltaToSource(
                                        deltaX = dragAmount.x,
                                        deltaY = dragAmount.y,
                                        image = imageRect,
                                        sourceWidth = source.sourceWidth,
                                        sourceHeight = source.sourceHeight,
                                    )
                                    crop = WritingImageCropGeometry.resize(
                                        rect = currentCrop,
                                        handle = handle,
                                        deltaX = dx,
                                        deltaY = dy,
                                        sourceWidth = source.sourceWidth,
                                        sourceHeight = source.sourceHeight,
                                        aspectRatio = if (ratio > 0f) ratio else null,
                                    )
                                },
                                onDragEnd = { activeHandle = null },
                                onDragCancel = { activeHandle = null },
                            )
                        },
                ) {
                    Image(
                        bitmap = source.preview.asImageBitmap(),
                        contentDescription = "待裁剪图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val imageLeft = imageRect.left
                        val imageTop = imageRect.top
                        val imageRight = imageRect.right
                        val imageBottom = imageRect.bottom
                        if (imageRect.width <= 0f || imageRect.height <= 0f) return@Canvas
                        val dark = Color.Black.copy(alpha = 0.45f)
                        // 只压暗图片内、裁剪框外的区域。
                        drawRect(
                            dark,
                            topLeft = Offset(imageLeft, imageTop),
                            size = Size(imageRect.width, (cropDisplay.top - imageTop).coerceAtLeast(0f)),
                        )
                        drawRect(
                            dark,
                            topLeft = Offset(imageLeft, cropDisplay.bottom),
                            size = Size(
                                imageRect.width,
                                (imageBottom - cropDisplay.bottom).coerceAtLeast(0f),
                            ),
                        )
                        drawRect(
                            dark,
                            topLeft = Offset(imageLeft, cropDisplay.top),
                            size = Size(
                                (cropDisplay.left - imageLeft).coerceAtLeast(0f),
                                cropDisplay.height,
                            ),
                        )
                        drawRect(
                            dark,
                            topLeft = Offset(cropDisplay.right, cropDisplay.top),
                            size = Size(
                                (imageRight - cropDisplay.right).coerceAtLeast(0f),
                                cropDisplay.height,
                            ),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(cropDisplay.left, cropDisplay.top),
                            size = Size(cropDisplay.width, cropDisplay.height),
                            style = Stroke(width = with(density) { 2.dp.toPx() }),
                        )
                        val handleRadius = with(density) { 5.dp.toPx() }
                        val handleColor = accent
                        listOf(
                            Offset(cropDisplay.left, cropDisplay.top),
                            Offset(cropDisplay.right, cropDisplay.top),
                            Offset(cropDisplay.left, cropDisplay.bottom),
                            Offset(cropDisplay.right, cropDisplay.bottom),
                            Offset(cropDisplay.left, cropDisplay.top + cropDisplay.height / 2f),
                            Offset(cropDisplay.right, cropDisplay.top + cropDisplay.height / 2f),
                            Offset(cropDisplay.left + cropDisplay.width / 2f, cropDisplay.top),
                            Offset(cropDisplay.left + cropDisplay.width / 2f, cropDisplay.bottom),
                        ).forEach { point ->
                            drawCircle(Color.White, radius = handleRadius + 2f, center = point)
                            drawCircle(handleColor, radius = handleRadius, center = point)
                        }
                    }
                }
                Text(
                    text = "${crop.width} × ${crop.height} px",
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val sourceRatio = source.sourceWidth.toFloat() / source.sourceHeight
                    val ratios = listOf(
                        null to "自由",
                        sourceRatio to "原图",
                        1f to "1:1",
                        4f / 3f to "4:3",
                        3f / 4f to "3:4",
                        16f / 9f to "16:9",
                        9f / 16f to "9:16",
                    )
                    ratios.forEach { (value, label) ->
                        val selected = if (value == null) {
                            ratio <= 0f
                        } else {
                            kotlin.math.abs(ratio - value) < 0.001f
                        }
                        TextButton(
                            onClick = {
                                ratio = value ?: 0f
                                crop = WritingImageCropGeometry.withAspect(
                                    rect = crop,
                                    ratio = value,
                                    sourceWidth = source.sourceWidth,
                                    sourceHeight = source.sourceHeight,
                                )
                            },
                            enabled = !busy,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(1.dp))
            }
        }
    }
}
