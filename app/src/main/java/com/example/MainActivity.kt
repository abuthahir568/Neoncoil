package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.CartoonOutlinedText
import com.example.ui.GameOverScreen
import com.example.ui.GameScreen
import com.example.ui.HomeScreen
import com.example.ui.NeonCoilViewModel
import com.example.ui.ScreenRoute
import com.example.ui.SettingsScreen
import com.example.ui.SkinsScreen
import com.example.ui.SkyAndFoliageBackground
import com.example.ui.SplashScreen
import com.example.ui.StonePlaqueButton
import com.example.ui.crackedStonePlaque
import com.example.ui.theme.GoldCoinYellow
import com.example.ui.theme.NeonCoilTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeonCoilTheme {
                NeonCoilRoot()
            }
        }
    }
}

@Composable
fun NeonCoilRoot(
    vm: NeonCoilViewModel = viewModel()
) {
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Auto-pause offline run & save snapshot when app loses focus; disconnect online run after 10s
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> vm.onForegroundChanged(true)
                Lifecycle.Event.ON_PAUSE -> vm.onForegroundChanged(false)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.toastMessage) {
        if (uiState.toastMessage != null) {
            delay(2200L)
            vm.clearToast()
        }
    }

    if (uiState.fatalError != null) {
        CartoonRestartScreen(
            message = uiState.fatalError ?: "Unexpected error",
            onRestart = vm::restartFromError
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = uiState.screen,
            animationSpec = tween(durationMillis = 280),
            label = "screen_crossfade"
        ) { screen ->
            when (screen) {
                ScreenRoute.SPLASH -> SplashScreen(
                    lang = uiState.profile.settings.language,
                    onFinished = vm::finishSplashToHome
                )

                ScreenRoute.HOME -> HomeScreen(
                    profile = uiState.profile,
                    hasSavedRun = uiState.hasSavedOfflineRun,
                    savedScore = uiState.savedOfflineScore,
                    onlineCount = uiState.onlineCount,
                    isConnectingOnline = uiState.isConnectingOnline,
                    serverUnreachableMessage = uiState.serverUnreachableMessage,
                    onContinueSavedRun = vm::continueSavedOfflineRun,
                    onStartFreshOffline = vm::startFreshOfflineRun,
                    onSaveNickname = vm::saveNickname,
                    onPlayOnlinePublic = vm::startOnlinePublic,
                    onCreatePrivateRoom = vm::createPrivateOnlineRoom,
                    onJoinPrivateRoom = vm::joinPrivateOnlineRoom,
                    onRetryOnlineConnect = vm::retryOnlineConnection,
                    onCancelOnlineConnectAndPlayOffline = vm::startFreshOfflineRun,
                    onDismissServerPopup = vm::dismissOnlinePopup,
                    onOpenSkins = { vm.navigateTo(ScreenRoute.SKINS) },
                    onOpenSettings = { vm.navigateTo(ScreenRoute.SETTINGS) }
                )

                ScreenRoute.GAME -> GameScreen(
                    renderSource = vm.activeRenderSource,
                    hud = uiState.hud,
                    settings = uiState.profile.settings,
                    isOnlineMatch = uiState.isOnlineMatch,
                    isPausedModalOpen = uiState.isPausedModalOpen,
                    resumeCountdownNumber = uiState.resumeCountdownNumber,
                    onPlayerInput = vm::onPlayerInput,
                    onFrameTick = vm::onGameFrame,
                    onOpenPauseModal = vm::pauseOfflineImmediately,
                    onResumeWithCountdown = vm::resumeOfflineWithCountdown,
                    onRestartOffline = vm::startFreshOfflineRun,
                    onOpenSettingsFromPause = vm::openSettingsFromPauseMenu,
                    onExitHomeSavingSession = vm::exitPausedGameToHome,
                    onLeaveOnlineMatch = vm::leaveOnlineMatchToHome,
                    onTogglePrivateRoomBots = vm::togglePrivateRoomBots
                )

                ScreenRoute.GAME_OVER -> {
                    val summary = uiState.matchSummary
                    if (summary != null) {
                        GameOverScreen(
                            summary = summary,
                            lang = uiState.profile.settings.language,
                            onPlayAgain = vm::playAgainAfterGameOver,
                            onHome = {
                                if (uiState.isOnlineMatch) {
                                    vm.leaveOnlineMatchToHome()
                                } else {
                                    vm.navigateTo(ScreenRoute.HOME)
                                }
                            }
                        )
                    } else {
                        vm.navigateTo(ScreenRoute.HOME)
                    }
                }

                ScreenRoute.SKINS -> SkinsScreen(
                    profile = uiState.profile,
                    onSelectOrUnlock = vm::selectOrUnlockSkin,
                    onBack = { vm.navigateTo(ScreenRoute.HOME) }
                )

                ScreenRoute.SETTINGS -> SettingsScreen(
                    settings = uiState.profile.settings,
                    onUpdate = vm::updateSettings,
                    onBack = vm::navigateBackFromSettings
                )
            }
        }

        // Stone-Plaque Toast Notification (safe-area + >=12.dp padding)
        AnimatedVisibility(
            visible = uiState.toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 56.dp, start = 12.dp, end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .crackedStonePlaque(cornerDp = 16.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                CartoonOutlinedText(
                    text = uiState.toastMessage.orEmpty(),
                    fontSize = 17.sp,
                    fillColor = GoldCoinYellow,
                    strokeWidthPx = 6.5f
                )
            }
        }
    }
}

@Composable
private fun CartoonRestartScreen(
    message: String,
    onRestart: () -> Unit
) {
    SkyAndFoliageBackground(modifier = Modifier.testTag("error_boundary_screen")) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .crackedStonePlaque(cornerDp = 24.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CartoonOutlinedText(text = "OOPS!", fontSize = 32.sp, strokeWidthPx = 10f)
                Spacer(Modifier.height(8.dp))
                CartoonOutlinedText(text = message, fontSize = 16.sp, strokeWidthPx = 6f)
                Spacer(Modifier.height(18.dp))
                StonePlaqueButton(
                    text = "RESTART",
                    onClick = onRestart,
                    modifier = Modifier
                        .width(200.dp)
                        .height(54.dp)
                        .testTag("restart_button")
                )
            }
        }
    }
}
