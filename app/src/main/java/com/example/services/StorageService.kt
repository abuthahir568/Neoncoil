package com.example.services

import android.content.Context
import com.example.config.AppLanguage
import com.example.config.GameModeId
import org.json.JSONArray
import org.json.JSONObject

enum class ControlScheme { JOYSTICK, TOUCH_DRAG }
enum class GraphicsQuality { LOW, MEDIUM, HIGH }

data class UserSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val controlScheme: ControlScheme = ControlScheme.JOYSTICK,
    val controlsSizePercent: Int = DEFAULT_CONTROLS_SIZE, // 50..150, default 85
    val hudSizePercent: Int = DEFAULT_HUD_SIZE,           // 60..140, default 80
    val graphicsQuality: GraphicsQuality = GraphicsQuality.HIGH,
    val language: AppLanguage = AppLanguage.EN
) {
    val controlsScale: Float get() = (controlsSizePercent.coerceIn(50, 150)) / 100f
    val hudScale: Float get() = (hudSizePercent.coerceIn(60, 140)) / 100f

    companion object {
        const val DEFAULT_CONTROLS_SIZE = 85
        const val DEFAULT_HUD_SIZE = 80
    }
}

data class PlayerProfile(
    val coins: Int = 120,
    val unlockedSkins: Set<String> = setOf("rainbow_coil"),
    val selectedSkinId: String = "rainbow_coil",
    val nickname: String = "",
    val bestScore: Int = 0,
    val hudCoinProgress: Int = 0,
    val hudCoinTarget: Int = 5,
    val settings: UserSettings = UserSettings()
)

class StorageService(context: Context?) {
    private val prefs = try {
        context?.getSharedPreferences("neoncoil_cartoon_save_v2", Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private var memoryProfileFallback: String? = null
    private var memorySnapshotFallback: String? = null

    fun loadProfile(): PlayerProfile {
        return try {
            val raw = prefs?.getString(KEY_SAVE, null) ?: memoryProfileFallback
            if (raw.isNullOrBlank()) {
                val initial = PlayerProfile()
                saveProfile(initial)
                initial
            } else {
                deserialize(JSONObject(raw))
            }
        } catch (_: Throwable) {
            PlayerProfile()
        }
    }

    fun saveProfile(profile: PlayerProfile) {
        try {
            val str = serialize(profile).toString()
            memoryProfileFallback = str
            prefs?.edit()?.putString(KEY_SAVE, str)?.apply()
        } catch (_: Throwable) {
            // Safe fallback
        }
    }

    fun saveOfflineSnapshot(snapshotJson: String) {
        try {
            memorySnapshotFallback = snapshotJson
            prefs?.edit()?.putString(KEY_OFFLINE_SNAPSHOT, snapshotJson)?.apply()
        } catch (_: Throwable) {
            // Safe fallback
        }
    }

    fun loadOfflineSnapshot(): String? {
        return try {
            val raw = prefs?.getString(KEY_OFFLINE_SNAPSHOT, null) ?: memorySnapshotFallback
            if (raw.isNullOrBlank()) null else raw
        } catch (_: Throwable) {
            null
        }
    }

    fun clearOfflineSnapshot() {
        try {
            memorySnapshotFallback = null
            prefs?.edit()?.remove(KEY_OFFLINE_SNAPSHOT)?.apply()
        } catch (_: Throwable) {
            // Safe fallback
        }
    }

    private fun serialize(p: PlayerProfile): JSONObject {
        val obj = JSONObject()
        obj.put("coins", p.coins)
        obj.put("unlockedSkins", JSONArray(p.unlockedSkins.map { it.trim() }))
        obj.put("selectedSkinId", p.selectedSkinId.trim())
        obj.put("nickname", p.nickname)
        obj.put("bestScore", p.bestScore)
        obj.put("hudCoinProgress", p.hudCoinProgress)
        obj.put("hudCoinTarget", p.hudCoinTarget)

        val s = p.settings
        val sObj = JSONObject()
        sObj.put("soundEnabled", s.soundEnabled)
        sObj.put("musicEnabled", s.musicEnabled)
        sObj.put("vibrationEnabled", s.vibrationEnabled)
        sObj.put("controlScheme", s.controlScheme.name)
        sObj.put("controlsSizePercent", s.controlsSizePercent.coerceIn(50, 150))
        sObj.put("hudSizePercent", s.hudSizePercent.coerceIn(60, 140))
        sObj.put("graphicsQuality", s.graphicsQuality.name)
        sObj.put("language", s.language.name)
        obj.put("settings", sObj)
        return obj
    }

    private fun deserialize(obj: JSONObject): PlayerProfile {
        val unlocked = mutableSetOf("rainbow_coil")
        obj.optJSONArray("unlockedSkins")?.let { arr ->
            for (i in 0 until arr.length()) {
                val id = arr.optString(i).trim()
                if (id.isNotEmpty()) unlocked.add(id)
            }
        }

        // Backward compatible with legacy bestScores map if present
        var best = obj.optInt("bestScore", 0)
        if (best == 0) {
            best = obj.optJSONObject("bestScores")?.optInt(GameModeId.START.name, 0) ?: 0
        }

        val sObj = obj.optJSONObject("settings")
        val settings = UserSettings(
            soundEnabled = sObj?.optBoolean("soundEnabled", true) ?: true,
            musicEnabled = sObj?.optBoolean("musicEnabled", true) ?: true,
            vibrationEnabled = sObj?.optBoolean("vibrationEnabled", true) ?: true,
            controlScheme = runCatching {
                ControlScheme.valueOf(sObj?.optString("controlScheme", "JOYSTICK") ?: "JOYSTICK")
            }.getOrDefault(ControlScheme.JOYSTICK),
            controlsSizePercent = (sObj?.optInt("controlsSizePercent", UserSettings.DEFAULT_CONTROLS_SIZE)
                ?: UserSettings.DEFAULT_CONTROLS_SIZE).coerceIn(50, 150),
            hudSizePercent = (sObj?.optInt("hudSizePercent", UserSettings.DEFAULT_HUD_SIZE)
                ?: UserSettings.DEFAULT_HUD_SIZE).coerceIn(60, 140),
            graphicsQuality = runCatching {
                GraphicsQuality.valueOf(sObj?.optString("graphicsQuality", "HIGH") ?: "HIGH")
            }.getOrDefault(GraphicsQuality.HIGH),
            language = runCatching {
                AppLanguage.valueOf(sObj?.optString("language", "EN") ?: "EN")
            }.getOrDefault(AppLanguage.EN)
        )

        return PlayerProfile(
            coins = obj.optInt("coins", 120),
            unlockedSkins = unlocked,
            selectedSkinId = obj.optString("selectedSkinId", "rainbow_coil").trim(),
            nickname = obj.optString("nickname", ""),
            bestScore = best,
            hudCoinProgress = obj.optInt("hudCoinProgress", 0),
            hudCoinTarget = obj.optInt("hudCoinTarget", 5).coerceAtLeast(5),
            settings = settings
        )
    }

    companion object {
        private const val KEY_SAVE = "neoncoil_save_json_v2"
        private const val KEY_OFFLINE_SNAPSHOT = "neoncoil_offline_session_v1"
    }
}
