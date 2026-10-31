package com.example.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CartoonOutline
import com.example.ui.theme.CloudShade
import com.example.ui.theme.CloudWhite
import com.example.ui.theme.HeartRedBright
import com.example.ui.theme.HeartRedDeep
import com.example.ui.theme.LeafBright
import com.example.ui.theme.LeafDark
import com.example.ui.theme.LeafMid
import com.example.ui.theme.LilitaOneFontFamily
import com.example.ui.theme.NunitoFontFamily
import com.example.ui.theme.SkyBottomBlue
import com.example.ui.theme.SkyMidBlue
import com.example.ui.theme.SkyTopBlue
import com.example.ui.theme.StoneBorder
import com.example.ui.theme.StoneCrack
import com.example.ui.theme.StoneDark
import com.example.ui.theme.StoneLight
import com.example.ui.theme.StoneMid
import com.example.ui.theme.WoodBranch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Chunky white (or tinted) cartoon lettering with a thick dark outline and drop shadow.
 */
@Composable
fun CartoonOutlinedText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fillColor: Color = Color.White,
    outlineColor: Color = CartoonOutline,
    strokeWidthPx: Float = 9f,
    textAlign: TextAlign = TextAlign.Center,
    maxLines: Int = Int.MAX_VALUE
) {
    val baseStyle = TextStyle(
        fontFamily = LilitaOneFontFamily,
        fontSize = fontSize,
        letterSpacing = 0.8.sp,
        textAlign = textAlign
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = baseStyle.copy(
                color = outlineColor.copy(alpha = 0.85f),
                drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round)
            ),
            modifier = Modifier.offset(y = 2.dp)
        )
        Text(
            text = text,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = baseStyle.copy(
                color = outlineColor,
                drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round)
            )
        )
        Text(
            text = text,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            style = baseStyle.copy(color = fillColor)
        )
    }
}

/**
 * Draws a cracked grey cartoon stone plaque background on any Box/Surface.
 */
fun Modifier.crackedStonePlaque(cornerDp: Dp = 18.dp): Modifier = this.drawBehind {
    val r = cornerDp.toPx()
    val w = size.width
    val h = size.height
    val borderW = 3.5.dp.toPx()

    drawRoundRect(
        color = StoneBorder,
        topLeft = Offset.Zero,
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r)
    )
    drawRoundRect(
        color = StoneDark,
        topLeft = Offset(borderW, borderW),
        size = Size((w - borderW * 2).coerceAtLeast(2f), (h - borderW * 2).coerceAtLeast(2f)),
        cornerRadius = CornerRadius((r - borderW).coerceAtLeast(4f), (r - borderW).coerceAtLeast(4f))
    )
    val inset = borderW * 1.15f
    val faceH = (h - inset * 2 - 4.dp.toPx()).coerceAtLeast(6f)
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(StoneLight, StoneMid)),
        topLeft = Offset(inset, inset),
        size = Size((w - inset * 2).coerceAtLeast(2f), faceH),
        cornerRadius = CornerRadius((r - inset).coerceAtLeast(4f), (r - inset).coerceAtLeast(4f))
    )
    drawLine(
        color = Color.White.copy(alpha = 0.55f),
        start = Offset(r, inset + 2.5.dp.toPx()),
        end = Offset((w - r).coerceAtLeast(r), inset + 2.5.dp.toPx()),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round
    )

    val crackStroke = 2.dp.toPx()
    drawLine(StoneCrack, Offset(w * 0.18f, inset), Offset(w * 0.15f, inset + h * 0.24f), crackStroke)
    drawLine(StoneCrack, Offset(w * 0.15f, inset + h * 0.24f), Offset(w * 0.20f, inset + h * 0.36f), crackStroke)
    drawLine(StoneCrack, Offset(w * 0.76f, h - inset), Offset(w * 0.73f, h - inset - h * 0.28f), crackStroke)
    drawLine(StoneCrack, Offset(w * 0.84f, inset), Offset(w * 0.81f, inset + h * 0.18f), crackStroke)

    drawCircle(StoneDark, radius = 2.2.dp.toPx(), center = Offset(w * 0.08f, h * 0.42f))
    drawCircle(StoneDark, radius = 1.8.dp.toPx(), center = Offset(w * 0.91f, h * 0.58f))
    drawCircle(Color.White.copy(alpha = 0.45f), radius = 2.0.dp.toPx(), center = Offset(w * 0.12f, h * 0.28f))
}

/**
 * Wide cracked grey STONE BANNER (for "NEONCOIL" title and panel headers).
 */
@Composable
fun StoneBannerPlaque(
    title: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 36.sp,
    verticalPad: Dp = 10.dp
) {
    Box(
        modifier = modifier
            .crackedStonePlaque(cornerDp = 20.dp)
            .padding(horizontal = 20.dp, vertical = verticalPad),
        contentAlignment = Alignment.Center
    ) {
        CartoonOutlinedText(
            text = title,
            fontSize = fontSize,
            strokeWidthPx = 11f,
            maxLines = 1
        )
    }
}

/**
 * Cracked stone-plaque button with bouncy press animation, soft idle wobble, and optional small subtitle/status dot.
 */
@Composable
fun StonePlaqueButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showSubtitleGreenDot: Boolean = false,
    wobblePhaseOffsetMs: Int = 0,
    fontSize: TextUnit = 25.sp,
    enabled: Boolean = true
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "stone_btn_press"
    )

    val wobbleAnim = rememberInfiniteTransition(label = "stone_idle_wobble")
    val wobbleDeg by wobbleAnim.animateFloat(
        initialValue = -0.65f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900 + wobblePhaseOffsetMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble_deg"
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .scale(pressScale)
            .rotate(if (enabled) wobbleDeg else 0f)
            .crackedStonePlaque(cornerDp = 16.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val released = tryAwaitRelease()
                        pressed = false
                        if (released) onClick()
                    }
                )
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CartoonOutlinedText(
                text = text,
                fontSize = fontSize,
                fillColor = if (enabled) Color.White else Color(0xFFD0D5DD),
                strokeWidthPx = 9.5f,
                maxLines = 1
            )
            if (!subtitle.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (showSubtitleGreenDot) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38E54D))
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = subtitle,
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = CartoonOutline,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

enum class StoneRoundIconType {
    SKINS_SNAKE,
    SETTINGS_GEAR,
    PAUSE_BARS,
    BACK_ARROW,
    BOOST_BOLT,
    LEAVE_EXIT
}

/**
 * Small round stone button with custom code-drawn cartoon icon.
 * Guarantees >= 48.dp accessible touch target even when visual sizeDp is smaller (e.g. 38.dp HUD pause button).
 */
@Composable
fun RoundStoneButton(
    iconType: StoneRoundIconType,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 52.dp,
    caption: String? = null,
    captionFontSize: TextUnit = 11.sp
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "round_stone_press"
    )

    Column(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            }
            .scale(pressScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        val ok = tryAwaitRelease()
                        pressed = false
                        if (ok) onClick()
                    }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(sizeDp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.5f
                val c = Offset(size.width * 0.5f, size.height * 0.5f)
                drawCircle(StoneBorder, radius = r, center = c)
                drawCircle(StoneDark, radius = r * 0.90f, center = c)
                drawCircle(
                    brush = Brush.verticalGradient(listOf(StoneLight, StoneMid)),
                    radius = r * 0.80f,
                    center = Offset(c.x, c.y - r * 0.03f)
                )
                drawCircle(StoneBorder, radius = r * 0.80f, center = c, style = Stroke(width = 2.2f))

                when (iconType) {
                    StoneRoundIconType.SKINS_SNAKE -> {
                        val dots = listOf(
                            Offset(-r * 0.30f, r * 0.14f) to Color(0xFF2FE6DE),
                            Offset(-r * 0.10f, r * 0.24f) to Color(0xFF56E84D),
                            Offset(r * 0.12f, r * 0.16f) to Color(0xFFA87BFF),
                            Offset(r * 0.22f, -r * 0.04f) to Color(0xFFFF7AD9),
                            Offset(r * 0.08f, -r * 0.24f) to Color(0xFFFFDE38)
                        )
                        dots.forEach { (off, col) ->
                            drawCircle(CartoonOutline, radius = r * 0.18f + 2.0f, center = c + off)
                            drawCircle(col, radius = r * 0.18f, center = c + off)
                        }
                        val headC = c + Offset(r * 0.08f, -r * 0.24f)
                        drawCircle(CartoonOutline, r * 0.045f, headC + Offset(-r * 0.05f, -r * 0.02f))
                        drawCircle(CartoonOutline, r * 0.045f, headC + Offset(r * 0.05f, -r * 0.02f))
                    }
                    StoneRoundIconType.SETTINGS_GEAR -> {
                        val gearColor = Color(0xFF4B525E)
                        for (t in 0 until 8) {
                            val a = (t * PI / 4.0).toFloat()
                            drawLine(
                                color = gearColor,
                                start = c + Offset(cos(a) * r * 0.22f, sin(a) * r * 0.22f),
                                end = c + Offset(cos(a) * r * 0.48f, sin(a) * r * 0.48f),
                                strokeWidth = r * 0.18f,
                                cap = StrokeCap.Round
                            )
                        }
                        drawCircle(gearColor, radius = r * 0.34f, center = c)
                        drawCircle(StoneLight, radius = r * 0.14f, center = c)
                    }
                    StoneRoundIconType.PAUSE_BARS -> {
                        drawRoundRect(
                            CartoonOutline,
                            Offset(c.x - r * 0.26f, c.y - r * 0.28f),
                            Size(r * 0.18f, r * 0.56f),
                            CornerRadius(5f, 5f)
                        )
                        drawRoundRect(
                            CartoonOutline,
                            Offset(c.x + r * 0.08f, c.y - r * 0.28f),
                            Size(r * 0.18f, r * 0.56f),
                            CornerRadius(5f, 5f)
                        )
                    }
                    StoneRoundIconType.LEAVE_EXIT -> {
                        val s = r * 0.26f
                        drawLine(
                            color = Color(0xFFD92D20),
                            start = Offset(c.x - s, c.y - s),
                            end = Offset(c.x + s, c.y + s),
                            strokeWidth = r * 0.20f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFFD92D20),
                            start = Offset(c.x + s, c.y - s),
                            end = Offset(c.x - s, c.y + s),
                            strokeWidth = r * 0.20f,
                            cap = StrokeCap.Round
                        )
                    }
                    StoneRoundIconType.BACK_ARROW -> {
                        val p = Path().apply {
                            moveTo(c.x + r * 0.22f, c.y - r * 0.28f)
                            lineTo(c.x - r * 0.24f, c.y)
                            lineTo(c.x + r * 0.22f, c.y + r * 0.28f)
                        }
                        drawPath(p, CartoonOutline, style = Stroke(width = r * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    StoneRoundIconType.BOOST_BOLT -> {
                        val bolt = Path().apply {
                            moveTo(c.x + r * 0.08f, c.y - r * 0.44f)
                            lineTo(c.x - r * 0.26f, c.y + r * 0.04f)
                            lineTo(c.x + r * 0.02f, c.y + r * 0.04f)
                            lineTo(c.x - r * 0.10f, c.y + r * 0.44f)
                            lineTo(c.x + r * 0.28f, c.y - r * 0.06f)
                            lineTo(c.x, c.y - r * 0.06f)
                            close()
                        }
                        drawPath(bolt, Color(0xFFFFDE38))
                        drawPath(bolt, CartoonOutline, style = Stroke(width = 3.2f, join = StrokeJoin.Round))
                    }
                }
            }
        }
        if (caption != null) {
            CartoonOutlinedText(
                text = caption,
                fontSize = captionFontSize,
                strokeWidthPx = 5.5f,
                maxLines = 1
            )
        }
    }
}

/**
 * Compact In-Game Boost Energy Bar:
 * Default 28.dp glossy red heart in round stone ring + 14.dp tall segmented red bar (NO text label),
 * scaled cleanly by the player's HUD size slider.
 */
@Composable
fun BoostEnergyHeartBar(
    energy: Float,
    modifier: Modifier = Modifier,
    heartSizeDp: Dp = 28.dp,
    barHeightDp: Dp = 14.dp
) {
    val clamped = energy.coerceIn(0f, 1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(
            modifier = Modifier
                .padding(start = heartSizeDp * 0.62f)
                .fillMaxWidth()
                .height(barHeightDp)
        ) {
            val w = size.width
            val h = size.height
            val cr = CornerRadius(h * 0.5f, h * 0.5f)

            drawRoundRect(StoneBorder, Offset.Zero, Size(w, h), cr)
            val frameInset = (h * 0.14f).coerceAtLeast(1.5f)
            drawRoundRect(
                color = StoneMid,
                topLeft = Offset(frameInset, frameInset),
                size = Size((w - frameInset * 2).coerceAtLeast(4f), (h - frameInset * 2).coerceAtLeast(4f)),
                cornerRadius = cr
            )
            val pad = (h * 0.24f).coerceAtLeast(2.5f)
            val innerW = (w - pad * 2).coerceAtLeast(8f)
            val innerH = (h - pad * 2).coerceAtLeast(4f)
            drawRoundRect(
                color = Color(0xFF3A1D22),
                topLeft = Offset(pad, pad),
                size = Size(innerW, innerH),
                cornerRadius = CornerRadius(innerH * 0.5f, innerH * 0.5f)
            )

            val segments = 10
            val gap = 1.5.dp.toPx()
            val segW = ((innerW - gap * (segments - 1)) / segments).coerceAtLeast(1f)
            val filledCount = (clamped * segments).toInt()
            val partialFrac = (clamped * segments) - filledCount

            for (i in 0 until segments) {
                val sx = pad + i * (segW + gap)
                val fillAlpha = when {
                    i < filledCount -> 1f
                    i == filledCount && partialFrac > 0.05f -> partialFrac
                    else -> 0f
                }
                if (fillAlpha > 0f) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(
                                HeartRedBright.copy(alpha = fillAlpha),
                                HeartRedDeep.copy(alpha = fillAlpha)
                            )
                        ),
                        topLeft = Offset(sx, pad),
                        size = Size(segW, innerH),
                        cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                    )
                }
            }
        }

        Canvas(modifier = Modifier.size(heartSizeDp)) {
            val r = size.minDimension * 0.5f
            val c = Offset(r, r)

            drawCircle(StoneBorder, radius = r, center = c)
            drawCircle(StoneDark, radius = r * 0.90f, center = c)
            drawCircle(
                brush = Brush.verticalGradient(listOf(StoneLight, StoneMid)),
                radius = r * 0.80f,
                center = c
            )

            val hs = r * 0.56f
            val hp = Path().apply {
                moveTo(c.x, c.y + hs * 0.82f)
                cubicTo(
                    c.x - hs * 1.28f, c.y + hs * 0.12f,
                    c.x - hs * 1.05f, c.y - hs * 0.92f,
                    c.x, c.y - hs * 0.32f
                )
                cubicTo(
                    c.x + hs * 1.05f, c.y - hs * 0.92f,
                    c.x + hs * 1.28f, c.y + hs * 0.12f,
                    c.x, c.y + hs * 0.82f
                )
                close()
            }
            drawPath(hp, Brush.verticalGradient(listOf(HeartRedBright, HeartRedDeep)))
            drawPath(hp, CartoonOutline, style = Stroke(width = (r * 0.12f).coerceAtLeast(1.5f), join = StrokeJoin.Round))
            drawOval(
                color = Color.White.copy(alpha = 0.78f),
                topLeft = Offset(c.x - hs * 0.56f, c.y - hs * 0.42f),
                size = Size(hs * 0.34f, hs * 0.22f)
            )
        }
    }
}

/**
 * Bright Sky-Blue cartoon nature background with slowly drifting fluffy clouds,
 * hanging wooden tree branches & green leaves in the top corners, and green bush clusters along the bottom.
 */
@Composable
fun SkyAndFoliageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val cloudTransition = rememberInfiniteTransition(label = "drifting_clouds")
    val drift by cloudTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloud_drift_frac"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(SkyTopBlue, SkyMidBlue, SkyBottomBlue)
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = minOf(w, h) * 0.58f,
                center = Offset(w * 0.5f, h * 0.36f)
            )

            val c1x = ((w * 0.18f + drift * w * 1.2f) % (w * 1.3f)) - w * 0.15f
            val c2x = ((w * 0.72f + drift * w * 0.9f) % (w * 1.3f)) - w * 0.15f
            val c3x = ((w * 0.42f + drift * w * 0.7f) % (w * 1.3f)) - w * 0.15f

            drawFluffyCloud(cx = c1x, cy = h * 0.22f, scale = w * 0.0011f)
            drawFluffyCloud(cx = c3x, cy = h * 0.48f, scale = w * 0.00135f)
            drawFluffyCloud(cx = c2x, cy = h * 0.66f, scale = w * 0.00105f)

            drawLine(
                color = CartoonOutline,
                start = Offset(-10f, h * 0.09f),
                end = Offset(w * 0.22f, -10f),
                strokeWidth = 22.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = WoodBranch,
                start = Offset(-10f, h * 0.09f),
                end = Offset(w * 0.22f, -10f),
                strokeWidth = 15.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = CartoonOutline,
                start = Offset(w + 10f, h * 0.10f),
                end = Offset(w * 0.78f, -10f),
                strokeWidth = 22.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = WoodBranch,
                start = Offset(w + 10f, h * 0.10f),
                end = Offset(w * 0.78f, -10f),
                strokeWidth = 15.dp.toPx(),
                cap = StrokeCap.Round
            )

            drawBushCluster(cx = w * 0.10f, cy = h * 0.03f, r = w * 0.15f)
            drawBushCluster(cx = w * 0.24f, cy = h * 0.01f, r = w * 0.11f)
            drawBushCluster(cx = w * 0.90f, cy = h * 0.03f, r = w * 0.15f)
            drawBushCluster(cx = w * 0.76f, cy = h * 0.01f, r = w * 0.11f)

            drawBushCluster(cx = w * 0.06f, cy = h * 0.94f, r = w * 0.18f)
            drawBushCluster(cx = w * 0.28f, cy = h * 0.98f, r = w * 0.15f)
            drawBushCluster(cx = w * 0.52f, cy = h * 0.99f, r = w * 0.16f)
            drawBushCluster(cx = w * 0.74f, cy = h * 0.98f, r = w * 0.15f)
            drawBushCluster(cx = w * 0.94f, cy = h * 0.94f, r = w * 0.18f)
        }
        content()
    }
}

private fun DrawScope.drawFluffyCloud(cx: Float, cy: Float, scale: Float) {
    val s = scale.coerceIn(0.45f, 1.8f) * 100f
    drawCircle(CloudShade, radius = s * 0.45f, center = Offset(cx - s * 0.45f, cy + s * 0.08f))
    drawCircle(CloudShade, radius = s * 0.56f, center = Offset(cx, cy + s * 0.06f))
    drawCircle(CloudShade, radius = s * 0.42f, center = Offset(cx + s * 0.48f, cy + s * 0.09f))
    drawCircle(CloudWhite, radius = s * 0.45f, center = Offset(cx - s * 0.45f, cy))
    drawCircle(CloudWhite, radius = s * 0.60f, center = Offset(cx, cy - s * 0.10f))
    drawCircle(CloudWhite, radius = s * 0.42f, center = Offset(cx + s * 0.48f, cy + s * 0.02f))
}

private fun DrawScope.drawBushCluster(cx: Float, cy: Float, r: Float) {
    val puffs = listOf(
        Offset(-r * 0.55f, r * 0.12f) to r * 0.62f,
        Offset(r * 0.55f, r * 0.12f) to r * 0.62f,
        Offset(0f, -r * 0.12f) to r * 0.76f
    )
    puffs.forEach { (off, rad) ->
        drawCircle(CartoonOutline, radius = rad + 3.5.dp.toPx(), center = Offset(cx, cy) + off)
    }
    puffs.forEach { (off, rad) ->
        drawCircle(LeafDark, radius = rad, center = Offset(cx, cy) + off)
        drawCircle(LeafMid, radius = rad * 0.88f, center = Offset(cx, cy) + off + Offset(0f, -rad * 0.06f))
    }
    drawCircle(LeafBright, radius = r * 0.16f, center = Offset(cx - r * 0.24f, cy - r * 0.28f))
    drawCircle(LeafBright, radius = r * 0.11f, center = Offset(cx + r * 0.18f, cy - r * 0.22f))
    drawCircle(LeafBright, radius = r * 0.10f, center = Offset(cx - r * 0.52f, cy + r * 0.06f))
}
