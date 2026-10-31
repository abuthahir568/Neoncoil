package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.config.AppLanguage
import com.example.config.I18n
import com.example.ui.theme.CartoonOutline
import com.example.ui.theme.GrassProgressGreen
import com.example.ui.theme.LeafBright
import com.example.ui.theme.LeafMid
import com.example.ui.theme.SunnyOrange
import kotlinx.coroutines.delay

/**
 * SCREEN 1: SPLASH / LOADING (Action-Poster Illustrated Scene).
 * - Bright sky blue with soft swirly clouds, floating grassy rock cliffs left & right, speed-line streaks.
 * - Top: Chunky 3D wooden/amber "NEON COIL" logo atop a cracked stone plaque with green leaves + orange "SNAKE ARENA".
 * - Center: One big, cute, colorful striped snake leaping toward camera with huge smiling face, shiny eyes & pink cheeks.
 * - Bottom: Three small angry enemy snakes chasing on a hill path + flying vector Apple, Cupcake & Croissant.
 * - Bottom: Cartoon loading bar + pulsing "TAP TO PLAY", shown ~2.5s before fading to Home Menu.
 */
@Composable
fun SplashScreen(
    lang: AppLanguage,
    onFinished: () -> Unit
) {
    var progress by remember { mutableFloatStateOf(0.05f) }

    LaunchedEffect(Unit) {
        val steps = 25
        for (i in 1..steps) {
            delay(100L) // 25 * 100ms = 2.5 seconds
            progress = i / steps.toFloat()
        }
        onFinished()
    }

    val pulseTransition = rememberInfiniteTransition(label = "splash_tap_pulse")
    val tapScale by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tap_scale"
    )
    val heroBob by pulseTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hero_bob"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (progress > 0.35f) onFinished()
            }
            .testTag("splash_screen")
    ) {
        // Full-Screen Original 2D Illustrated Action Poster Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // 1. Bright Sky Blue Gradient + Swirly Wind Ribbons + Speed Streaks
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF3AA6FF), Color(0xFF88D4FF), Color(0xFFC4ECFF))
                )
            )

            // Soft swirly wind ribbons
            val swirl1 = Path().apply {
                moveTo(-20f, h * 0.24f)
                cubicTo(w * 0.35f, h * 0.32f, w * 0.55f, h * 0.14f, w + 20f, h * 0.20f)
            }
            drawPath(swirl1, Color.White.copy(alpha = 0.28f), style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round))

            val swirl2 = Path().apply {
                moveTo(-20f, h * 0.56f)
                cubicTo(w * 0.28f, h * 0.50f, w * 0.65f, h * 0.66f, w + 20f, h * 0.58f)
            }
            drawPath(swirl2, Color.White.copy(alpha = 0.32f), style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round))

            // Fluffy center-right clouds
            drawPosterCloud(w * 0.56f, h * 0.33f, w * 0.09f)
            drawPosterCloud(w * 0.20f, h * 0.40f, w * 0.08f)
            drawPosterCloud(w * 0.80f, h * 0.50f, w * 0.09f)

            // Light diagonal speed-line streaks
            val streakColor = Color.White.copy(alpha = 0.65f)
            drawLine(streakColor, Offset(w * 0.96f, h * 0.22f), Offset(w * 0.78f, h * 0.36f), 3.dp.toPx(), StrokeCap.Round)
            drawLine(streakColor, Offset(w * 0.98f, h * 0.34f), Offset(w * 0.76f, h * 0.52f), 4.dp.toPx(), StrokeCap.Round)
            drawLine(streakColor, Offset(w * 0.24f, h * 0.38f), Offset(w * 0.03f, h * 0.49f), 3.dp.toPx(), StrokeCap.Round)
            drawLine(streakColor, Offset(w * 0.18f, h * 0.59f), Offset(w * 0.04f, h * 0.66f), 3.dp.toPx(), StrokeCap.Round)

            // 2. Floating Grassy Rock Cliffs on Left & Right
            drawFloatingCliff(
                topX = w * 0.08f,
                topY = h * 0.29f,
                cliffW = w * 0.24f,
                cliffH = h * 0.25f,
                isLeft = true
            )
            drawFloatingCliff(
                topX = w * 0.88f,
                topY = h * 0.36f,
                cliffW = w * 0.25f,
                cliffH = h * 0.25f,
                isLeft = false
            )

            // 3. Bottom Rolling Green Hill + Sandy Path + Grass Tufts
            val hillPath = Path().apply {
                moveTo(0f, h * 0.82f)
                quadraticTo(w * 0.35f, h * 0.75f, w * 0.68f, h * 0.81f)
                quadraticTo(w * 0.88f, h * 0.84f, w, h * 0.79f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(hillPath, LeafMid)
            drawPath(hillPath, CartoonOutline, style = Stroke(width = 4.dp.toPx()))

            val sandPath = Path().apply {
                moveTo(0f, h * 0.89f)
                quadraticTo(w * 0.45f, h * 0.93f, w, h * 0.84f)
                lineTo(w, h * 0.91f)
                quadraticTo(w * 0.45f, h * 0.99f, 0f, h * 0.96f)
                close()
            }
            drawPath(sandPath, Color(0xFFEBC278))
            drawPath(sandPath, CartoonOutline, style = Stroke(width = 3.dp.toPx()))

            // Little grass tufts
            drawGrassTuft(w * 0.12f, h * 0.84f)
            drawGrassTuft(w * 0.40f, h * 0.81f)
            drawGrassTuft(w * 0.66f, h * 0.85f)
            drawGrassTuft(w * 0.88f, h * 0.82f)

            // 4. Three Small Angry Enemy Snakes Chasing at the Bottom
            drawSmallAngrySnake(
                headX = w * 0.23f,
                headY = h * 0.83f,
                headR = w * 0.056f,
                primary = Color(0xFFFF7A33),
                stripe = Color(0xFFFFC299),
                tailDirRight = true
            )
            drawSmallAngrySnake(
                headX = w * 0.51f,
                headY = h * 0.87f,
                headR = w * 0.050f,
                primary = Color(0xFF7B5CFF),
                stripe = Color(0xFFC4B5FF),
                tailDirRight = false
            )
            drawSmallAngrySnake(
                headX = w * 0.78f,
                headY = h * 0.82f,
                headR = w * 0.056f,
                primary = Color(0xFF14B87A),
                stripe = Color(0xFF8CE8C2),
                tailDirRight = false
            )

            // 5. Center Hero: Big Cute Colorful Striped Snake Leaping Toward Camera!
            drawHeroLeapingSnake(w = w, h = h, bobOffset = heroBob)

            // 6. Flying Vector Treats (Apple, Cupcake, Croissant & Glowing Orbs)
            drawPosterApple(cx = w * 0.16f, cy = h * 0.45f, r = w * 0.042f)
            drawPosterApple(cx = w * 0.81f, cy = h * 0.66f, r = w * 0.028f)
            drawPosterCupcake(cx = w * 0.83f, cy = h * 0.45f, r = w * 0.046f)
            drawPosterCroissant(cx = w * 0.20f, cy = h * 0.64f, r = w * 0.048f)

            // Cute outlined candy orbs
            drawPosterOrb(w * 0.11f, h * 0.16f, 7.dp.toPx(), Color(0xFFFF85C0))
            drawPosterOrb(w * 0.89f, h * 0.16f, 7.dp.toPx(), Color(0xFFA87BFF))
            drawPosterOrb(w * 0.09f, h * 0.55f, 8.dp.toPx(), Color(0xFF38D9FF))
            drawPosterOrb(w * 0.70f, h * 0.39f, 6.dp.toPx(), Color(0xFF56E84D))
        }

        // Top Logo + Bottom Loading Bar Overlay (safe-area + >=12.dp padding on all edges)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Chunky 3D Wooden/Amber "NEON COIL" + Stone Plaque "SNAKE ARENA" with Green Leaves
            PosterLogoHeader()

            // Bottom Pulsing "TAP TO PLAY" + Cartoon Progress Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CartoonOutlinedText(
                    text = I18n.t("tap_to_play", lang),
                    fontSize = 30.sp,
                    strokeWidthPx = 11f,
                    modifier = Modifier.scale(tapScale)
                )
                Spacer(Modifier.height(8.dp))
                CartoonLoadingBar(progress = progress)
            }
        }
    }
}

@Composable
private fun PosterLogoHeader() {
    val woodTop = Color(0xFFFFDF85)
    val woodMid = Color(0xFFF39C38)
    val woodOutline = Color(0xFF592D0E)

    Box(contentAlignment = Alignment.TopCenter) {
        // Stone Plaque + Green Side Leaves behind "COIL / SNAKE ARENA"
        Box(
            modifier = Modifier
                .padding(top = 74.dp)
                .width(276.dp)
                .height(60.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Left & Right Green Leaf Ears peeking behind stone plaque
                drawCircle(CartoonOutline, radius = 22.dp.toPx(), center = Offset(10.dp.toPx(), size.height * 0.36f))
                drawCircle(LeafMid, radius = 19.dp.toPx(), center = Offset(10.dp.toPx(), size.height * 0.36f))
                drawCircle(CartoonOutline, radius = 22.dp.toPx(), center = Offset(size.width - 10.dp.toPx(), size.height * 0.36f))
                drawCircle(LeafMid, radius = 19.dp.toPx(), center = Offset(size.width - 10.dp.toPx(), size.height * 0.36f))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(52.dp)
                    .crackedStonePlaque(cornerDp = 14.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                CartoonOutlinedText(
                    text = "SNAKE ARENA",
                    fontSize = 21.sp,
                    fillColor = SunnyOrange,
                    outlineColor = woodOutline,
                    strokeWidthPx = 9f,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }
        }

        // Big Chunky 3D "NEON" & "COIL" Letters on Top
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CartoonOutlinedText(
                text = "NEON",
                fontSize = 62.sp,
                fillColor = woodTop,
                outlineColor = woodOutline,
                strokeWidthPx = 16f
            )
            CartoonOutlinedText(
                text = "COIL",
                fontSize = 44.sp,
                fillColor = woodMid,
                outlineColor = woodOutline,
                strokeWidthPx = 14f,
                modifier = Modifier.offset(y = (-16).dp)
            )
        }
    }
}

@Composable
private fun CartoonLoadingBar(progress: Float) {
    Canvas(
        modifier = Modifier
            .width(260.dp)
            .height(22.dp)
            .testTag("splash_loading_bar")
    ) {
        val w = size.width
        val h = size.height
        val cr = CornerRadius(h * 0.5f, h * 0.5f)
        drawRoundRect(CartoonOutline, Offset.Zero, Size(w, h), cr)
        val pad = 3.5.dp.toPx()
        drawRoundRect(
            color = Color(0xFF333945),
            topLeft = Offset(pad, pad),
            size = Size(w - pad * 2, h - pad * 2),
            cornerRadius = cr
        )
        val fillW = ((w - pad * 2) * progress.coerceIn(0.05f, 1f))
        drawRoundRect(
            color = GrassProgressGreen,
            topLeft = Offset(pad, pad),
            size = Size(fillW, h - pad * 2),
            cornerRadius = cr
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.45f),
            topLeft = Offset(pad + 4f, pad + 2f),
            size = Size((fillW - 8f).coerceAtLeast(4f), (h - pad * 2) * 0.35f),
            cornerRadius = cr
        )
    }
}

private fun DrawScope.drawHeroLeapingSnake(w: Float, h: Float, bobOffset: Float) {
    val bodyColors = listOf(
        Color(0xFF22E4DC),
        Color(0xFF54E84C),
        Color(0xFFFFDE38),
        Color(0xFFB388FF),
        Color(0xFFFF7AD9)
    )
    val count = 22
    val pts = Array(count) { i ->
        val t = i / (count - 1f) // 0 = tail at bottom-right, 1 = neck behind head
        val x = when {
            t < 0.45f -> {
                val u = t / 0.45f
                w * (0.68f - u * 0.25f)
            }
            else -> {
                val u = (t - 0.45f) / 0.55f
                w * (0.43f + kotlin.math.sin(u * Math.PI).toFloat() * 0.24f)
            }
        }
        val y = h * (0.76f - t * 0.27f) + bobOffset
        val r = w * (0.055f + t * 0.036f)
        Triple(x, y, r)
    }

    // Dark outline pass
    for ((x, y, r) in pts) {
        drawCircle(CartoonOutline, radius = r + 4.dp.toPx(), center = Offset(x, y))
    }
    // Colorful glossy stripes pass
    for (i in pts.indices) {
        val (x, y, r) = pts[i]
        val c = bodyColors[(i / 2) % bodyColors.size]
        drawCircle(c, radius = r, center = Offset(x, y))
        drawCircle(
            Color.White.copy(alpha = 0.42f),
            radius = r * 0.34f,
            center = Offset(x - r * 0.30f, y - r * 0.22f)
        )
    }

    // Giant Friendly Smiling Yellow Head in Center!
    val hx = w * 0.50f
    val hy = h * 0.50f + bobOffset
    val hr = w * 0.175f

    drawCircle(CartoonOutline, radius = hr + 4.5.dp.toPx(), center = Offset(hx, hy))
    drawCircle(Color(0xFFFFDE38), radius = hr, center = Offset(hx, hy))
    // Forehead glossy oval
    drawOval(
        color = Color.White.copy(alpha = 0.45f),
        topLeft = Offset(hx - hr * 0.58f, hy - hr * 0.78f),
        size = Size(hr * 0.62f, hr * 0.34f)
    )

    // Big Shiny Cartoon Eyes
    val ex = hr * 0.36f
    val ey = hy - hr * 0.06f
    val er = hr * 0.30f
    drawCircle(CartoonOutline, er + 3.dp.toPx(), Offset(hx - ex, ey))
    drawCircle(CartoonOutline, er + 3.dp.toPx(), Offset(hx + ex, ey))
    drawCircle(Color.White, er, Offset(hx - ex, ey))
    drawCircle(Color.White, er, Offset(hx + ex, ey))
    drawCircle(Color(0xFF1A1E26), er * 0.62f, Offset(hx - ex + 2f, ey + 2f))
    drawCircle(Color(0xFF1A1E26), er * 0.62f, Offset(hx + ex - 2f, ey + 2f))
    drawCircle(Color.White, er * 0.22f, Offset(hx - ex - 4f, ey - 5f))
    drawCircle(Color.White, er * 0.22f, Offset(hx + ex - 4f, ey - 5f))

    // Cute Pink Cheeks
    drawOval(
        color = Color(0xFFFF7597),
        topLeft = Offset(hx - hr * 0.82f, hy + hr * 0.12f),
        size = Size(hr * 0.32f, hr * 0.18f)
    )
    drawOval(
        color = Color(0xFFFF7597),
        topLeft = Offset(hx + hr * 0.50f, hy + hr * 0.12f),
        size = Size(hr * 0.32f, hr * 0.18f)
    )

    // Big Friendly Smiling Mouth + Cute White Corner Teeth + Pink Tongue
    val mouth = Path().apply {
        moveTo(hx - hr * 0.38f, hy + hr * 0.22f)
        quadraticTo(hx, hy + hr * 0.78f, hx + hr * 0.38f, hy + hr * 0.22f)
        close()
    }
    drawPath(mouth, Color(0xFF7B1A2C))
    drawPath(mouth, CartoonOutline, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    drawOval(
        color = Color(0xFFFF7597),
        topLeft = Offset(hx - hr * 0.20f, hy + hr * 0.38f),
        size = Size(hr * 0.40f, hr * 0.22f)
    )
    // Two tiny cute white corner fangs
    val leftFang = Path().apply {
        moveTo(hx - hr * 0.29f, hy + hr * 0.23f)
        lineTo(hx - hr * 0.21f, hy + hr * 0.38f)
        lineTo(hx - hr * 0.15f, hy + hr * 0.23f)
        close()
    }
    val rightFang = Path().apply {
        moveTo(hx + hr * 0.29f, hy + hr * 0.23f)
        lineTo(hx + hr * 0.21f, hy + hr * 0.38f)
        lineTo(hx + hr * 0.15f, hy + hr * 0.23f)
        close()
    }
    drawPath(leftFang, Color.White)
    drawPath(rightFang, Color.White)
}

private fun DrawScope.drawFloatingCliff(
    topX: Float,
    topY: Float,
    cliffW: Float,
    cliffH: Float,
    isLeft: Boolean
) {
    val cx = if (isLeft) topX + cliffW * 0.35f else topX - cliffW * 0.35f
    val rock = Path().apply {
        moveTo(cx - cliffW * 0.55f, topY)
        lineTo(cx + cliffW * 0.55f, topY)
        lineTo(cx + (if (isLeft) cliffW * 0.08f else cliffW * 0.22f), topY + cliffH)
        lineTo(cx - cliffW * 0.15f, topY + cliffH * 0.62f)
        close()
    }
    drawPath(rock, Color(0xFF566275))
    drawPath(rock, CartoonOutline, style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round))
    drawLine(CartoonOutline, Offset(cx, topY + 20f), Offset(cx - 8f, topY + cliffH * 0.55f), 2.5.dp.toPx())

    // Green grassy top disc
    drawOval(
        color = CartoonOutline,
        topLeft = Offset(cx - cliffW * 0.60f - 3.dp.toPx(), topY - cliffH * 0.12f - 3.dp.toPx()),
        size = Size(cliffW * 1.20f + 6.dp.toPx(), cliffH * 0.24f + 6.dp.toPx())
    )
    drawOval(
        color = LeafMid,
        topLeft = Offset(cx - cliffW * 0.60f, topY - cliffH * 0.12f),
        size = Size(cliffW * 1.20f, cliffH * 0.24f)
    )
    // Green bush puff on cliff
    val bx = if (isLeft) cx - cliffW * 0.18f else cx + cliffW * 0.08f
    val by = topY - cliffH * 0.12f
    drawCircle(CartoonOutline, cliffW * 0.28f + 3.dp.toPx(), Offset(bx, by))
    drawCircle(LeafMid, cliffW * 0.28f, Offset(bx, by))
    drawCircle(LeafBright, cliffW * 0.12f, Offset(bx - 6f, by - 6f))
}

private fun DrawScope.drawSmallAngrySnake(
    headX: Float,
    headY: Float,
    headR: Float,
    primary: Color,
    stripe: Color,
    tailDirRight: Boolean
) {
    val dir = if (tailDirRight) 1f else -1f
    for (i in 6 downTo 1) {
        val sx = headX + dir * i * headR * 0.52f
        val sy = headY + headR * 0.58f - (if (i > 4) (i - 4) * 4f else 0f)
        val sr = headR * (0.72f - i * 0.04f)
        drawCircle(CartoonOutline, sr + 3.dp.toPx(), Offset(sx, sy))
    }
    for (i in 6 downTo 1) {
        val sx = headX + dir * i * headR * 0.52f
        val sy = headY + headR * 0.58f - (if (i > 4) (i - 4) * 4f else 0f)
        val sr = headR * (0.72f - i * 0.04f)
        drawCircle(if (i % 2 == 0) primary else stripe, sr, Offset(sx, sy))
    }

    drawCircle(CartoonOutline, headR + 3.5.dp.toPx(), Offset(headX, headY))
    drawCircle(primary, headR, Offset(headX, headY))

    // Eyes + Angry Eyebrows
    val ex = headR * 0.34f
    val er = headR * 0.26f
    drawCircle(CartoonOutline, er + 2f, Offset(headX - ex, headY - 2f))
    drawCircle(CartoonOutline, er + 2f, Offset(headX + ex, headY - 2f))
    drawCircle(Color.White, er, Offset(headX - ex, headY - 2f))
    drawCircle(Color.White, er, Offset(headX + ex, headY - 2f))
    drawCircle(CartoonOutline, er * 0.52f, Offset(headX - ex, headY - 1f))
    drawCircle(CartoonOutline, er * 0.52f, Offset(headX + ex, headY - 1f))

    drawLine(CartoonOutline, Offset(headX - ex * 1.6f, headY - headR * 0.45f), Offset(headX - ex * 0.3f, headY - headR * 0.28f), 3.dp.toPx(), StrokeCap.Round)
    drawLine(CartoonOutline, Offset(headX + ex * 1.6f, headY - headR * 0.45f), Offset(headX + ex * 0.3f, headY - headR * 0.28f), 3.dp.toPx(), StrokeCap.Round)

    // Grumpy zig-zag frown
    drawArc(
        color = CartoonOutline,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(headX - headR * 0.3f, headY + headR * 0.32f),
        size = Size(headR * 0.6f, headR * 0.35f),
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawPosterCloud(cx: Float, cy: Float, r: Float) {
    drawCircle(Color.White, r * 0.75f, Offset(cx - r * 0.7f, cy + r * 0.1f))
    drawCircle(Color.White, r, Offset(cx, cy))
    drawCircle(Color.White, r * 0.72f, Offset(cx + r * 0.72f, cy + r * 0.12f))
}

private fun DrawScope.drawGrassTuft(x: Float, y: Float) {
    val p = Path().apply {
        moveTo(x - 10f, y)
        lineTo(x - 14f, y - 16f)
        lineTo(x - 3f, y - 6f)
        lineTo(x, y - 20f)
        lineTo(x + 4f, y - 6f)
        lineTo(x + 14f, y - 15f)
        lineTo(x + 10f, y)
        close()
    }
    drawPath(p, Color(0xFF2E9E28))
    drawPath(p, CartoonOutline, style = Stroke(width = 2.2.dp.toPx(), join = StrokeJoin.Round))
}

private fun DrawScope.drawPosterApple(cx: Float, cy: Float, r: Float) {
    drawCircle(CartoonOutline, r + 3.dp.toPx(), Offset(cx, cy))
    drawCircle(Color(0xFFFF3333), r, Offset(cx, cy))
    drawLine(CartoonOutline, Offset(cx, cy - r * 0.75f), Offset(cx + 3f, cy - r * 1.3f), 4.dp.toPx(), StrokeCap.Round)
    drawOval(LeafMid, Offset(cx + 2f, cy - r * 1.3f), Size(r * 0.8f, r * 0.45f))
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.22f, Offset(cx - r * 0.36f, cy - r * 0.2f))
}

private fun DrawScope.drawPosterCupcake(cx: Float, cy: Float, r: Float) {
    val cup = Path().apply {
        moveTo(cx - r * 0.75f, cy + r * 0.1f)
        lineTo(cx + r * 0.75f, cy + r * 0.1f)
        lineTo(cx + r * 0.55f, cy + r * 0.95f)
        lineTo(cx - r * 0.55f, cy + r * 0.95f)
        close()
    }
    drawPath(cup, Color(0xFFE09F5A))
    drawPath(cup, CartoonOutline, style = Stroke(width = 3.dp.toPx()))
    drawCircle(CartoonOutline, r * 0.82f + 3.dp.toPx(), Offset(cx, cy - r * 0.12f))
    drawCircle(Color(0xFFFF85C0), r * 0.82f, Offset(cx, cy - r * 0.12f))
    drawCircle(CartoonOutline, r * 0.28f + 2.dp.toPx(), Offset(cx, cy - r * 0.88f))
    drawCircle(Color(0xFFFF2A4B), r * 0.28f, Offset(cx, cy - r * 0.88f))
}

private fun DrawScope.drawPosterCroissant(cx: Float, cy: Float, r: Float) {
    drawArc(CartoonOutline, 195f, 150f, false, Offset(cx - r, cy - r * 0.75f), Size(r * 2f, r * 1.5f), style = Stroke(width = r * 0.78f, cap = StrokeCap.Round))
    drawArc(Color(0xFFF4A236), 195f, 150f, false, Offset(cx - r, cy - r * 0.75f), Size(r * 2f, r * 1.5f), style = Stroke(width = r * 0.56f, cap = StrokeCap.Round))
}

private fun DrawScope.drawPosterOrb(cx: Float, cy: Float, r: Float, color: Color) {
    drawCircle(CartoonOutline, r + 2.5.dp.toPx(), Offset(cx, cy))
    drawCircle(color, r, Offset(cx, cy))
    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.34f, Offset(cx - r * 0.24f, cy - r * 0.24f))
}
