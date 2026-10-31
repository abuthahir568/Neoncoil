package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.config.AppLanguage
import com.example.config.I18n
import com.example.config.SnakeSkin
import com.example.config.SkinsConfig
import com.example.services.ControlScheme
import com.example.services.GraphicsQuality
import com.example.services.PlayerProfile
import com.example.services.UserSettings
import com.example.ui.theme.CartoonOutline
import com.example.ui.theme.GoldCoinYellow
import com.example.ui.theme.LeafMid
import com.example.ui.theme.NunitoFontFamily
import com.example.ui.theme.StoneBorder
import com.example.ui.theme.StoneLight
import com.example.ui.theme.StoneMid
import com.example.ui.theme.SunnyOrange
import kotlin.math.roundToInt

/**
 * SKINS SCREEN:
 * Safe-area + 12.dp padding on all edges.
 * Stone-style panel with 14 colorful snake skins unlocked with coins earned in matches,
 * plus a live animated preview snake on top.
 */
@Composable
fun SkinsScreen(
    profile: PlayerProfile,
    onSelectOrUnlock: (SnakeSkin) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val lang = profile.settings.language
    val selectedSkin = SkinsConfig.getById(profile.selectedSkinId)

    SkyAndFoliageBackground(
        modifier = Modifier.testTag("skins_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp), // Safe-area + 12.dp extra padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundStoneButton(
                    iconType = StoneRoundIconType.BACK_ARROW,
                    contentDescription = stringResource(R.string.cd_back_button),
                    onClick = onBack,
                    sizeDp = 50.dp,
                    modifier = Modifier.testTag("skins_back_button")
                )

                StoneBannerPlaque(
                    title = I18n.t("skins", lang),
                    fontSize = 26.sp,
                    verticalPad = 8.dp,
                    modifier = Modifier.widthIn(min = 140.dp)
                )

                Box(
                    modifier = Modifier
                        .crackedStonePlaque(cornerDp = 14.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    CartoonOutlinedText(
                        text = "★ ${profile.coins}",
                        fontSize = 18.sp,
                        fillColor = GoldCoinYellow,
                        strokeWidthPx = 7f
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .crackedStonePlaque(cornerDp = 20.dp)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CartoonOutlinedText(
                    text = selectedSkin.name.uppercase(),
                    fontSize = 20.sp,
                    strokeWidthPx = 7.5f
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(82.dp)
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF6CC3FF))
                        .border(2.5.dp, CartoonOutline, RoundedCornerShape(14.dp))
                ) {
                    SnakePreviewCanvas(skin = selectedSkin)
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(SkinsConfig.allSkins, key = { it.id }) { skin ->
                    val owned = profile.unlockedSkins.contains(skin.id)
                    val isEquipped = profile.selectedSkinId == skin.id

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .crackedStonePlaque(cornerDp = 18.dp)
                            .padding(10.dp)
                            .testTag("skin_item_${skin.id}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = skin.name,
                            fontFamily = NunitoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = CartoonOutline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF7CD0FF))
                                .border(2.dp, CartoonOutline, RoundedCornerShape(12.dp))
                        ) {
                            SnakePreviewCanvas(skin = skin, segmentCount = 10, animated = false)
                        }
                        Spacer(Modifier.height(8.dp))

                        val btnText = when {
                            isEquipped -> I18n.t("equipped", lang)
                            owned -> I18n.t("equip", lang)
                            else -> "${skin.priceCoins} ★"
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isEquipped -> LeafMid
                                        owned -> Color(0xFF4CA8FF)
                                        else -> SunnyOrange
                                    }
                                )
                                .border(2.5.dp, CartoonOutline, RoundedCornerShape(12.dp))
                                .clickable { onSelectOrUnlock(skin) }
                                .testTag("skin_btn_${skin.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            CartoonOutlinedText(
                                text = btnText,
                                fontSize = 16.sp,
                                strokeWidthPx = 6.5f
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * SETTINGS SCREEN:
 * Safe-area + 12.dp extra padding on ALL edges.
 * Includes Controls Size slider (50%..150%, default 85%), HUD Size slider (60%..140%, default 80%),
 * Live Preview box, RESET button, Sound/Music/Vibration toggles, Control Scheme, Graphics, and Language.
 */
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onUpdate: ((UserSettings) -> UserSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val lang = settings.language
    val cdControlsSlider = stringResource(R.string.cd_controls_size_slider)
    val cdHudSlider = stringResource(R.string.cd_hud_size_slider)

    SkyAndFoliageBackground(
        modifier = Modifier.testTag("settings_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp) // Safe-area + 12.dp extra padding
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                RoundStoneButton(
                    iconType = StoneRoundIconType.BACK_ARROW,
                    contentDescription = stringResource(R.string.cd_back_button),
                    onClick = onBack,
                    sizeDp = 50.dp,
                    modifier = Modifier.testTag("settings_back_button")
                )
                StoneBannerPlaque(
                    title = I18n.t("settings", lang),
                    fontSize = 26.sp,
                    verticalPad = 8.dp
                )
                Spacer(Modifier.width(50.dp))
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .crackedStonePlaque(cornerDp = 24.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Controls Size Slider (50% to 150%, default 85%)
                SizeSliderRow(
                    title = I18n.t("controls_size", lang),
                    percent = settings.controlsSizePercent,
                    valueRange = 50f..150f,
                    contentDesc = cdControlsSlider,
                    tag = "slider_controls_size",
                    onPercentChange = { pct ->
                        onUpdate { it.copy(controlsSizePercent = pct.coerceIn(50, 150)) }
                    }
                )

                // 2. HUD Size Slider (60% to 140%, default 80%)
                SizeSliderRow(
                    title = I18n.t("hud_size", lang),
                    percent = settings.hudSizePercent,
                    valueRange = 60f..140f,
                    contentDesc = cdHudSlider,
                    tag = "slider_hud_size",
                    onPercentChange = { pct ->
                        onUpdate { it.copy(hudSizePercent = pct.coerceIn(60, 140)) }
                    }
                )

                // 3. Live Scaled Preview + RESET Button
                HudAndControlsLivePreview(
                    hudScale = settings.hudScale,
                    ctrlScale = settings.controlsScale,
                    lang = lang,
                    onReset = {
                        onUpdate {
                            it.copy(
                                controlsSizePercent = UserSettings.DEFAULT_CONTROLS_SIZE,
                                hudSizePercent = UserSettings.DEFAULT_HUD_SIZE
                            )
                        }
                    }
                )

                StoneSwitchRow(
                    label = I18n.t("sound", lang),
                    checked = settings.soundEnabled,
                    tag = "toggle_sound"
                ) { v -> onUpdate { it.copy(soundEnabled = v) } }

                StoneSwitchRow(
                    label = I18n.t("music", lang),
                    checked = settings.musicEnabled,
                    tag = "toggle_music"
                ) { v -> onUpdate { it.copy(musicEnabled = v) } }

                StoneSwitchRow(
                    label = I18n.t("vibration", lang),
                    checked = settings.vibrationEnabled,
                    tag = "toggle_vibration"
                ) { v -> onUpdate { it.copy(vibrationEnabled = v) } }

                // Controls: Joystick vs Touch-Drag
                CartoonOutlinedText(
                    text = I18n.t("controls", lang),
                    fontSize = 19.sp,
                    strokeWidthPx = 7f
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CartoonOptionButton(
                        label = I18n.t("joystick", lang),
                        selected = settings.controlScheme == ControlScheme.JOYSTICK,
                        modifier = Modifier.weight(1f),
                        tag = "ctrl_joystick"
                    ) { onUpdate { it.copy(controlScheme = ControlScheme.JOYSTICK) } }

                    CartoonOptionButton(
                        label = I18n.t("touch_drag", lang),
                        selected = settings.controlScheme == ControlScheme.TOUCH_DRAG,
                        modifier = Modifier.weight(1f),
                        tag = "ctrl_touch_drag"
                    ) { onUpdate { it.copy(controlScheme = ControlScheme.TOUCH_DRAG) } }
                }

                // Graphics Quality: Low / Med / High
                CartoonOutlinedText(
                    text = I18n.t("graphics", lang),
                    fontSize = 19.sp,
                    strokeWidthPx = 7f
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GraphicsQuality.entries.forEach { q ->
                        val qText = when (q) {
                            GraphicsQuality.LOW -> I18n.t("low", lang)
                            GraphicsQuality.MEDIUM -> I18n.t("med", lang)
                            GraphicsQuality.HIGH -> I18n.t("high", lang)
                        }
                        CartoonOptionButton(
                            label = qText,
                            selected = settings.graphicsQuality == q,
                            modifier = Modifier.weight(1f),
                            tag = "gfx_${q.name.lowercase()}"
                        ) { onUpdate { it.copy(graphicsQuality = q) } }
                    }
                }

                // Language: English, Tamil, Hindi
                CartoonOutlinedText(
                    text = I18n.t("language", lang),
                    fontSize = 19.sp,
                    strokeWidthPx = 7f
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { opt ->
                        CartoonOptionButton(
                            label = opt.nativeName,
                            selected = settings.language == opt,
                            modifier = Modifier.weight(1f),
                            tag = "lang_${opt.code}"
                        ) { onUpdate { it.copy(language = opt) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeSliderRow(
    title: String,
    percent: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    contentDesc: String,
    tag: String,
    onPercentChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = CartoonOutline
            )
            CartoonOutlinedText(
                text = "$percent%",
                fontSize = 18.sp,
                fillColor = GoldCoinYellow,
                strokeWidthPx = 6.5f
            )
        }
        // Minimum 48.dp tap height for slider accessibility
        Slider(
            value = percent.toFloat(),
            onValueChange = { onPercentChange(it.roundToInt()) },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = GoldCoinYellow,
                activeTrackColor = LeafMid,
                inactiveTrackColor = Color(0xFF5D6675)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = contentDesc }
                .testTag(tag)
        )
    }
}

@Composable
private fun HudAndControlsLivePreview(
    hudScale: Float,
    ctrlScale: Float,
    lang: AppLanguage,
    onReset: () -> Unit
) {
    val miniFactor = 0.50f // Scale preview container at 50% of full screen
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = I18n.t("live_preview", lang),
                fontFamily = NunitoFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = CartoonOutline
            )
            StonePlaqueButton(
                text = I18n.t("reset", lang),
                onClick = onReset,
                fontSize = 15.sp,
                modifier = Modifier
                    .width(96.dp)
                    .height(48.dp)
                    .testTag("btn_reset_sizes")
            )
        }

        // Miniature dark woven arena showing real-time HUD & Controls proportions
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF18202D))
                .border(2.5.dp, CartoonOutline, RoundedCornerShape(14.dp))
                .padding(8.dp)
                .testTag("settings_live_preview")
        ) {
            // Top HUD row preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Leaderboard preview
                Box(
                    modifier = Modifier
                        .width((64f * hudScale).dp)
                        .height((34f * hudScale).dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x8C141828))
                        .border(1.dp, StoneLight, RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "1.Sunny\nYOU:#2",
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (7.5f * hudScale).coerceIn(6f, 11f).sp,
                        lineHeight = (9f * hudScale).coerceIn(7f, 12f).sp,
                        color = Color.White
                    )
                }

                // Coin + Score preview
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .width((68f * hudScale).dp)
                            .height((12f * hudScale).dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(LeafMid)
                            .border(1.dp, CartoonOutline, RoundedCornerShape(5.dp))
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "240",
                        fontFamily = NunitoFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = (8.5f * hudScale).coerceIn(7f, 12f).sp,
                        color = Color.White
                    )
                }

                // Pause + Minimap preview
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .size((38f * miniFactor * hudScale).dp)
                            .clip(CircleShape)
                            .background(StoneLight)
                            .border(1.dp, StoneBorder, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size((70f * miniFactor * hudScale).dp)
                            .clip(CircleShape)
                            .background(Color(0xEE1A2232))
                            .border(1.5.dp, StoneMid, CircleShape)
                    )
                }
            }

            // Bottom Controls + Energy preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Joystick preview
                Canvas(
                    modifier = Modifier
                        .size((92f * miniFactor * ctrlScale).dp)
                        .alpha(0.75f)
                ) {
                    val r = size.minDimension * 0.5f
                    val c = Offset(r, r)
                    drawCircle(StoneMid, radius = r, center = c)
                    drawCircle(StoneLight, radius = r * 0.42f, center = c)
                }

                // Energy bar preview
                BoostEnergyHeartBar(
                    energy = 0.8f,
                    heartSizeDp = (16f * hudScale).dp,
                    barHeightDp = (9f * hudScale).dp,
                    modifier = Modifier.width((76f * hudScale).dp)
                )

                // Boost button preview
                Box(
                    modifier = Modifier
                        .size((62f * miniFactor * ctrlScale).dp)
                        .alpha(0.75f)
                        .clip(CircleShape)
                        .background(GoldCoinYellow)
                        .border(1.5.dp, CartoonOutline, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun StoneSwitchRow(
    label: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        CartoonOutlinedText(text = label, fontSize = 20.sp, strokeWidthPx = 7f)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = LeafMid,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFF6D7582)
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun CartoonOptionButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) LeafMid else Color(0xFF7F8794))
            .border(2.5.dp, CartoonOutline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = NunitoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1
        )
    }
}
