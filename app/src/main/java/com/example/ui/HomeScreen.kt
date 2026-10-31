package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.config.AppLanguage
import com.example.config.I18n
import com.example.config.ServerConfig
import com.example.config.SkinsConfig
import com.example.services.PlayerProfile
import com.example.ui.theme.CartoonOutline
import com.example.ui.theme.GoldCoinYellow
import com.example.ui.theme.LeafMid
import com.example.ui.theme.NunitoFontFamily

enum class PendingOnlineAction {
    PUBLIC,
    OPEN_FRIENDS
}

/**
 * SCREEN 2: SIMPLIFIED HOME MENU
 * - Safe-area + 12.dp extra padding on ALL edges; fits without scrolling from 360x640 up to large phones.
 * - Top: Cracked grey stone "NEONCOIL" banner.
 * - Middle: Bigger idle animated mascot snake.
 * - Action Stack:
 *   1) Optional CONTINUE button ABOVE START (with saved score in small text) when a paused run exists.
 *   2) Big START stone button (Infinite endless mode; asks confirmation if a saved run exists).
 *   3) PLAY ONLINE stone button (with green dot + "NN online") + smaller FRIENDS button next to it.
 * - Bottom Corners: 52.dp round SKINS (bottom-left) and SETTINGS (bottom-right) stone buttons with 11.sp labels.
 */
@Composable
fun HomeScreen(
    profile: PlayerProfile,
    hasSavedRun: Boolean,
    savedScore: Int,
    onlineCount: Int,
    isConnectingOnline: Boolean,
    serverUnreachableMessage: String?,
    onContinueSavedRun: () -> Unit,
    onStartFreshOffline: () -> Unit,
    onSaveNickname: (String) -> Unit,
    onPlayOnlinePublic: () -> Unit,
    onCreatePrivateRoom: (Boolean) -> Unit,
    onJoinPrivateRoom: (String) -> Unit,
    onRetryOnlineConnect: () -> Unit,
    onCancelOnlineConnectAndPlayOffline: () -> Unit,
    onDismissServerPopup: () -> Unit,
    onOpenSkins: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val lang = profile.settings.language
    val currentSkin = SkinsConfig.getById(profile.selectedSkinId)

    var showConfirmNewGame by remember { mutableStateOf(false) }
    var pendingOnlineAction by remember { mutableStateOf<PendingOnlineAction?>(null) }
    var showFriendsPopup by remember { mutableStateOf(false) }

    val handleRequestOnline: (PendingOnlineAction) -> Unit = { action ->
        if (ServerConfig.validateNickname(profile.nickname) == null) {
            pendingOnlineAction = action
        } else {
            when (action) {
                PendingOnlineAction.PUBLIC -> onPlayOnlinePublic()
                PendingOnlineAction.OPEN_FRIENDS -> showFriendsPopup = true
            }
        }
    }

    SkyAndFoliageBackground(
        modifier = Modifier.testTag("home_screen")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp) // Safe area + 12px extra on ALL edges
        ) {
            val availW = maxWidth
            val availH = maxHeight
            val compactVertical = availH < 600.dp

            val bannerFont = if (compactVertical) 30.sp else 36.sp
            val bannerVertPad = if (compactVertical) 7.dp else 10.dp
            val mascotHeight = (availH * (if (hasSavedRun) 0.18f else 0.23f)).coerceIn(86.dp, 156.dp)
            val mascotWidth = (availW * 0.76f).coerceIn(220.dp, 320.dp)
            val primaryBtnH = (availH * 0.088f).coerceIn(48.dp, 60.dp)
            val secondaryBtnH = (availH * 0.082f).coerceIn(48.dp, 56.dp)
            val stackGap = if (compactVertical) 7.dp else 10.dp
            val mainStackWidth = (availW * 0.84f).coerceIn(240.dp, 330.dp)

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Stone "NEONCOIL" Banner (safely below safe-area top + 12.dp)
                StoneBannerPlaque(
                    title = "NEONCOIL",
                    fontSize = bannerFont,
                    verticalPad = bannerVertPad,
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .widthIn(max = 360.dp)
                        .testTag("home_title_banner")
                )

                // 2. Center Mascot Snake (bigger!) + Stone Action Stack (no scrolling needed)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(stackGap)
                ) {
                    Box(
                        modifier = Modifier
                            .width(mascotWidth)
                            .height(mascotHeight)
                            .testTag("home_mascot_snake")
                    ) {
                        SnakePreviewCanvas(skin = currentSkin, segmentCount = 15, animated = true)
                    }

                    // Optional CONTINUE button ABOVE START when a paused run exists
                    if (hasSavedRun) {
                        StonePlaqueButton(
                            text = I18n.t("continue_run", lang),
                            subtitle = "${I18n.t("score", lang)}: $savedScore",
                            onClick = onContinueSavedRun,
                            fontSize = if (compactVertical) 21.sp else 24.sp,
                            wobblePhaseOffsetMs = 0,
                            modifier = Modifier
                                .width(mainStackWidth)
                                .height(primaryBtnH)
                                .testTag("btn_continue_run")
                        )
                    }

                    // Big START Stone Button (Infinite endless mode)
                    StonePlaqueButton(
                        text = I18n.t("start", lang),
                        onClick = {
                            if (hasSavedRun) {
                                showConfirmNewGame = true
                            } else {
                                onStartFreshOffline()
                            }
                        },
                        fontSize = if (compactVertical) 24.sp else 27.sp,
                        wobblePhaseOffsetMs = 200,
                        modifier = Modifier
                            .width(mainStackWidth)
                            .height(primaryBtnH)
                            .testTag("btn_mode_start")
                    )

                    // PLAY ONLINE + smaller FRIENDS stone button next to it
                    Row(
                        modifier = Modifier.width(mainStackWidth),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StonePlaqueButton(
                            text = I18n.t("play_online", lang),
                            subtitle = I18n.t("online_count", lang).format(onlineCount),
                            showSubtitleGreenDot = true,
                            onClick = { handleRequestOnline(PendingOnlineAction.PUBLIC) },
                            fontSize = if (compactVertical) 17.sp else 19.sp,
                            wobblePhaseOffsetMs = 420,
                            modifier = Modifier
                                .weight(1.45f)
                                .height(secondaryBtnH)
                                .testTag("btn_play_online")
                        )

                        StonePlaqueButton(
                            text = I18n.t("friends", lang),
                            onClick = { handleRequestOnline(PendingOnlineAction.OPEN_FRIENDS) },
                            fontSize = if (compactVertical) 16.sp else 17.sp,
                            wobblePhaseOffsetMs = 640,
                            modifier = Modifier
                                .weight(1.0f)
                                .height(secondaryBtnH)
                                .testTag("btn_friends_room")
                        )
                    }
                }

                // 3. Bottom Corners: 52.dp round SKINS & SETTINGS stone buttons with 11.sp labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    RoundStoneButton(
                        iconType = StoneRoundIconType.SKINS_SNAKE,
                        contentDescription = stringResource(R.string.cd_skins_button),
                        onClick = onOpenSkins,
                        sizeDp = 52.dp,
                        caption = I18n.t("skins", lang),
                        captionFontSize = 11.sp,
                        modifier = Modifier.testTag("home_skins_button")
                    )

                    RoundStoneButton(
                        iconType = StoneRoundIconType.SETTINGS_GEAR,
                        contentDescription = stringResource(R.string.cd_settings_button),
                        onClick = onOpenSettings,
                        sizeDp = 52.dp,
                        caption = I18n.t("settings", lang),
                        captionFontSize = 11.sp,
                        modifier = Modifier.testTag("home_settings_button")
                    )
                }
            }

            // POPUP 1: Confirm overwriting saved run ("Start a new game? Your current run will be lost.")
            if (showConfirmNewGame) {
                StoneModalScrim(tag = "confirm_new_game_popup") {
                    Text(
                        text = I18n.t("new_game_prompt", lang),
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = CartoonOutline,
                        textAlign = TextAlign.Center
                    )
                    StonePlaqueButton(
                        text = I18n.t("new_game", lang),
                        onClick = {
                            showConfirmNewGame = false
                            onStartFreshOffline()
                        },
                        fontSize = 21.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_confirm_new_game")
                    )
                    StonePlaqueButton(
                        text = I18n.t("cancel", lang),
                        onClick = { showConfirmNewGame = false },
                        fontSize = 19.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_cancel_new_game")
                    )
                }
            }

            // POPUP 2: Nickname Entry (3–12 clean chars before first online game)
            if (pendingOnlineAction != null) {
                NicknameStonePopup(
                    initialNick = profile.nickname,
                    lang = lang,
                    onConfirm = { validNick ->
                        onSaveNickname(validNick)
                        val act = pendingOnlineAction
                        pendingOnlineAction = null
                        if (act == PendingOnlineAction.PUBLIC) {
                            onPlayOnlinePublic()
                        } else if (act == PendingOnlineAction.OPEN_FRIENDS) {
                            showFriendsPopup = true
                        }
                    },
                    onCancel = { pendingOnlineAction = null }
                )
            }

            // POPUP 3: Friends Private Room (CREATE ROOM with Bots toggle + JOIN 4-letter code)
            if (showFriendsPopup) {
                FriendsPrivateRoomPopup(
                    lang = lang,
                    onCreateRoom = { botsOn ->
                        showFriendsPopup = false
                        onCreatePrivateRoom(botsOn)
                    },
                    onJoinRoom = { code ->
                        showFriendsPopup = false
                        onJoinPrivateRoom(code)
                    },
                    onCancel = { showFriendsPopup = false }
                )
            }

            // POPUP 4: Connecting / Can't Reach Server Fallback
            if (isConnectingOnline || serverUnreachableMessage != null) {
                StoneModalScrim(tag = "server_status_popup") {
                    if (isConnectingOnline && serverUnreachableMessage == null) {
                        CircularProgressIndicator(color = GoldCoinYellow)
                        CartoonOutlinedText(
                            text = I18n.t("connecting", lang),
                            fontSize = 22.sp,
                            strokeWidthPx = 8f
                        )
                        StonePlaqueButton(
                            text = I18n.t("cancel", lang),
                            onClick = onDismissServerPopup,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )
                    } else {
                        Text(
                            text = serverUnreachableMessage ?: I18n.t("cant_reach_server", lang),
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = CartoonOutline,
                            textAlign = TextAlign.Center
                        )
                        StonePlaqueButton(
                            text = I18n.t("play_offline", lang),
                            onClick = onCancelOnlineConnectAndPlayOffline,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_fallback_play_offline")
                        )
                        StonePlaqueButton(
                            text = I18n.t("retry", lang),
                            onClick = onRetryOnlineConnect,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_retry_online")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NicknameStonePopup(
    initialNick: String,
    lang: AppLanguage,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var draft by remember { mutableStateOf(initialNick) }
    var showError by remember { mutableStateOf(false) }

    StoneModalScrim(tag = "nickname_popup") {
        CartoonOutlinedText(
            text = I18n.t("nickname_title", lang),
            fontSize = 26.sp,
            strokeWidthPx = 9f
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(2.5.dp, CartoonOutline, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (draft.isEmpty()) {
                Text(
                    text = I18n.t("nickname_hint", lang),
                    fontFamily = NunitoFontFamily,
                    fontSize = 14.sp,
                    color = Color(0xFF7A828F)
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = {
                    if (it.length <= 12) {
                        draft = it
                        showError = false
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = NunitoFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = CartoonOutline
                ),
                cursorBrush = SolidColor(CartoonOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nickname_input")
            )
        }
        if (showError) {
            Text(
                text = I18n.t("nickname_invalid", lang),
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFFB42318)
            )
        }
        StonePlaqueButton(
            text = I18n.t("save_play", lang),
            onClick = {
                val valid = ServerConfig.validateNickname(draft)
                if (valid != null) {
                    onConfirm(valid)
                } else {
                    showError = true
                }
            },
            fontSize = 21.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_save_nickname")
        )
        StonePlaqueButton(
            text = I18n.t("cancel", lang),
            onClick = onCancel,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )
    }
}

@Composable
private fun FriendsPrivateRoomPopup(
    lang: AppLanguage,
    onCreateRoom: (Boolean) -> Unit,
    onJoinRoom: (String) -> Unit,
    onCancel: () -> Unit
) {
    var codeInput by remember { mutableStateOf("") }
    var botsEnabled by remember { mutableStateOf(true) }

    StoneModalScrim(tag = "friends_popup") {
        CartoonOutlinedText(
            text = I18n.t("friends", lang),
            fontSize = 26.sp,
            strokeWidthPx = 9f
        )

        // Host Bots On/Off Toggle + CREATE ROOM
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = I18n.t("bots_toggle", lang),
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = CartoonOutline
            )
            Switch(
                checked = botsEnabled,
                onCheckedChange = { botsEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LeafMid
                ),
                modifier = Modifier.testTag("private_room_bots_switch")
            )
        }

        StonePlaqueButton(
            text = I18n.t("create_room", lang),
            onClick = { onCreateRoom(botsEnabled) },
            fontSize = 20.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("btn_create_private_room")
        )

        // 4-letter Room Code + JOIN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(2.5.dp, CartoonOutline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (codeInput.isEmpty()) {
                    Text(
                        text = "ABCD",
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF98A2B3)
                    )
                }
                BasicTextField(
                    value = codeInput,
                    onValueChange = { raw ->
                        val clean = raw.uppercase().filter { it in 'A'..'Z' }.take(4)
                        codeInput = clean
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 3.sp,
                        textAlign = TextAlign.Center,
                        color = CartoonOutline
                    ),
                    cursorBrush = SolidColor(CartoonOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_room_code")
                )
            }

            StonePlaqueButton(
                text = I18n.t("join_room", lang),
                enabled = codeInput.length == 4,
                onClick = { if (codeInput.length == 4) onJoinRoom(codeInput) },
                fontSize = 19.sp,
                modifier = Modifier
                    .width(106.dp)
                    .height(48.dp)
                    .testTag("btn_join_private_room")
            )
        }

        StonePlaqueButton(
            text = I18n.t("cancel", lang),
            onClick = onCancel,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )
    }
}

@Composable
private fun StoneModalScrim(
    tag: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99162032))
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .widthIn(max = 320.dp)
                .crackedStonePlaque(cornerDp = 22.dp)
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}
