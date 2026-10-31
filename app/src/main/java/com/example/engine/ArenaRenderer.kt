package com.example.engine

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.example.config.PatternType
import com.example.services.GraphicsQuality
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders the scrolling dark woven/tiled arena floor, glossy cartoon striped snakes
 * with cute big eyes & smiling faces, original vector Apple/Cupcake/Croissant treats,
 * particles, floating "+score" popups, and the stone-ringed minimap for both Offline & Online sessions!
 */
object ArenaRenderer {
    private val path = Path()

    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(5f, 0f, 2f, android.graphics.Color.BLACK)
    }

    private val popupPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(6f, 0f, 3f, android.graphics.Color.BLACK)
    }

    fun DrawScope.renderArena(source: ArenaRenderSource) {
        val vw = size.width
        val vh = size.height

        // Base dark woven arena backdrop
        drawRect(color = Color(0xFF151B26))

        val sx = if (source.shakeIntensity > 0.2f) (Random.nextFloat() - 0.5f) * source.shakeIntensity * 2f else 0f
        val sy = if (source.shakeIntensity > 0.2f) (Random.nextFloat() - 0.5f) * source.shakeIntensity * 2f else 0f

        val zoom = source.cameraZoom
        val camX = source.cameraX
        val camY = source.cameraY
        val halfW = (vw * 0.5f) / zoom
        val halfH = (vh * 0.5f) / zoom
        val quality = source.effectiveQuality

        withTransform({
            translate(vw * 0.5f + sx, vh * 0.5f + sy)
            scale(zoom, zoom, Offset.Zero)
            translate(-camX, -camY)
        }) {
            // 1. Dark Woven / Tiled Floor
            drawWovenTiledFloor(
                minX = camX - halfW - 110f,
                maxX = camX + halfW + 110f,
                minY = camY - halfH - 110f,
                maxY = camY + halfH + 110f,
                arenaRadius = source.arenaRadius
            )

            // 2. Visible Arena Border (touching = death!)
            drawArenaBorder(arenaRadius = source.arenaRadius)

            // 3. Glowing Orbs & Vector Desserts (Apple, Cupcake, Croissant, Coin)
            for (food in source.renderFoods) {
                if (!food.active) continue
                if (abs(food.x - camX) > halfW + 60f || abs(food.y - camY) > halfH + 60f) continue
                drawCartoonFood(food, source.elapsedSec, quality)
            }

            // 4. Particles
            for (p in source.renderParticles) {
                if (!p.active) continue
                if (abs(p.x - camX) > halfW + 40f || abs(p.y - camY) > halfH + 40f) continue
                val a = (p.life / p.maxLife).coerceIn(0f, 1f)
                drawCircle(p.color.copy(alpha = a), radius = p.radius * a + 2f, center = Offset(p.x, p.y))
            }

            // 5. Glossy Cartoon Snakes (rivals first, local player on top)
            val snakes = source.renderSnakes
            var localSnake: SnakeEntity? = null
            for (s in snakes) {
                if (!s.alive) continue
                if (s.isPlayer) {
                    localSnake = s
                } else {
                    drawGlossyCartoonSnake(s, camX, camY, halfW, halfH, quality)
                }
            }
            localSnake?.let {
                drawGlossyCartoonSnake(it, camX, camY, halfW, halfH, quality)
            }

            // 6. Floating "+score" text
            for (pop in source.renderPopups) {
                if (!pop.active) continue
                val a = (pop.life / pop.maxLife).coerceIn(0f, 1f)
                popupPaint.color = android.graphics.Color.argb(
                    (a * 255).toInt(),
                    (pop.color.red * 255).toInt(),
                    (pop.color.green * 255).toInt(),
                    (pop.color.blue * 255).toInt()
                )
                drawContext.canvas.nativeCanvas.drawText(pop.text, pop.x, pop.y, popupPaint)
            }
        }
    }

    private fun DrawScope.drawWovenTiledFloor(
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        arenaRadius: Float
    ) {
        val tile = 92f
        val sx = floor(minX / tile) * tile
        val sy = floor(minY / tile) * tile
        val rSq = arenaRadius * arenaRadius

        var y = sy
        var iy = (sy / tile).toInt()
        while (y <= maxY) {
            var x = sx
            var ix = (sx / tile).toInt()
            while (x <= maxX) {
                if (x * x + y * y < rSq) {
                    val isEven = ((ix + iy) and 1) == 0
                    val fill = if (isEven) Color(0xFF1D2534) else Color(0xFF18202D)
                    drawRoundRect(
                        color = fill,
                        topLeft = Offset(x + 4f, y + 4f),
                        size = Size(tile - 8f, tile - 8f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )
                    if (isEven) {
                        drawLine(
                            color = Color(0xFF263145),
                            start = Offset(x + 18f, y + tile * 0.5f),
                            end = Offset(x + tile - 18f, y + tile * 0.5f),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawLine(
                            color = Color(0xFF263145),
                            start = Offset(x + tile * 0.5f, y + 18f),
                            end = Offset(x + tile * 0.5f, y + tile - 18f),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                x += tile
                ix++
            }
            y += tile
            iy++
        }
    }

    private fun DrawScope.drawArenaBorder(arenaRadius: Float) {
        drawCircle(
            color = Color(0x55FF5252),
            radius = arenaRadius + 8f,
            center = Offset.Zero,
            style = Stroke(width = 24f)
        )
        drawCircle(
            color = Color(0xFFFF5252),
            radius = arenaRadius,
            center = Offset.Zero,
            style = Stroke(width = 9f)
        )
        drawCircle(
            color = Color(0xFFFFE066),
            radius = arenaRadius - 5f,
            center = Offset.Zero,
            style = Stroke(width = 3f)
        )
    }

    private fun DrawScope.drawCartoonFood(
        food: FoodItem,
        timeSec: Float,
        quality: GraphicsQuality
    ) {
        val pulse = 1f + 0.10f * sin(food.phase + timeSec * 2.2f)
        val cx = food.x
        val cy = food.y
        val r = food.radius * pulse
        val outline = Color(0xFF222831)

        if (quality != GraphicsQuality.LOW) {
            drawCircle(
                color = food.color.copy(alpha = 0.25f),
                radius = r * 1.75f,
                center = Offset(cx, cy)
            )
        }

        when (food.kind) {
            FoodKind.BOOST_PELLET, FoodKind.GLOW_ORB, FoodKind.DEATH_DROP -> {
                drawCircle(outline, radius = r + 2.5f, center = Offset(cx, cy))
                drawCircle(food.color, radius = r, center = Offset(cx, cy))
                drawCircle(Color.White.copy(alpha = 0.78f), radius = r * 0.36f, center = Offset(cx - r * 0.25f, cy - r * 0.25f))
            }
            FoodKind.APPLE -> {
                drawCircle(outline, radius = r + 3f, center = Offset(cx, cy + 2f))
                drawCircle(Color(0xFFFF3B3B), radius = r, center = Offset(cx, cy + 2f))
                drawLine(outline, Offset(cx, cy - r * 0.65f), Offset(cx + 2f, cy - r * 1.25f), strokeWidth = 5f, cap = StrokeCap.Round)
                drawOval(Color(0xFF56E84D), Offset(cx + 2f, cy - r * 1.25f), Size(r * 0.75f, r * 0.45f))
                drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.24f, center = Offset(cx - r * 0.35f, cy - r * 0.15f))
            }
            FoodKind.CUPCAKE -> {
                path.reset()
                path.moveTo(cx - r * 0.78f, cy + r * 0.08f)
                path.lineTo(cx + r * 0.78f, cy + r * 0.08f)
                path.lineTo(cx + r * 0.55f, cy + r * 0.96f)
                path.lineTo(cx - r * 0.55f, cy + r * 0.96f)
                path.close()
                drawPath(path, Color(0xFFE09F5A))
                drawPath(path, outline, style = Stroke(width = 3f))
                drawCircle(outline, radius = r * 0.84f + 2.5f, center = Offset(cx, cy - r * 0.14f))
                drawCircle(Color(0xFFFF85C0), radius = r * 0.84f, center = Offset(cx, cy - r * 0.14f))
                drawCircle(outline, radius = r * 0.3f + 2f, center = Offset(cx, cy - r * 0.88f))
                drawCircle(Color(0xFFFF2A4B), radius = r * 0.3f, center = Offset(cx, cy - r * 0.88f))
            }
            FoodKind.CROISSANT -> {
                drawArc(
                    color = outline,
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r * 0.8f),
                    size = Size(r * 2f, r * 1.6f),
                    style = Stroke(width = r * 0.85f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFFF4A236),
                    startAngle = 195f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r * 0.8f),
                    size = Size(r * 2f, r * 1.6f),
                    style = Stroke(width = r * 0.65f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFFFFD97D),
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.85f, cy - r * 0.65f),
                    size = Size(r * 1.7f, r * 1.3f),
                    style = Stroke(width = r * 0.25f, cap = StrokeCap.Round)
                )
            }
            FoodKind.COIN -> {
                drawCircle(outline, radius = r + 3f, center = Offset(cx, cy))
                drawCircle(Color(0xFFFFD83D), radius = r, center = Offset(cx, cy))
                drawCircle(Color(0xFFFFF3B0), radius = r * 0.72f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
            }
        }
    }

    private fun DrawScope.drawGlossyCartoonSnake(
        snake: SnakeEntity,
        camX: Float,
        camY: Float,
        halfW: Float,
        halfH: Float,
        quality: GraphicsQuality
    ) {
        val segs = snake.segments
        if (segs.isEmpty()) return
        val baseR = snake.bodyRadius
        val colors = snake.skin.colors
        val outline = Color(0xFF202630)

        // 1. Dark Cartoon Outline Pass
        for (i in segs.lastIndex downTo 0) {
            val pt = segs[i]
            if (abs(pt.x - camX) > halfW + baseR * 3f || abs(pt.y - camY) > halfH + baseR * 3f) continue
            val taper = if (i > segs.size - 6) ((segs.size - i) / 6f).coerceIn(0.48f, 1f) else 1f
            val r = baseR * taper
            drawCircle(color = outline, radius = r + 3.6f, center = Offset(pt.x, pt.y))
        }

        // 2. Glossy Colored Body Segments Pass (tail to head)
        for (i in segs.lastIndex downTo 1) {
            val pt = segs[i]
            if (abs(pt.x - camX) > halfW + baseR * 3f || abs(pt.y - camY) > halfH + baseR * 3f) continue
            val taper = if (i > segs.size - 6) ((segs.size - i) / 6f).coerceIn(0.48f, 1f) else 1f
            val r = baseR * taper

            val segColor = when (snake.skin.pattern) {
                PatternType.RAINBOW_BANDS, PatternType.STRIPED -> colors[(i / 3) % colors.size]
                PatternType.SOLID -> colors[0]
                PatternType.PEARL -> if (i % 2 == 0) colors[0] else colors[1 % colors.size]
                PatternType.GRADIENT -> {
                    val t = i.toFloat() / segs.size.coerceAtLeast(1)
                    lerpColor(colors[0], colors[1 % colors.size], t)
                }
            }

            if (snake.activelyBoosting && quality != GraphicsQuality.LOW && i % 2 == 0) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = r * 1.45f,
                    center = Offset(pt.x, pt.y)
                )
            }

            drawCircle(color = segColor, radius = r, center = Offset(pt.x, pt.y))

            if (quality != GraphicsQuality.LOW && i % 2 == 0) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.42f),
                    radius = r * 0.34f,
                    center = Offset(pt.x - r * 0.24f, pt.y - r * 0.24f)
                )
            }
        }

        // 3. Cute Smiling Cartoon Head
        val head = segs[0]
        if (abs(head.x - camX) <= halfW + 140f && abs(head.y - camY) <= halfH + 140f) {
            val headR = baseR * 1.18f
            drawCircle(color = outline, radius = headR + 4f, center = Offset(head.x, head.y))
            drawCircle(color = snake.skin.headColor, radius = headR, center = Offset(head.x, head.y))

            val angDeg = snake.angle * 180f / PI.toFloat()
            withTransform({
                translate(head.x, head.y)
                rotate(angDeg, Offset.Zero)
            }) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = headR * 0.34f,
                    center = Offset(-headR * 0.28f, -headR * 0.28f)
                )

                val eyeX = headR * 0.24f
                val eyeY = headR * 0.40f
                val eyeR = headR * 0.32f
                drawCircle(outline, eyeR + 2.2f, Offset(eyeX, -eyeY))
                drawCircle(outline, eyeR + 2.2f, Offset(eyeX, eyeY))
                drawCircle(Color.White, eyeR, Offset(eyeX, -eyeY))
                drawCircle(Color.White, eyeR, Offset(eyeX, eyeY))

                val pupilR = eyeR * 0.56f
                drawCircle(Color(0xFF1A1F29), pupilR, Offset(eyeX + eyeR * 0.2f, -eyeY))
                drawCircle(Color(0xFF1A1F29), pupilR, Offset(eyeX + eyeR * 0.2f, eyeY))
                drawCircle(Color.White, pupilR * 0.36f, Offset(eyeX + eyeR * 0.08f, -eyeY - pupilR * 0.25f))
                drawCircle(Color.White, pupilR * 0.36f, Offset(eyeX + eyeR * 0.08f, eyeY - pupilR * 0.25f))

                drawCircle(Color(0xFFFF7597).copy(alpha = 0.72f), headR * 0.19f, Offset(headR * 0.05f, -headR * 0.72f))
                drawCircle(Color(0xFFFF7597).copy(alpha = 0.72f), headR * 0.19f, Offset(headR * 0.05f, headR * 0.72f))

                drawArc(
                    color = Color(0xFF7F1D2B),
                    startAngle = -38f,
                    sweepAngle = 76f,
                    useCenter = false,
                    topLeft = Offset(headR * 0.18f, -headR * 0.34f),
                    size = Size(headR * 0.55f, headR * 0.68f),
                    style = Stroke(width = 3.2f, cap = StrokeCap.Round)
                )
            }

            if (!snake.isPlayer) {
                namePaint.color = android.graphics.Color.WHITE
                drawContext.canvas.nativeCanvas.drawText(snake.name, head.x, head.y - headR - 16f, namePaint)
            }
        }
    }

    fun DrawScope.renderMinimap(source: ArenaRenderSource) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.5f
        val mapR = minOf(w, h) * 0.44f
        val scale = mapR / source.arenaRadius.coerceAtLeast(1f)

        drawCircle(Color(0xEE1A2232), radius = mapR + 2f, center = Offset(cx, cy))
        drawCircle(
            color = Color(0xFFFF5252),
            radius = (source.arenaRadius * scale).coerceAtLeast(6f),
            center = Offset(cx, cy),
            style = Stroke(width = 2.0f)
        )

        for (dot in source.minimapDots) {
            val mx = cx + dot.x * scale
            val my = cy + dot.y * scale
            if (hypot(mx - cx, my - cy) > mapR) continue
            if (dot.isPlayer) {
                drawCircle(Color(0xFF56E84D), radius = 4.2f, center = Offset(mx, my))
                drawCircle(Color.White, radius = 5.6f, center = Offset(mx, my), style = Stroke(width = 1.4f))
            } else {
                drawCircle(Color(0xFFFFDE38), radius = 2.2f, center = Offset(mx, my))
            }
        }
    }

    private fun lerpColor(a: Color, b: Color, t: Float): Color {
        val f = t.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * f,
            green = a.green + (b.green - a.green) * f,
            blue = a.blue + (b.blue - a.blue) * f,
            alpha = 1f
        )
    }
}
