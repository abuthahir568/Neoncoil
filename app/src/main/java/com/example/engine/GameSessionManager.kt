package com.example.engine

import android.content.Context
import com.example.config.SnakeSkin
import com.example.services.AudioService
import com.example.services.GraphicsQuality
import com.example.services.HapticService
import com.example.services.StorageService

/**
 * True process-wide singleton managing the single offline ArenaEngine instance.
 *
 * Lives completely outside Jetpack Compose screens and ViewModels so navigating
 * between GAME, SETTINGS, SKINS, and HOME never recreates or resets the world!
 */
object GameSessionManager {
    val audio = AudioService()
    private var haptics = HapticService(null)

    val offlineEngine = ArenaEngine(audio, haptics)
    private var initializedFromDisk = false

    fun attachContext(context: Context) {
        haptics = HapticService(context.applicationContext)
        offlineEngine.haptics = haptics
    }

    fun setVibrationEnabled(enabled: Boolean) {
        haptics.enabled = enabled
    }

    fun triggerMediumImpact() {
        haptics.mediumImpact()
    }

    /**
     * Restores a saved offline snapshot once on app launch if no session is currently in memory.
     */
    fun restoreSavedSessionIfNeeded(storage: StorageService, quality: GraphicsQuality): Boolean {
        if (offlineEngine.hasActiveRun) {
            initializedFromDisk = true
            return true
        }
        if (initializedFromDisk) return false
        initializedFromDisk = true

        val raw = storage.loadOfflineSnapshot() ?: return false
        val ok = offlineEngine.restoreFromSnapshotJson(raw, quality)
        if (!ok) {
            storage.clearOfflineSnapshot()
        }
        return ok
    }

    /**
     * Creates a brand-new offline world ONLY when explicitly requested (START / NEW GAME / RESTART / PLAY AGAIN).
     */
    fun createNewOfflineWorld(
        playerSkin: SnakeSkin,
        quality: GraphicsQuality,
        nickname: String,
        storage: StorageService
    ) {
        storage.clearOfflineSnapshot()
        offlineEngine.startNewMatch(
            playerSkin = playerSkin,
            quality = quality,
            playerNickname = nickname
        )
        saveCurrentSnapshot(storage)
    }

    fun saveCurrentSnapshot(storage: StorageService) {
        if (!offlineEngine.hasActiveRun) return
        val json = offlineEngine.toSnapshotJson() ?: return
        storage.saveOfflineSnapshot(json)
    }

    fun clearSessionOnGameOver(storage: StorageService) {
        storage.clearOfflineSnapshot()
    }
}
