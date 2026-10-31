package com.example.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.config.PatternType
import com.example.config.SnakeSkin
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws a cute, glossy, smiling cartoon snake wiggling smoothly (used on Home & Skins screens).
 */
@Composable
fun SnakePreviewCanvas(
    skin: SnakeSkin,
    modifier: Modifier = Modifier,
    segmentCount: Int = 14,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "cute_snake_wiggle")
    val phaseAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_val"
    )
    val phase = if (animated) phaseAnim else 0.9f

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val baseR = (minOf(w, h) * 0.19f).coerceIn(9f, 28f)
        val outline = Color(0xFF232933)

        // Tail on right, big smiling cartoon head on left (matching reference preview pose!)
        val tailX = w * 0.80f
        val headX = w * 0.25f
        val stepX = (tailX - headX) / (segmentCount - 1).coerceAtLeast(1)

        // 1. Dark Cartoon Outline Pass
        for (i in 0 until segmentCount) {
            val idxFromTail = i
            val x = tailX - idxFromTail * stepX
            val curveDip = sin((idxFromTail.toFloat() / segmentCount) * PI.toFloat()) * (h * 0.18f)
            val wiggle = sin(phase + idxFromTail * 0.45f) * (h * 0.06f)
            val y = h * 0.50f + curveDip + wiggle
            val taper = if (idxFromTail < 3) 0.68f + idxFromTail * 0.1f else 1f
            val r = baseR * taper
            drawCircle(outline, radius = r + 3.5f, center = Offset(x, y))
        }

        // 2. Glossy Colored Segments Pass (tail -> neck)
        for (i in 0 until segmentCount - 1) {
            val x = tailX - i * stepX
            val curveDip = sin((i.toFloat() / segmentCount) * PI.toFloat()) * (h * 0.18f)
            val wiggle = sin(phase + i * 0.45f) * (h * 0.06f)
            val y = h * 0.50f + curveDip + wiggle
            val taper = if (i < 3) 0.68f + i * 0.1f else 1f
            val r = baseR * taper

            val segColor = when (skin.pattern) {
                PatternType.RAINBOW_BANDS, PatternType.STRIPED -> skin.colors[(i / 2) % skin.colors.size]
                PatternType.SOLID -> skin.colors[0]
                PatternType.PEARL -> if (i % 2 == 0) skin.colors[0] else skin.colors[1 % skin.colors.size]
                PatternType.GRADIENT -> {
                    val t = i.toFloat() / segmentCount.coerceAtLeast(1)
                    val c0 = skin.colors[0]
                    val c1 = skin.colors[1 % skin.colors.size]
                    Color(
                        red = c0.red + (c1.red - c0.red) * t,
                        green = c0.green + (c1.green - c0.green) * t,
                        blue = c0.blue + (c1.blue - c0.blue) * t,
                        alpha = 1f
                    )
                }
            }
            drawCircle(segColor, radius = r, center = Offset(x, y))
            drawCircle(
                Color.White.copy(alpha = 0.42f),
                radius = r * 0.32f,
                center = Offset(x - r * 0.2f, y - r * 0.25f)
            )
        }

        // 3. Cute Big Smiling Cartoon Head facing camera
        val hx = headX
        val hy = h * 0.44f + sin(phase + segmentCount * 0.45f) * (h * 0.05f)
        val headR = baseR * 1.42f
        drawCircle(outline, radius = headR + 4f, center = Offset(hx, hy))
        drawCircle(skin.headColor, radius = headR, center = Offset(hx, hy))
        drawCircle(
            Color.White.copy(alpha = 0.45f),
            radius = headR * 0.32f,
            center = Offset(hx - headR * 0.32f, hy - headR * 0.34f)
        )

        // Big shiny eyes
        val eyeOffsetX = headR * 0.34f
        val eyeY = hy - headR * 0.08f
        val eyeR = headR * 0.29f
        drawCircle(outline, eyeR + 2f, Offset(hx - eyeOffsetX, eyeY))
        drawCircle(outline, eyeR + 2f, Offset(hx + eyeOffsetX, eyeY))
        drawCircle(Color.White, eyeR, Offset(hx - eyeOffsetX, eyeY))
        drawCircle(Color.White, eyeR, Offset(hx + eyeOffsetX, eyeY))
        drawCircle(Color(0xFF1D222B), eyeR * 0.56f, Offset(hx - eyeOffsetX, eyeY + 1f))
        drawCircle(Color(0xFF1D222B), eyeR * 0.56f, Offset(hx + eyeOffsetX, eyeY + 1f))
        drawCircle(Color.White, eyeR * 0.22f, Offset(hx - eyeOffsetX - 2f, eyeY - 3f))
        drawCircle(Color.White, eyeR * 0.22f, Offset(hx + eyeOffsetX - 2f, eyeY - 3f))

        // Rosy pink cheeks
        drawCircle(Color(0xFFFF7597).copy(alpha = 0.72f), headR * 0.16f, Offset(hx - headR * 0.56f, hy + headR * 0.16f))
        drawCircle(Color(0xFFFF7597).copy(alpha = 0.72f), headR * 0.16f, Offset(hx + headR * 0.56f, hy + headR * 0.16f))

        // Happy open cartoon mouth + pink tongue
        val mouthPath = Path().apply {
            moveTo(hx - headR * 0.34f, hy + headR * 0.24f)
            quadraticTo(hx, hy + headR * 0.72f, hx + headR * 0.34f, hy + headR * 0.24f)
            close()
        }
        drawPath(mouthPath, Color(0xFF8C1D2D))
        drawPath(mouthPath, outline, style = Stroke(width = 2.5f))
        drawOval(
            color = Color(0xFFFF7597),
            topLeft = Offset(hx - headR * 0.18f, hy + headR * 0.38f),
            size = Size(headR * 0.36f, headR * 0.18f)
        )
    }
}
