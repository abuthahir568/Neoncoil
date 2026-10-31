package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.config.AppLanguage
import com.example.config.I18n
import com.example.engine.ArenaRenderSource
import com.example.engine.ArenaRenderer.renderArena
import com.example.engine.ArenaRenderer.renderMinimap
import com.example.engine.HudState
import com.example.engine.MatchEndSummary
import com.example.services.ControlScheme
import com.example.services.UserSettings
import com.example.ui.theme.CartoonOutline
import com.example.ui.theme.GoldCoinYellow
import com.example.ui.theme.GrassProgressGreen
import com.example.ui.theme.NunitoFontFamily
import com.example.ui.theme.StoneBorder
import com.example.ui.theme.StoneDark
import com.example.ui.theme.StoneLight
import com.example.ui.theme.StoneMid
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * SCREEN 3: COMPACT, ZERO-OVERLAP IN-GAME HUD + 60 FPS ARENA
 * - Safe-area + 12.dp extra padding on ALL edges.
 * - Multiplied dynamically by `settings.hudScale` (60%..140%, default 80%)
 *   and `settings.controlsScale` (50%..150%, default 85%).
 * - Offline mode: Pause button -> Stone Pause Popup (RESUME w/ 3-2-1 countdown, RESTART, SETTINGS, HOME).
 * - Online mode: Leave button -> Stone Leave Popup, Ping badge, Room code pill, and Kill Feed.
 */
@Composable
fun GameScreen(
    renderSource: ArenaRenderSource,
    hud: HudState,
    settings: UserSettings,
    isOnlineMatch: Boolean,
    isPausedModalOpen: Boolean,
    resumeCountdownNumber: Int?, // 3, 2, 1 when resuming
    onPlayerInput: (Float?, Boolean) -> Unit,
    onFrameTick: (Float) -> Unit,
    onOpenPauseModal: () -> Unit,
    onResumeWithCountdown: () -> Unit,
    onRestartOffline: () -> Unit,
    onOpenSettingsFromPause: () -> Unit,
    onExitHomeSavingSession: () -> Unit,
    onLeaveOnlineMatch: () -> Unit,
    onTogglePrivateRoomBots: (Boolean) -> Unit
) {
    var showLeaveOnlineConfirm by remember { mutableStateOf(false) }

    BackHandler {
        if (isOnlineMatch) {
            showLeaveOnlineConfirm = !showLeaveOnlineConfirm
        } else {
            if (!isPausedModalOpen) onOpenPauseModal()
        }
    }

    var renderTick by remember { mutableIntStateOf(0) }
    var steerAngle by remember { mutableStateOf<Float?>(null) }
    var boostTouch by remember { mutableStateOf(false) }
    var boostKey by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    val focusRequester = remember { FocusRequester() }

    val lang = settings.language
    val hudScale = settings.hudScale
    val ctrlScale = settings.controlsScale
    val controlScheme = settings.controlScheme

    LaunchedEffect(isOnlineMatch) {
        runCatching { focusRequester.requestFocus() }
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 1f / 60f else ((now - lastNanos) / 1_000_000_000f)
                lastNanos = now
                onPlayerInput(steerAngle, boostTouch || boostKey)
                onFrameTick(dt)
                renderTick++
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { ev ->
                when (ev.key) {
                    Key.Spacebar -> {
                        boostKey = ev.type == KeyEventType.KeyDown
                        true
                    }
                    Key.DirectionRight, Key.D -> {
                        if (ev.type == KeyEventType.KeyDown) steerAngle = 0f
                        true
                    }
                    Key.DirectionLeft, Key.A -> {
                        if (ev.type == KeyEventType.KeyDown) steerAngle = Math.PI.toFloat()
                        true
                    }
                    Key.DirectionUp, Key.W -> {
                        if (ev.type == KeyEventType.KeyDown) steerAngle = (-Math.PI / 2.0).toFloat()
                        true
                    }
                    Key.DirectionDown, Key.S -> {
                        if (ev.type == KeyEventType.KeyDown) steerAngle = (Math.PI / 2.0).toFloat()
                        true
                    }
                    Key.Escape -> {
                        if (ev.type == KeyEventType.KeyDown) {
                            if (isOnlineMatch) showLeaveOnlineConfirm = true else onOpenPauseModal()
                        }
                        true
                    }
                    else -> false
                }
            }
            .onSizeChanged { canvasSize = it }
            .then(
                if (controlScheme == ControlScheme.TOUCH_DRAG) {
                    Modifier
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                val cx = canvasSize.width * 0.5f
                                val cy = canvasSize.height * 0.5f
                                val dx = change.position.x - cx
                                val dy = change.position.y - cy
                                if (hypot(dx, dy) > 6f) steerAngle = atan2(dy, dx)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val cx = canvasSize.width * 0.5f
                                val cy = canvasSize.height * 0.5f
                                steerAngle = atan2(pos.y - cy, pos.x - cx)
                            }
                        }
                } else Modifier
            )
            .testTag("game_screen")
    ) {
        // 1. 60 FPS Scrolling Arena Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (renderTick >= 0) {
                renderArena(renderSource)
            }
        }

        // 2. Non-Overlapping Safe-Area HUD Layer (safeDrawing + 12.dp extra padding)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp)
        ) {
            val screenW = maxWidth

            // Scaled HUD Dimensions
            val leaderboardMaxW = min(screenW * 0.32f, (136f * hudScale).dp)
            val coinBarMaxW = min(screenW * 0.34f, (152f * hudScale).dp)
            val pauseVisualDp = (38f * hudScale).dp
            val minimapDp = (70f * hudScale).dp

            val energyBarW = min(screenW * 0.32f, (170f * hudScale).dp)
            val energyHeartDp = (28f * hudScale).dp
            val energyBarHeightDp = (14f * hudScale).dp

            // Scaled Controls Dimensions (16.dp total edge margin = 12.dp safe pad + 4.dp inner pad)
            val joystickBaseDp = (92f * ctrlScale).dp
            val joystickKnobDp = (38f * ctrlScale).dp
            val boostBtnDp = (62f * ctrlScale).dp

            // Check if bottom controls + energy bar would collide horizontally; if so, lift energy bar above!
            val leftCtrlSpan = if (controlScheme == ControlScheme.JOYSTICK) joystickBaseDp else 0.dp
            val neededBottomRowW = leftCtrlSpan + boostBtnDp + energyBarW + 28.dp
            val stackEnergyAboveControls = neededBottomRowW > screenW

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP FLEX ROW: [Leaderboard (Left)]  [Coin Bar + Score + Ping/KillFeed (Center)]  [Pause + Minimap (Right)]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // TOP-LEFT: Compact Semi-Transparent Dark Leaderboard (Top 5 + "YOU: #rank")
                    CompactLeaderboardHud(
                        hud = hud,
                        lang = lang,
                        hudScale = hudScale,
                        modifier = Modifier
                            .widthIn(max = leaderboardMaxW)
                            .testTag("hud_leaderboard")
                    )

                    // TOP-CENTER: Coin Progress Bar + Tiny Score Pill (+ Online Ping / Kill Feed)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CompactCoinProgressHud(
                            progress = hud.hudCoinProgress,
                            target = hud.hudCoinTarget,
                            lang = lang,
                            hudScale = hudScale,
                            modifier = Modifier.widthIn(max = coinBarMaxW)
                        )

                        // Tiny Score Pill (+ Ping Badge when Online)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0x99141828))
                                    .border(1.2.dp, StoneLight, RoundedCornerShape(50))
                                    .padding(horizontal = 9.dp, vertical = 2.dp)
                                    .testTag("hud_score_pill")
                            ) {
                                Text(
                                    text = "${hud.score}",
                                    fontFamily = NunitoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = (13f * hudScale).coerceIn(9f, 19f).sp,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }

                            if (isOnlineMatch) {
                                PingStatusBadge(pingMs = hud.pingMs, hudScale = hudScale)
                            }
                        }

                        // Private Room Code + Host Bots Toggle (if in a 4-letter private room)
                        if (isOnlineMatch && !hud.roomCode.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xAA141828))
                                        .border(1.dp, GoldCoinYellow, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${I18n.t("room_code", lang)}: ${hud.roomCode}",
                                        fontFamily = NunitoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = (10f * hudScale).coerceIn(8f, 13f).sp,
                                        color = GoldCoinYellow,
                                        maxLines = 1
                                    )
                                }
                                if (hud.isPrivateHost) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (hud.privateBotsEnabled) Color(0xAA1F7A34) else Color(0xAA475467))
                                            .clickable { onTogglePrivateRoomBots(!hud.privateBotsEnabled) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (hud.privateBotsEnabled) "Bots: ON" else "Bots: OFF",
                                            fontFamily = NunitoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (9.5f * hudScale).coerceIn(8f, 12f).sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        // Online Kill Feed ("Coco ate Taffy")
                        if (isOnlineMatch && hud.killFeed.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                hud.killFeed.take(2).forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0x88141828))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = item.text,
                                            fontFamily = NunitoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = (10f * hudScale).coerceIn(8f, 13f).sp,
                                            color = Color(0xFFFFE066),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // TOP-RIGHT: Always-visible PAUSE / LEAVE Button immediately left of Minimap!
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RoundStoneButton(
                            iconType = if (isOnlineMatch) StoneRoundIconType.LEAVE_EXIT else StoneRoundIconType.PAUSE_BARS,
                            contentDescription = if (isOnlineMatch) {
                                stringResource(R.string.cd_leave_button)
                            } else {
                                stringResource(R.string.cd_pause_button)
                            },
                            onClick = {
                                if (isOnlineMatch) showLeaveOnlineConfirm = true else onOpenPauseModal()
                            },
                            sizeDp = pauseVisualDp,
                            modifier = Modifier.testTag("pause_button")
                        )

                        // 70px * hudScale Minimap
                        Box(
                            modifier = Modifier
                                .size(minimapDp)
                                .testTag("hud_minimap"),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val r = size.minDimension * 0.5f
                                val c = Offset(r, r)
                                drawCircle(StoneBorder, radius = r, center = c)
                                drawCircle(StoneMid, radius = r * 0.92f, center = c)
                                drawCircle(StoneBorder, radius = r * 0.84f, center = c)
                            }
                            Canvas(
                                modifier = Modifier
                                    .size(minimapDp * 0.84f)
                                    .clip(CircleShape)
                            ) {
                                if (renderTick >= 0) {
                                    renderMinimap(renderSource)
                                }
                            }
                        }
                    }
                }

                // BOTTOM CONTROLS + BOOST ENERGY BAR (auto-stacks energy bar above controls when large!)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp), // +12.dp outer = 16.dp safe margin
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (stackEnergyAboveControls) {
                        BoostEnergyHeartBar(
                            energy = hud.boostEnergy,
                            heartSizeDp = energyHeartDp,
                            barHeightDp = energyBarHeightDp,
                            modifier = Modifier
                                .width(energyBarW)
                                .testTag("hud_boost_energy_bar")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (controlScheme == ControlScheme.JOYSTICK) {
                            StoneVirtualJoystick(
                                outerDp = joystickBaseDp,
                                knobDp = joystickKnobDp,
                                onAngleChange = { ang -> steerAngle = ang },
                                modifier = Modifier.alpha(0.75f)
                            )
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        if (!stackEnergyAboveControls) {
                            BoostEnergyHeartBar(
                                energy = hud.boostEnergy,
                                heartSizeDp = energyHeartDp,
                                barHeightDp = energyBarHeightDp,
                                modifier = Modifier
                                    .width(energyBarW)
                                    .padding(bottom = 6.dp)
                                    .testTag("hud_boost_energy_bar")
                            )
                        }

                        // Round Stone Boost Button (62.dp * ctrlScale, opacity 0.75)
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .size(boostBtnDp)
                                .alpha(0.75f)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            boostTouch = true
                                            tryAwaitRelease()
                                            boostTouch = false
                                        }
                                    )
                                }
                                .testTag("boost_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val r = size.minDimension * 0.5f
                                val c = Offset(r, r)
                                drawCircle(StoneBorder, radius = r, center = c)
                                drawCircle(StoneDark, radius = r * 0.91f, center = c)
                                drawCircle(
                                    brush = Brush.verticalGradient(
                                        if (boostTouch) listOf(Color(0xFFFFE066), Color(0xFFFF922B))
                                        else listOf(StoneLight, StoneMid)
                                    ),
                                    radius = r * 0.81f,
                                    center = c
                                )
                                val bolt = Path().apply {
                                    moveTo(c.x + r * 0.08f, c.y - r * 0.44f)
                                    lineTo(c.x - r * 0.28f, c.y + r * 0.05f)
                                    lineTo(c.x + r * 0.02f, c.y + r * 0.05f)
                                    lineTo(c.x - r * 0.10f, c.y + r * 0.44f)
                                    lineTo(c.x + r * 0.30f, c.y - r * 0.06f)
                                    lineTo(c.x, c.y - r * 0.06f)
                                    close()
                                }
                                drawPath(bolt, GoldCoinYellow)
                                drawPath(bolt, CartoonOutline, style = Stroke(width = 3.2f))
                            }
                        }
                    }
                }
            }
        }

        // 3. Offline 3 - 2 - 1 Resume Countdown Overlay (while game remains frozen)
        if (!isOnlineMatch && resumeCountdownNumber != null && !isPausedModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x44141828))
                    .testTag("resume_countdown_overlay"),
                contentAlignment = Alignment.Center
            ) {
                CartoonOutlinedText(
                    text = "$resumeCountdownNumber",
                    fontSize = 76.sp,
                    fillColor = GoldCoinYellow,
                    strokeWidthPx = 16f
                )
            }
        }

        // 4. Offline Stone Pause Popup (RESUME, RESTART, SETTINGS, HOME)
        if (!isOnlineMatch && isPausedModalOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x881A2436))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .testTag("pause_popup"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .widthIn(max = 300.dp)
                        .crackedStonePlaque(cornerDp = 24.dp)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CartoonOutlinedText(
                        text = I18n.t("paused", lang),
                        fontSize = 30.sp,
                        strokeWidthPx = 10f
                    )
                    StonePlaqueButton(
                        text = I18n.t("resume", lang),
                        onClick = onResumeWithCountdown,
                        fontSize = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("resume_button")
                    )
                    StonePlaqueButton(
                        text = I18n.t("restart", lang),
                        onClick = onRestartOffline,
                        fontSize = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pause_restart_button")
                    )
                    StonePlaqueButton(
                        text = I18n.t("settings", lang),
                        onClick = onOpenSettingsFromPause,
                        fontSize = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pause_settings_button")
                    )
                    StonePlaqueButton(
                        text = I18n.t("home", lang),
                        onClick = onExitHomeSavingSession,
                        fontSize = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pause_home_button")
                    )
                }
            }
        }

        // 5. Online Leave Confirm Popup
        if (isOnlineMatch && showLeaveOnlineConfirm) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x881A2436))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp)
                    .testTag("leave_online_popup"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 290.dp)
                        .crackedStonePlaque(cornerDp = 22.dp)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = I18n.t("leave_prompt", lang),
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = CartoonOutline,
                        textAlign = TextAlign.Center
                    )
                    StonePlaqueButton(
                        text = I18n.t("leave", lang),
                        onClick = {
                            showLeaveOnlineConfirm = false
                            onLeaveOnlineMatch()
                        },
                        fontSize = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_leave_online_button")
                    )
                    StonePlaqueButton(
                        text = I18n.t("cancel", lang),
                        onClick = { showLeaveOnlineConfirm = false },
                        fontSize = 19.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }
            }
        }
    }
}

/**
 * Small semi-transparent dark panel (rgba(20,24,40,0.55)) with a thin stone border.
 * Shows ONLY Top 5 names with scores + separate last line "YOU: #rank".
 */
@Composable
private fun CompactLeaderboardHud(
    hud: HudState,
    lang: AppLanguage,
    hudScale: Float,
    modifier: Modifier = Modifier
) {
    val rowFont = (11.5f * hudScale).coerceIn(8.5f, 15.5f).sp
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x8C141828)) // rgba(20, 24, 40, 0.55)
            .border(1.3.dp, StoneLight.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        hud.leaderboard.take(5).forEach { entry ->
            val rowColor = if (entry.isPlayer) Color(0xFF6EF285) else Color.White
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.rank}.${entry.name}",
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = rowFont,
                    color = rowColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${entry.score}",
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = rowFont,
                    color = rowColor,
                    maxLines = 1
                )
            }
        }
        // Separate last line: "YOU: #rank"
        Text(
            text = I18n.t("you_rank", lang).format(hud.playerRank),
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = rowFont,
            color = GoldCoinYellow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * Compact top-center coin progress bar: bar height 8.dp * hudScale, text 11.sp * hudScale.
 */
@Composable
private fun CompactCoinProgressHud(
    progress: Int,
    target: Int,
    lang: AppLanguage,
    hudScale: Float,
    modifier: Modifier = Modifier
) {
    val frac = (progress.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
    val textSp = (11f * hudScale).coerceIn(8.5f, 15f).sp
    val barH = (8f * hudScale).coerceIn(5f, 12f).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x8C141828))
            .border(1.2.dp, StoneLight.copy(alpha = 0.82f), RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${I18n.t("collect_coins", lang).format(target)} ($progress/$target)",
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = textSp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barH)
        ) {
            val cr = CornerRadius(size.height * 0.5f, size.height * 0.5f)
            drawRoundRect(CartoonOutline, Offset.Zero, size, cr)
            drawRoundRect(
                color = GrassProgressGreen,
                topLeft = Offset(1.5f, 1.5f),
                size = Size(((size.width - 3f) * frac).coerceAtLeast(0f), (size.height - 3f).coerceAtLeast(2f)),
                cornerRadius = cr
            )
        }
    }
}

@Composable
private fun PingStatusBadge(pingMs: Int?, hudScale: Float) {
    val dotColor = when {
        pingMs == null -> Color(0xFF98A2B3)
        pingMs < 80 -> Color(0xFF38E54D)   // Green <80ms
        pingMs < 160 -> Color(0xFFFACC15)  // Yellow <160ms
        else -> Color(0xFFF97066)          // Red >=160ms
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x99141828))
            .border(1.dp, StoneLight.copy(alpha = 0.75f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("hud_ping_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size((6.5f * hudScale).coerceIn(5f, 9f).dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = if (pingMs != null) "${pingMs}ms" else "--ms",
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (10f * hudScale).coerceIn(8f, 13f).sp,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun StoneVirtualJoystick(
    outerDp: Dp,
    knobDp: Dp,
    onAngleChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val maxR = with(density) { ((outerDp - knobDp) / 2).coerceAtLeast(8.dp).toPx() }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .size(outerDp)
            .pointerInput(outerDp, knobDp) {
                detectDragGestures(
                    onDragEnd = { knobOffset = Offset.Zero },
                    onDragCancel = { knobOffset = Offset.Zero },
                    onDrag = { change, _ ->
                        val c = Offset(size.width * 0.5f, size.height * 0.5f)
                        val d = change.position - c
                        val dist = hypot(d.x, d.y)
                        if (dist > 4f) {
                            onAngleChange(atan2(d.y, d.x))
                            val cl = dist.coerceAtMost(maxR)
                            knobOffset = Offset((d.x / dist) * cl, (d.y / dist) * cl)
                        }
                    }
                )
            }
            .testTag("virtual_joystick"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.5f
            val c = Offset(r, r)
            drawCircle(StoneBorder.copy(alpha = 0.85f), radius = r, center = c)
            drawCircle(StoneMid.copy(alpha = 0.65f), radius = r * 0.88f, center = c)
            drawCircle(StoneBorder.copy(alpha = 0.80f), radius = r * 0.88f, center = c, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }
        Canvas(
            modifier = Modifier
                .offset { IntOffset(knobOffset.x.roundToInt(), knobOffset.y.roundToInt()) }
                .size(knobDp)
        ) {
            val r = size.minDimension * 0.5f
            val c = Offset(r, r)
            drawCircle(StoneBorder, radius = r, center = c)
            drawCircle(StoneLight, radius = r * 0.82f, center = c)
        }
    }
}

/**
 * SCREEN 4: GAME OVER
 * Bright stone-plaque popup showing GAME OVER, Score, Length, Kills, Rank, Coins, PLAY AGAIN, and HOME.
 */
@Composable
fun GameOverScreen(
    summary: MatchEndSummary,
    lang: AppLanguage,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit
) {
    BackHandler(onBack = onHome)

    SkyAndFoliageBackground(
        modifier = Modifier.testTag("game_over_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp), // Safe-area + 12.dp extra padding
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 380.dp)
                    .crackedStonePlaque(cornerDp = 26.dp)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CartoonOutlinedText(
                    text = I18n.t("game_over", lang),
                    fontSize = 34.sp,
                    fillColor = Color.White,
                    strokeWidthPx = 11f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StoneStatItem(I18n.t("score", lang), "${summary.score}")
                    StoneStatItem(I18n.t("length", lang), "${summary.length}")
                    StoneStatItem(I18n.t("kills", lang), "${summary.kills}")
                    StoneStatItem(I18n.t("rank", lang), "#${summary.rank}")
                }

                CartoonOutlinedText(
                    text = "+${summary.coinsEarned} ${I18n.t("coins", lang)}",
                    fontSize = 20.sp,
                    fillColor = GoldCoinYellow,
                    strokeWidthPx = 8f
                )

                StonePlaqueButton(
                    text = I18n.t("play_again", lang),
                    onClick = onPlayAgain,
                    fontSize = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("play_again_button")
                )

                StonePlaqueButton(
                    text = I18n.t("home", lang),
                    onClick = onHome,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("gameover_home_button")
                )
            }
        }
    }
}

@Composable
private fun StoneStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CartoonOutlinedText(text = value, fontSize = 24.sp, strokeWidthPx = 8f)
        Text(
            text = label,
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = CartoonOutline
        )
    }
}
