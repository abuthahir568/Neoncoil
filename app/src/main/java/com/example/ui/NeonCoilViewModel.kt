package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.I18n
import com.example.config.ServerConfig
import com.example.config.SnakeSkin
import com.example.config.SkinsConfig
import com.example.engine.ArenaRenderSource
import com.example.engine.GameSessionManager
import com.example.engine.HudState
import com.example.engine.MatchEndSummary
import com.example.engine.OnlineArenaSession
import com.example.engine.OnlineConnectParams
import com.example.engine.OnlineJoinMode
import com.example.services.PlayerProfile
import com.example.services.StorageService
import com.example.services.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ScreenRoute {
    SPLASH,
    HOME,
    GAME,
    GAME_OVER,
    SKINS,
    SETTINGS
}

data class NeonCoilUiState(
    val screen: ScreenRoute = ScreenRoute.SPLASH,
    val returnScreenAfterSettings: ScreenRoute = ScreenRoute.HOME,
    val profile: PlayerProfile = PlayerProfile(),
    val hasSavedOfflineRun: Boolean = false,
    val savedOfflineScore: Int = 0,
    val isOnlineMatch: Boolean = false,
    val isPausedModalOpen: Boolean = false,
    val resumeCountdownNumber: Int? = null,
    val onlineCount: Int = 0,
    val isConnectingOnline: Boolean = false,
    val serverUnreachableMessage: String? = null,
    val hud: HudState = HudState(),
    val matchSummary: MatchEndSummary? = null,
    val toastMessage: String? = null,
    val fatalError: String? = null
)

class NeonCoilViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = StorageService(application)
    val audio = GameSessionManager.audio
    val offlineEngine = GameSessionManager.offlineEngine
    val onlineSession = OnlineArenaSession(audio)

    private val _uiState = MutableStateFlow(NeonCoilUiState())
    val uiState: StateFlow<NeonCoilUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var onlineBgTimeoutJob: Job? = null
    private var lastConnectParams: OnlineConnectParams? = null
    private var offlineSnapshotTimerSec: Float = 0f

    val activeRenderSource: ArenaRenderSource
        get() = if (_uiState.value.isOnlineMatch) onlineSession else offlineEngine

    init {
        GameSessionManager.attachContext(application)
        initApp()
        startOnlineHealthPoller()
    }

    private fun initApp() {
        try {
            val loaded = storage.loadProfile()
            audio.applySettings(loaded.settings.soundEnabled, loaded.settings.musicEnabled)
            GameSessionManager.setVibrationEnabled(loaded.settings.vibrationEnabled)

            offlineEngine.onCoinPickedUp = { amount -> onCoinsPickedUp(amount) }
            offlineEngine.onMatchFinished = { summary -> onMatchEnded(summary) }

            onlineSession.onWelcomeJoined = {
                viewModelScope.launch {
                    audio.setGameplayPaused(false)
                    _uiState.update {
                        it.copy(
                            isConnectingOnline = false,
                            serverUnreachableMessage = null,
                            isOnlineMatch = true,
                            isPausedModalOpen = false,
                            resumeCountdownNumber = null,
                            matchSummary = null,
                            screen = ScreenRoute.GAME,
                            hud = HudState(
                                hudCoinProgress = it.profile.hudCoinProgress,
                                hudCoinTarget = it.profile.hudCoinTarget,
                                isOnline = true,
                                roomCode = onlineSession.roomCode,
                                isPrivateHost = onlineSession.isPrivateHost,
                                privateBotsEnabled = onlineSession.privateBotsEnabled
                            )
                        )
                    }
                }
            }

            onlineSession.onConnectionFailed = { customMsg ->
                viewModelScope.launch {
                    val lang = _uiState.value.profile.settings.language
                    val msg = customMsg ?: I18n.t("cant_reach_server", lang)
                    if (_uiState.value.screen == ScreenRoute.GAME && _uiState.value.isOnlineMatch) {
                        onlineSession.disconnect(sendLeave = false)
                        _uiState.update {
                            it.copy(
                                isOnlineMatch = false,
                                isConnectingOnline = false,
                                screen = ScreenRoute.HOME,
                                serverUnreachableMessage = msg
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isConnectingOnline = false,
                                serverUnreachableMessage = msg
                            )
                        }
                    }
                }
            }

            onlineSession.onCoinsPickedUp = { amount ->
                viewModelScope.launch { onCoinsPickedUp(amount) }
            }

            onlineSession.onPlayerDied = { summary ->
                viewModelScope.launch { onMatchEnded(summary) }
            }

            // Restore saved offline session if one exists on disk
            val hasRestored = GameSessionManager.restoreSavedSessionIfNeeded(
                storage = storage,
                quality = loaded.settings.graphicsQuality
            )

            _uiState.value = NeonCoilUiState(
                screen = ScreenRoute.SPLASH,
                profile = loaded,
                hasSavedOfflineRun = hasRestored && offlineEngine.hasActiveRun,
                savedOfflineScore = offlineEngine.currentPlayerScore
            )
        } catch (t: Throwable) {
            _uiState.update { it.copy(fatalError = t.message ?: "Initialization error") }
        }
    }

    private fun startOnlineHealthPoller() {
        viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.screen == ScreenRoute.HOME) {
                    val count = onlineSession.fetchOnlineCount()
                    _uiState.update { it.copy(onlineCount = count) }
                }
                delay(6000L)
            }
        }
    }

    fun restartFromError() {
        _uiState.update { it.copy(fatalError = null) }
        initApp()
    }

    /**
     * Auto-pause offline run & save snapshot on backgrounding;
     * for online matches, if app stays in background > 10s, disconnect cleanly and return to Home.
     */
    fun onForegroundChanged(inForeground: Boolean) {
        audio.onForegroundChanged(inForeground)
        val state = _uiState.value

        if (!inForeground) {
            if (state.screen == ScreenRoute.GAME && !state.isOnlineMatch) {
                pauseOfflineImmediately()
            } else if (state.screen == ScreenRoute.GAME && state.isOnlineMatch) {
                onlineBgTimeoutJob?.cancel()
                onlineBgTimeoutJob = viewModelScope.launch {
                    delay(ServerConfig.RECONNECT_GRACE_MS)
                    onlineSession.disconnect(sendLeave = true)
                    _uiState.update {
                        it.copy(
                            isOnlineMatch = false,
                            screen = ScreenRoute.HOME,
                            toastMessage = "Disconnected after 10s in background"
                        )
                    }
                }
            }
        } else {
            onlineBgTimeoutJob?.cancel()
            onlineBgTimeoutJob = null
        }
    }

    fun finishSplashToHome() {
        if (_uiState.value.screen == ScreenRoute.SPLASH) {
            audio.playButtonClick()
            syncHomeSavedRunFlags(ScreenRoute.HOME)
        }
    }

    fun navigateTo(route: ScreenRoute) {
        audio.playButtonClick()
        if (route == ScreenRoute.HOME) {
            audio.setGameplayPaused(false)
            syncHomeSavedRunFlags(ScreenRoute.HOME)
        } else {
            _uiState.update {
                it.copy(
                    screen = route,
                    returnScreenAfterSettings = if (route == ScreenRoute.SETTINGS) ScreenRoute.HOME else it.returnScreenAfterSettings
                )
            }
        }
    }

    fun openSettingsFromPauseMenu() {
        audio.playButtonClick()
        GameSessionManager.saveCurrentSnapshot(storage)
        _uiState.update {
            it.copy(
                screen = ScreenRoute.SETTINGS,
                returnScreenAfterSettings = ScreenRoute.GAME
            )
        }
    }

    fun navigateBackFromSettings() {
        audio.playButtonClick()
        val target = _uiState.value.returnScreenAfterSettings
        if (target == ScreenRoute.GAME && offlineEngine.hasActiveRun) {
            // Return directly to the paused offline session without resetting anything!
            offlineEngine.isPaused = true
            audio.setGameplayPaused(true)
            _uiState.update {
                it.copy(
                    screen = ScreenRoute.GAME,
                    isPausedModalOpen = true,
                    resumeCountdownNumber = null
                )
            }
        } else {
            syncHomeSavedRunFlags(ScreenRoute.HOME)
        }
    }

    /**
     * Starts a brand-new Infinite offline run (called ONLY on START / NEW GAME / RESTART / PLAY AGAIN).
     */
    fun startFreshOfflineRun() {
        try {
            audio.playButtonClick()
            countdownJob?.cancel()
            onlineSession.disconnect(sendLeave = false)

            val p = _uiState.value.profile
            val skin = SkinsConfig.getById(p.selectedSkinId)

            GameSessionManager.createNewOfflineWorld(
                playerSkin = skin,
                quality = p.settings.graphicsQuality,
                nickname = p.nickname.ifBlank { "YOU" },
                storage = storage
            )
            offlineSnapshotTimerSec = 0f
            audio.setGameplayPaused(false)

            val initialHud = offlineEngine.buildHudState().copy(
                hudCoinProgress = p.hudCoinProgress,
                hudCoinTarget = p.hudCoinTarget
            )

            _uiState.update {
                it.copy(
                    screen = ScreenRoute.GAME,
                    isOnlineMatch = false,
                    isPausedModalOpen = false,
                    resumeCountdownNumber = null,
                    hasSavedOfflineRun = true,
                    savedOfflineScore = 0,
                    serverUnreachableMessage = null,
                    isConnectingOnline = false,
                    matchSummary = null,
                    hud = initialHud
                )
            }
        } catch (t: Throwable) {
            _uiState.update { it.copy(fatalError = t.message ?: "Failed to start match") }
        }
    }

    /**
     * Continues the exact paused offline session from Home Menu with a smooth 3 - 2 - 1 countdown!
     */
    fun continueSavedOfflineRun() {
        audio.playButtonClick()
        val p = _uiState.value.profile
        if (!offlineEngine.hasActiveRun) {
            val ok = GameSessionManager.restoreSavedSessionIfNeeded(storage, p.settings.graphicsQuality)
            if (!ok) {
                startFreshOfflineRun()
                return
            }
        }
        offlineEngine.isPaused = true
        val restoredHud = offlineEngine.buildHudState().copy(
            hudCoinProgress = p.hudCoinProgress,
            hudCoinTarget = p.hudCoinTarget
        )
        _uiState.update {
            it.copy(
                screen = ScreenRoute.GAME,
                isOnlineMatch = false,
                isPausedModalOpen = false,
                hud = restoredHud
            )
        }
        startThreeTwoOneResumeCountdown()
    }

    fun pauseOfflineImmediately() {
        countdownJob?.cancel()
        offlineEngine.isPaused = true
        audio.setGameplayPaused(true)
        GameSessionManager.saveCurrentSnapshot(storage)
        _uiState.update {
            it.copy(
                isPausedModalOpen = true,
                resumeCountdownNumber = null,
                hasSavedOfflineRun = offlineEngine.hasActiveRun,
                savedOfflineScore = offlineEngine.currentPlayerScore
            )
        }
    }

    fun resumeOfflineWithCountdown() {
        audio.playButtonClick()
        startThreeTwoOneResumeCountdown()
    }

    private fun startThreeTwoOneResumeCountdown() {
        countdownJob?.cancel()
        offlineEngine.isPaused = true
        audio.setGameplayPaused(true)
        _uiState.update { it.copy(isPausedModalOpen = false, resumeCountdownNumber = 3) }

        countdownJob = viewModelScope.launch {
            for (n in 3 downTo 1) {
                _uiState.update { it.copy(resumeCountdownNumber = n) }
                audio.playButtonClick()
                delay(620L)
            }
            offlineEngine.isPaused = false
            audio.setGameplayPaused(false)
            _uiState.update { it.copy(resumeCountdownNumber = null) }
        }
    }

    fun exitPausedGameToHome() {
        audio.playButtonClick()
        countdownJob?.cancel()
        offlineEngine.isPaused = true
        audio.setGameplayPaused(false) // Allow menu music on Home
        GameSessionManager.saveCurrentSnapshot(storage)
        syncHomeSavedRunFlags(ScreenRoute.HOME)
    }

    private fun syncHomeSavedRunFlags(route: ScreenRoute) {
        val active = offlineEngine.hasActiveRun
        _uiState.update {
            it.copy(
                screen = route,
                isPausedModalOpen = false,
                resumeCountdownNumber = null,
                hasSavedOfflineRun = active,
                savedOfflineScore = if (active) offlineEngine.currentPlayerScore else 0
            )
        }
    }

    // --- ONLINE MULTIPLAYER ACTIONS ---

    fun saveNickname(cleanNickname: String) {
        val valid = ServerConfig.validateNickname(cleanNickname) ?: return
        val updated = _uiState.value.profile.copy(nickname = valid)
        storage.saveProfile(updated)
        _uiState.update { it.copy(profile = updated) }
    }

    fun startOnlinePublic() {
        val p = _uiState.value.profile
        val nick = ServerConfig.validateNickname(p.nickname) ?: "Player"
        connectWithParams(
            OnlineConnectParams(
                joinMode = OnlineJoinMode.PUBLIC,
                nickname = nick,
                skinId = p.selectedSkinId
            )
        )
    }

    fun createPrivateOnlineRoom(botsEnabled: Boolean) {
        val p = _uiState.value.profile
        val nick = ServerConfig.validateNickname(p.nickname) ?: "Player"
        connectWithParams(
            OnlineConnectParams(
                joinMode = OnlineJoinMode.CREATE_PRIVATE,
                nickname = nick,
                skinId = p.selectedSkinId,
                privateBotsEnabled = botsEnabled
            )
        )
    }

    fun joinPrivateOnlineRoom(code: String) {
        val p = _uiState.value.profile
        val nick = ServerConfig.validateNickname(p.nickname) ?: "Player"
        connectWithParams(
            OnlineConnectParams(
                joinMode = OnlineJoinMode.JOIN_PRIVATE,
                nickname = nick,
                skinId = p.selectedSkinId,
                roomCode = code.trim().uppercase()
            )
        )
    }

    private fun connectWithParams(params: OnlineConnectParams) {
        audio.playButtonClick()
        lastConnectParams = params
        _uiState.update {
            it.copy(
                isConnectingOnline = true,
                serverUnreachableMessage = null
            )
        }
        onlineSession.connect(params, _uiState.value.profile.settings.graphicsQuality)
    }

    fun retryOnlineConnection() {
        val prev = lastConnectParams
        if (prev != null) {
            connectWithParams(prev)
        } else {
            startOnlinePublic()
        }
    }

    fun dismissOnlinePopup() {
        audio.playButtonClick()
        onlineSession.disconnect(sendLeave = false)
        _uiState.update {
            it.copy(
                isConnectingOnline = false,
                serverUnreachableMessage = null
            )
        }
    }

    fun leaveOnlineMatchToHome() {
        audio.playButtonClick()
        onlineSession.disconnect(sendLeave = true)
        _uiState.update {
            it.copy(
                isOnlineMatch = false,
                isConnectingOnline = false,
                serverUnreachableMessage = null
            )
        }
        syncHomeSavedRunFlags(ScreenRoute.HOME)
    }

    fun playAgainAfterGameOver() {
        val summary = _uiState.value.matchSummary
        if (summary?.wasOnline == true) {
            audio.playButtonClick()
            val skinId = _uiState.value.profile.selectedSkinId
            if (onlineSession.isConnectedAndJoined) {
                onlineSession.respawnInCurrentRoom(skinId)
                _uiState.update {
                    it.copy(
                        screen = ScreenRoute.GAME,
                        isOnlineMatch = true,
                        matchSummary = null
                    )
                }
            } else {
                retryOnlineConnection()
            }
        } else {
            startFreshOfflineRun()
        }
    }

    fun togglePrivateRoomBots(enabled: Boolean) {
        audio.playButtonClick()
        onlineSession.togglePrivateRoomBots(enabled)
        _uiState.update { it.copy(hud = it.hud.copy(privateBotsEnabled = enabled)) }
    }

    // --- 60 FPS INPUT & FRAME TICK ---

    fun onPlayerInput(steerAngle: Float?, boosting: Boolean) {
        if (_uiState.value.isOnlineMatch) {
            onlineSession.setPlayerInput(steerAngle, boosting)
        } else {
            offlineEngine.setPlayerInput(steerAngle, boosting)
        }
    }

    fun onGameFrame(dt: Float) {
        val state = _uiState.value
        val p = state.profile

        if (state.isOnlineMatch) {
            val snap = onlineSession.updateRenderFrame(dt) ?: return
            _uiState.update {
                it.copy(
                    hud = snap.copy(
                        hudCoinProgress = p.hudCoinProgress,
                        hudCoinTarget = p.hudCoinTarget
                    )
                )
            }
        } else {
            val snap = offlineEngine.updateFrame(dt)
            if (!offlineEngine.isPaused && offlineEngine.hasActiveRun) {
                offlineSnapshotTimerSec += dt
                if (offlineSnapshotTimerSec >= 3.0f) {
                    offlineSnapshotTimerSec = 0f
                    GameSessionManager.saveCurrentSnapshot(storage)
                }
            }
            if (snap != null) {
                _uiState.update {
                    it.copy(
                        savedOfflineScore = snap.score,
                        hud = snap.copy(
                            hudCoinProgress = p.hudCoinProgress,
                            hudCoinTarget = p.hudCoinTarget
                        )
                    )
                }
            }
        }
    }

    private fun onCoinsPickedUp(amount: Int) {
        val p = _uiState.value.profile
        var prog = p.hudCoinProgress + amount
        var target = p.hudCoinTarget
        var bonus = 0
        var msg: String? = null
        if (prog >= target) {
            prog -= target
            target = (target + 5).coerceAtMost(25)
            bonus = 20
            msg = "+20 Bonus Coins!"
        }
        val updated = p.copy(
            coins = p.coins + amount + bonus,
            hudCoinProgress = prog,
            hudCoinTarget = target
        )
        storage.saveProfile(updated)
        _uiState.update {
            it.copy(
                profile = updated,
                hud = it.hud.copy(hudCoinProgress = prog, hudCoinTarget = target),
                toastMessage = msg ?: it.toastMessage
            )
        }
    }

    private fun onMatchEnded(summary: MatchEndSummary) {
        val state = _uiState.value
        if (!summary.wasOnline) {
            // Clear saved offline snapshot on death so CONTINUE disappears on Home!
            GameSessionManager.clearSessionOnGameOver(storage)
        }
        val p = state.profile
        val updated = p.copy(
            coins = p.coins + summary.coinsEarned,
            bestScore = maxOf(p.bestScore, summary.score)
        )
        storage.saveProfile(updated)
        _uiState.update {
            it.copy(
                screen = ScreenRoute.GAME_OVER,
                profile = updated,
                hasSavedOfflineRun = if (summary.wasOnline) it.hasSavedOfflineRun else false,
                savedOfflineScore = if (summary.wasOnline) it.savedOfflineScore else 0,
                matchSummary = summary
            )
        }
    }

    fun selectOrUnlockSkin(skin: SnakeSkin) {
        val p = _uiState.value.profile
        if (p.unlockedSkins.contains(skin.id)) {
            audio.playButtonClick()
            val updated = p.copy(selectedSkinId = skin.id)
            storage.saveProfile(updated)
            _uiState.update { it.copy(profile = updated) }
        } else if (p.coins >= skin.priceCoins) {
            audio.playCoin()
            GameSessionManager.triggerMediumImpact()
            val updated = p.copy(
                coins = p.coins - skin.priceCoins,
                unlockedSkins = p.unlockedSkins + skin.id,
                selectedSkinId = skin.id
            )
            storage.saveProfile(updated)
            _uiState.update {
                it.copy(
                    profile = updated,
                    toastMessage = "Unlocked ${skin.name}!"
                )
            }
        } else {
            audio.playButtonClick()
            _uiState.update {
                it.copy(toastMessage = "Need ${skin.priceCoins - p.coins} more coins!")
            }
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val p = _uiState.value.profile
        val next = transform(p.settings)
        audio.applySettings(next.soundEnabled, next.musicEnabled)
        GameSessionManager.setVibrationEnabled(next.vibrationEnabled)
        offlineEngine.updateGraphicsQuality(next.graphicsQuality)
        onlineSession.effectiveQuality = next.graphicsQuality

        val updated = p.copy(settings = next)
        storage.saveProfile(updated)
        _uiState.update { it.copy(profile = updated) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        GameSessionManager.saveCurrentSnapshot(storage)
        onlineSession.disconnect(sendLeave = true)
    }
}
