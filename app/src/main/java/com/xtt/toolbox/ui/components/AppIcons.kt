package com.xtt.toolbox.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.xtt.toolbox.R

/**
 * 项目自带的轻量线性图标，避免为图标引入额外依赖。
 * 图标本身不承载语义，调用方通过按钮的 contentDescription 提供可访问名称。
 */

@Composable
fun ConstructionIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.16f, h * 0.30f),
            size = Size(w * 0.68f, h * 0.52f),
            style = line,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.08f, h * 0.76f),
            end = Offset(w * 0.92f, h * 0.76f),
            strokeWidth = line.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.34f),
            end = Offset(w * 0.5f, h * 0.74f),
            strokeWidth = line.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun BookIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.22f, h * 0.14f),
            size = Size(w * 0.56f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = line,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.44f, h * 0.14f),
            end = Offset(w * 0.44f, h * 0.86f),
            strokeWidth = 1.7.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun PenIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(tint, Offset(w * 0.25f, h * 0.75f), Offset(w * 0.68f, h * 0.32f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.68f, h * 0.32f), Offset(w * 0.78f, h * 0.22f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.25f, h * 0.75f), Offset(w * 0.36f, h * 0.72f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun ToolboxIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val corner = CornerRadius(w * 0.05f)
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        listOf(0.18f, 0.52f).forEach { x ->
            listOf(0.18f, 0.52f).forEach { y ->
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * x, h * y),
                    size = Size(w * 0.3f, h * 0.3f),
                    cornerRadius = corner,
                    style = line,
                )
            }
        }
    }
}

@Composable
fun PersonIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(
            color = tint,
            radius = w * 0.16f,
            center = Offset(w * 0.5f, h * 0.34f),
            style = line,
        )
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.46f),
            size = Size(w * 0.6f, h * 0.44f),
            style = line,
        )
    }
}

@Composable
fun SearchIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(
            color = tint,
            radius = w * 0.22f,
            center = Offset(w * 0.43f, h * 0.43f),
            style = line,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.59f, h * 0.59f),
            end = Offset(w * 0.78f, h * 0.78f),
            strokeWidth = 1.7.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun ImportIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawLine(tint, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.62f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.33f, h * 0.47f), Offset(w * 0.5f, h * 0.64f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.67f, h * 0.47f), Offset(w * 0.5f, h * 0.64f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.78f, h * 0.78f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun PlusIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(tint, Offset(w * 0.5f, h * 0.24f), Offset(w * 0.5f, h * 0.76f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.24f, h * 0.5f), Offset(w * 0.76f, h * 0.5f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun ChevronRightIcon(modifier: Modifier = Modifier.size(16.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        drawLine(tint, Offset(w * 0.38f, h * 0.24f), Offset(w * 0.62f, h * 0.5f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.62f, h * 0.5f), Offset(w * 0.38f, h * 0.76f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun GridIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val corner = CornerRadius(w * 0.04f)
        listOf(0.22f, 0.54f).forEach { x ->
            listOf(0.22f, 0.54f).forEach { y ->
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * x, h * y),
                    size = Size(w * 0.24f, h * 0.24f),
                    cornerRadius = corner,
                    style = line,
                )
            }
        }
    }
}

@Composable
fun ListIcon(modifier: Modifier = Modifier.size(20.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        listOf(0.28f, 0.5f, 0.72f).forEach { y ->
            drawLine(tint, Offset(w * 0.22f, h * y), Offset(w * 0.78f, h * y), stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun BookmarkIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(w * 0.3f, h * 0.16f)
            lineTo(w * 0.7f, h * 0.16f)
            lineTo(w * 0.7f, h * 0.84f)
            lineTo(w * 0.5f, h * 0.67f)
            lineTo(w * 0.3f, h * 0.84f)
            close()
        }
        drawPath(path, tint, style = line)
    }
}

@Composable
fun DeleteIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        val line = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.28f, h * 0.30f),
            size = Size(w * 0.44f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = line,
        )
        drawLine(tint, Offset(w * 0.20f, h * 0.28f), Offset(w * 0.80f, h * 0.28f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.40f, h * 0.18f), Offset(w * 0.60f, h * 0.18f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.42f, h * 0.40f), Offset(w * 0.42f, h * 0.72f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.58f, h * 0.40f), Offset(w * 0.58f, h * 0.72f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun FileIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.26f, h * 0.14f),
            size = Size(w * 0.48f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.05f),
            style = line,
        )
        drawLine(tint, Offset(w * 0.37f, h * 0.36f), Offset(w * 0.63f, h * 0.36f), 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.37f, h * 0.52f), Offset(w * 0.63f, h * 0.52f), 1.5.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
fun SunIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.5f, h * 0.5f), style = line)
        listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { angle ->
            val rad = Math.toRadians(angle.toDouble())
            val startX = w * 0.5f + (w * 0.27f) * kotlin.math.cos(rad).toFloat()
            val startY = h * 0.5f + (h * 0.27f) * kotlin.math.sin(rad).toFloat()
            val endX = w * 0.5f + (w * 0.37f) * kotlin.math.cos(rad).toFloat()
            val endY = h * 0.5f + (h * 0.37f) * kotlin.math.sin(rad).toFloat()
            drawLine(tint, Offset(startX, startY), Offset(endX, endY), 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** 智能排版：首行缩进 + 段落分行。 */
@Composable
fun SmartFormatIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawLine(tint, Offset(w * 0.32f, h * 0.24f), Offset(w * 0.82f, h * 0.24f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.18f, h * 0.44f), Offset(w * 0.82f, h * 0.44f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.18f, h * 0.64f), Offset(w * 0.70f, h * 0.64f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.18f, h * 0.84f), Offset(w * 0.60f, h * 0.84f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun TextLinesIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawLine(tint, Offset(w * 0.22f, h * 0.3f), Offset(w * 0.78f, h * 0.3f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.7f), Offset(w * 0.64f, h * 0.7f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun FolderIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.32f)
            lineTo(w * 0.18f, h * 0.76f)
            lineTo(w * 0.82f, h * 0.76f)
            lineTo(w * 0.82f, h * 0.38f)
            lineTo(w * 0.48f, h * 0.38f)
            lineTo(w * 0.4f, h * 0.3f)
            lineTo(w * 0.18f, h * 0.3f)
            close()
        }
        drawPath(path, tint, style = line)
    }
}

/** 写作导出：文件/托盘加向上箭头，避免与导入/下载混淆。 */
@Composable
fun ExportIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        val line = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.2f, h * 0.56f),
            size = Size(w * 0.6f, h * 0.26f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = line,
        )
        drawLine(tint, Offset(w * 0.5f, h * 0.56f), Offset(w * 0.5f, h * 0.2f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.35f, h * 0.36f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.65f, h * 0.36f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun BackupIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.18f, h * 0.2f),
            size = Size(w * 0.64f, h * 0.46f),
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        drawLine(tint, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.5f, h * 0.3f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.37f, h * 0.42f), Offset(w * 0.5f, h * 0.29f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.63f, h * 0.42f), Offset(w * 0.5f, h * 0.29f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.22f, h * 0.82f), Offset(w * 0.78f, h * 0.78f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun InfoIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.3f, center = Offset(w * 0.5f, h * 0.5f), style = line)
        drawCircle(tint, radius = w * 0.035f, center = Offset(w * 0.5f, h * 0.34f))
        drawLine(tint, Offset(w * 0.5f, h * 0.48f), Offset(w * 0.5f, h * 0.68f), 1.7.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** 阅读器顶部使用的简洁左箭头。 */
@Composable
fun BackArrowIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(tint, Offset(w * 0.72f, h * 0.5f), Offset(w * 0.28f, h * 0.5f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.46f, h * 0.32f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.46f, h * 0.68f), stroke, cap = StrokeCap.Round)
    }
}

/** 写作快捷栏的图片入口占位图标。 */
@Composable
fun ImageIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.16f, h * 0.22f),
            size = Size(w * 0.68f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.08f),
            style = line,
        )
        drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.34f, h * 0.4f))
        val path = Path().apply {
            moveTo(w * 0.23f, h * 0.66f)
            lineTo(w * 0.42f, h * 0.48f)
            lineTo(w * 0.56f, h * 0.62f)
            lineTo(w * 0.64f, h * 0.55f)
            lineTo(w * 0.77f, h * 0.68f)
        }
        drawPath(path, tint, style = line)
    }
}

/** 撤销箭头图标：来自素材图裁剪的透明 PNG，尾部弯曲、尖端朝左。 */
@Composable
fun UndoIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Image(
        painter = painterResource(R.drawable.ic_undo),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}

/** 重做箭头图标：来自素材图裁剪的透明 PNG，尾部弯曲、尖端朝右。 */
@Composable
fun RedoIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Image(
        painter = painterResource(R.drawable.ic_redo),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun BoldIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.1.dp.toPx()
        drawLine(tint, Offset(w * 0.34f, h * 0.22f), Offset(w * 0.34f, h * 0.78f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.24f), Offset(w * 0.59f, h * 0.24f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.59f, h * 0.24f), Offset(w * 0.68f, h * 0.34f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.68f, h * 0.34f), Offset(w * 0.59f, h * 0.48f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.48f), Offset(w * 0.64f, h * 0.48f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.64f, h * 0.48f), Offset(w * 0.72f, h * 0.62f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.72f, h * 0.62f), Offset(w * 0.62f, h * 0.76f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.62f, h * 0.76f), Offset(w * 0.34f, h * 0.76f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun ItalicIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, Offset(w * 0.42f, h * 0.24f), Offset(w * 0.66f, h * 0.24f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.76f), Offset(w * 0.58f, h * 0.76f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.57f, h * 0.24f), Offset(w * 0.43f, h * 0.76f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun HeadingIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(tint, Offset(w * 0.27f, h * 0.28f), Offset(w * 0.27f, h * 0.72f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.27f, h * 0.5f), Offset(w * 0.56f, h * 0.5f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.56f, h * 0.28f), Offset(w * 0.56f, h * 0.72f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.68f, h * 0.28f), Offset(w * 0.82f, h * 0.28f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.68f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun QuoteIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        drawLine(tint, Offset(w * 0.3f, h * 0.3f), Offset(w * 0.3f, h * 0.7f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.3f, h * 0.3f), Offset(w * 0.5f, h * 0.3f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.3f), Offset(w * 0.5f, h * 0.48f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.48f), Offset(w * 0.3f, h * 0.48f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.58f, h * 0.3f), Offset(w * 0.58f, h * 0.7f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.58f, h * 0.3f), Offset(w * 0.78f, h * 0.3f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.3f), Offset(w * 0.78f, h * 0.48f), stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.78f, h * 0.48f), Offset(w * 0.58f, h * 0.48f), stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun GearIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.7.dp.toPx()
        val center = Offset(w * 0.5f, h * 0.5f)
        val radius = w * 0.22f
        drawCircle(tint, radius = radius, center = center, style = Stroke(stroke))
        drawCircle(tint, radius = w * 0.075f, center = center, style = Stroke(stroke))
        repeat(8) { index ->
            val angle = Math.toRadians((index * 45).toDouble())
            val startRadius = radius + w * 0.04f
            val endRadius = radius + w * 0.13f
            drawLine(
                color = tint,
                start = Offset(
                    center.x + (startRadius * kotlin.math.cos(angle)).toFloat(),
                    center.y + (startRadius * kotlin.math.sin(angle)).toFloat(),
                ),
                end = Offset(
                    center.x + (endRadius * kotlin.math.cos(angle)).toFloat(),
                    center.y + (endRadius * kotlin.math.sin(angle)).toFloat(),
                ),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 阅读设置入口的滑杆图标。 */
@Composable
fun SettingsIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawLine(tint, Offset(w * 0.22f, h * 0.3f), Offset(w * 0.78f, h * 0.3f), stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.42f, h * 0.3f), style = Stroke(stroke))
        drawLine(tint, Offset(w * 0.22f, h * 0.7f), Offset(w * 0.78f, h * 0.7f), stroke, cap = StrokeCap.Round)
        drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.62f, h * 0.7f), style = Stroke(stroke))
    }
}
