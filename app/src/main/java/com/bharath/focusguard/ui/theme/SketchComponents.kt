package com.bharath.focusguard.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

// ══════════════════════════════════════════════════════════════════
//  SKETCH MODIFIER — wobbly hand-drawn border drawn directly on canvas
// ══════════════════════════════════════════════════════════════════

/**
 * Draws a hand-drawn sketchy rectangular border around the composable.
 * Uses two staggered rounded-rect strokes with sine-wave perturbation to
 * simulate a pencil outline — same technique as the HTML SVG wobble filter.
 */
fun Modifier.sketchBorder(
    color: Color = FgColor.Ink,
    strokeWidth: Dp = 2.dp,
    cornerRadius: Dp = 18.dp,
    alpha: Float = 1f,
): Modifier = drawBehind {
    val sw = strokeWidth.toPx()
    val cr = cornerRadius.toPx()
    // Pass 1: main outline (slightly inset)
    drawSketchRect(color.copy(alpha = alpha), sw, cr, inset = sw / 2f)
    // Pass 2: lighter double-stroke offset for hand-drawn depth
    drawSketchRect(color.copy(alpha = alpha * 0.55f), sw * 0.65f, cr - 2f, inset = sw * 1.5f)
}

private fun DrawScope.drawSketchRect(
    color: Color,
    strokeWidth: Float,
    cornerRadius: Float,
    inset: Float,
) {
    val cr = cornerRadius.coerceAtLeast(1f)
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(cr, cr),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

// ══════════════════════════════════════════════════════════════════
//  DOT-GRID BACKGROUND (body canvas background)
// ══════════════════════════════════════════════════════════════════

fun Modifier.dotGridBackground(
    dotColor: Color = FgColor.Ink.copy(alpha = 0.18f),
    spacing: Dp = 18.dp,
    radius: Dp = 1.1.dp,
): Modifier = drawBehind {
    val sp = spacing.toPx()
    val r  = radius.toPx()
    val cols = (size.width / sp).toInt() + 2
    val rows = (size.height / sp).toInt() + 2
    for (row in 0..rows) {
        for (col in 0..cols) {
            drawCircle(dotColor, r, Offset(col * sp, row * sp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  DIAGONAL HATCH FILL (for buttons, thermometer, etc.)
// ══════════════════════════════════════════════════════════════════

fun DrawScope.drawHatch(
    color1: Color,
    color2: Color = color1.copy(alpha = 0.75f),
    stripeWidth: Float = 8f,
) {
    // Fill background with color1
    drawRect(color1)
    // Draw diagonal stripes in color2
    val diag = size.width + size.height
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color2,
            Offset(x, 0f),
            Offset(x + size.height, size.height),
            strokeWidth = stripeWidth * 0.5f,
        )
        x += stripeWidth
    }
}

// ══════════════════════════════════════════════════════════════════
//  SKETCH CARD — paper card with sketchBorder + optional rotation
// ══════════════════════════════════════════════════════════════════

@Composable
fun SketchCard(
    modifier: Modifier = Modifier,
    bgColor: Color = FgColor.Paper,
    borderColor: Color = FgColor.Ink,
    cornerRadius: Dp = 18.dp,
    rotateDeg: Float = 0f,
    elevation: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .rotate(rotateDeg)
            .background(bgColor, RoundedCornerShape(cornerRadius))
            .sketchBorder(borderColor, cornerRadius = cornerRadius)
            .shadow(elevation, RoundedCornerShape(cornerRadius), ambientColor = FgColor.Ink.copy(0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

// Shadow extension (simple box-shadow equivalent)
private fun Modifier.shadow(elevation: Dp, shape: RoundedCornerShape, ambientColor: Color): Modifier =
    this.drawBehind {
        val e = elevation.toPx()
        val cr = 18.dp.toPx() // fixed corner radius for shadow
        drawRoundRect(
            color = ambientColor,
            topLeft = Offset(e * 0.6f, e),
            size = size,
            cornerRadius = CornerRadius(cr, cr),
        )
    }

// ══════════════════════════════════════════════════════════════════
//  WASHI TAPE decoration
// ══════════════════════════════════════════════════════════════════

@Composable
fun WashiTape(
    modifier: Modifier = Modifier,
    width: Dp = 80.dp,
    height: Dp = 20.dp,
    rotateDeg: Float = 1.5f,
    color1: Color = FgColor.Peach,
    color2: Color = FgColor.Pink,
) {
    Canvas(
        modifier = modifier
            .width(width)
            .height(height)
            .rotate(rotateDeg),
    ) {
        drawHatch(color1, color2, stripeWidth = 10f)
        // dashed side borders
        val dashLen = 6f
        val gapLen  = 4f
        drawLine(FgColor.Ink.copy(0.25f), Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen)))
        drawLine(FgColor.Ink.copy(0.25f), Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLen, gapLen)))
    }
}

// ══════════════════════════════════════════════════════════════════
//  SKETCH BUTTON — hatched fill, hand-drawn border, bouncy press
// ══════════════════════════════════════════════════════════════════

@Composable
fun SketchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "",
    fillColor: Color = FgColor.Ink,
    textColor: Color = FgColor.Paper,
    hatched: Boolean = false,
    hatchColor1: Color = fillColor,
    hatchColor2: Color = fillColor.copy(alpha = 0.72f),
    cornerRadius: Dp = 12.dp,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit = {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) textColor else FgColor.Slate,
            fontWeight = FontWeight.Bold,
        )
    },
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.96f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btnScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                color = if (!hatched) (if (enabled) fillColor else FgColor.Line) else Color.Transparent,
                shape = RoundedCornerShape(cornerRadius),
            )
            .drawBehind {
                if (hatched && enabled) {
                    val cr = cornerRadius.toPx()
                    val path = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(
                            0f, 0f, size.width, size.height, cr, cr
                        ))
                    }
                    clipPath(path) { drawHatch(hatchColor1, hatchColor2, stripeWidth = 10f) }
                }
            }
            .sketchBorder(if (enabled) FgColor.Ink else FgColor.Line, cornerRadius = cornerRadius)
            .pointerInput(enabled) {
                detectTapGestures(onPress = {
                    if (enabled) {
                        tryAwaitRelease()
                        onClick()
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// ══════════════════════════════════════════════════════════════════
//  SKETCH CHECKBOX — hand-drawn square with animated checkmark
// ══════════════════════════════════════════════════════════════════

@Composable
fun SketchCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    checkColor: Color = FgColor.Red,
    boxColor: Color = FgColor.Ink,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "checkProgress"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .pointerInput(Unit) { detectTapGestures { onCheckedChange(!checked) } },
    ) {
        val s = this.size.width
        val r = s * 0.18f
        // Draw box
        drawRoundRect(
            color = FgColor.Paper,
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawRoundRect(
            color = boxColor,
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Draw animated checkmark path: L-shape from bottom-left corner
        if (progress > 0f) {
            val midX = s * 0.3f; val midY = s * 0.65f
            val endX = s * 0.85f; val endY = s * 0.2f
            val startX = s * 0.12f; val startY = s * 0.5f

            val path = Path()
            if (progress <= 0.5f) {
                // First segment: start → mid
                val t = progress * 2f
                path.moveTo(startX, startY)
                path.lineTo(lerp(startX, midX, t), lerp(startY, midY, t))
            } else {
                // Both segments
                val t = (progress - 0.5f) * 2f
                path.moveTo(startX, startY)
                path.lineTo(midX, midY)
                path.lineTo(lerp(midX, endX, t), lerp(midY, endY, t))
            }
            drawPath(path, checkColor, style = Stroke(width = s * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

// ══════════════════════════════════════════════════════════════════
//  RING PROGRESS (donut chart for usage)
// ══════════════════════════════════════════════════════════════════

@Composable
fun RingProgress(
    progress: Float,          // 0f..1f
    modifier: Modifier = Modifier,
    trackColor: Color = FgColor.Line,
    fillColor: Color = FgColor.Red,
    strokeWidth: Dp = 10.dp,
    label: @Composable () -> Unit = {},
) {
    val animProg by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "ringProg"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = strokeWidth.toPx()
            val diameter = minOf(size.width, size.height) - sw
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val sz = Size(diameter, diameter)

            // Track ring
            drawArc(trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = sz,
                style = Stroke(sw, cap = StrokeCap.Round))
            // Fill arc
            drawArc(fillColor, startAngle = -110f, sweepAngle = 360f * animProg, useCenter = false,
                topLeft = topLeft, size = sz,
                style = Stroke(sw * 1.4f, cap = StrokeCap.Round))
        }
        label()
    }
}

// ══════════════════════════════════════════════════════════════════
//  TALLY MARKS (5-strike tally)
// ══════════════════════════════════════════════════════════════════

@Composable
fun TallyMarks(
    count: Int,          // 0..max
    modifier: Modifier = Modifier,
    inkColor: Color = FgColor.Ink,
    slashColor: Color = FgColor.Red,
    groupSize: Int = 5,
) {
    val groups = count / groupSize
    val rem    = count % groupSize

    Canvas(modifier = modifier) {
        val lineH   = size.height * 0.75f
        val topY    = (size.height - lineH) / 2f
        val botY    = topY + lineH
        val spacing = size.width / ((groups + 1).coerceAtLeast(1) * groupSize.toFloat())
        val slashW  = spacing * 0.75f
        val lineW   = size.width * 0.045f

        var x = spacing * 0.6f
        repeat(groups) {
            // 4 vertical lines
            repeat(4) { i ->
                drawLine(inkColor, Offset(x + i * spacing, topY), Offset(x + i * spacing, botY), strokeWidth = lineW, cap = StrokeCap.Round)
            }
            // diagonal slash through the 4
            drawLine(slashColor, Offset(x - spacing * 0.3f, botY + size.height * 0.05f),
                Offset(x + 4 * spacing, topY - size.height * 0.05f), strokeWidth = lineW * 1.1f, cap = StrokeCap.Round)
            x += 5 * spacing
        }
        repeat(rem) { i ->
            drawLine(inkColor, Offset(x + i * spacing, topY), Offset(x + i * spacing, botY), strokeWidth = lineW, cap = StrokeCap.Round)
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  META LABEL (small all-caps category label)
// ══════════════════════════════════════════════════════════════════

@Composable
fun MetaLabel(text: String, modifier: Modifier = Modifier, color: Color = FgColor.Slate) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
        color = color,
    )
}

// ══════════════════════════════════════════════════════════════════
//  TIDE LINE (progress bar)
// ══════════════════════════════════════════════════════════════════

@Composable
fun TideLine(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = FgColor.Ink,
    trackColor: Color = FgColor.Line,
    height: Dp = 6.dp,
) {
    val animProg by animateFloatAsState(progress.coerceIn(0f, 1f),
        tween(600, easing = FastOutSlowInEasing), label = "tide")
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        // Track
        drawRoundRect(trackColor, cornerRadius = CornerRadius(size.height / 2))
        // Fill
        if (animProg > 0f) {
            drawRoundRect(color, size = Size(size.width * animProg, size.height),
                cornerRadius = CornerRadius(size.height / 2))
        }
    }
}

// ══════════════════════════════════════════════════════════════════
//  PILL TAG (small coloured label chip)
// ══════════════════════════════════════════════════════════════════

@Composable
fun PillTag(text: String, bgColor: Color = FgColor.Yellow, textColor: Color = FgColor.Ink, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(50))
            .sketchBorder(FgColor.Ink.copy(0.4f), strokeWidth = 1.dp, cornerRadius = 50.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = textColor)
    }
}
