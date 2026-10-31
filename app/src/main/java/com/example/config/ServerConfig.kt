package com.example.config

import com.example.BuildConfig

/**
 * Central configuration for the NeonCoil Authoritative Multiplayer Server.
 *
 * - In local development on Android Emulator, `ws://10.0.2.2:8080/ws` reaches `localhost:8080` on the host machine.
 * - For production (e.g. Render), update `NEONCOIL_SERVER_URL` in `.env` / Secrets panel (or `DEFAULT_WS_URL` below)
 *   to `wss://your-render-service.onrender.com/ws`.
 */
object ServerConfig {
    private const val DEFAULT_WS_URL = "ws://10.0.2.2:8080/ws"

    val wsUrl: String
        get() {
            val fromBuild = runCatching { BuildConfig.NEONCOIL_SERVER_URL.trim() }.getOrDefault("")
            return if (fromBuild.startsWith("ws://") || fromBuild.startsWith("wss://")) {
                fromBuild
            } else {
                DEFAULT_WS_URL
            }
        }

    val healthHttpUrl: String
        get() {
            val base = wsUrl
                .replaceFirst("wss://", "https://")
                .replaceFirst("ws://", "http://")
                .removeSuffix("/")
                .removeSuffix("/ws")
            return "$base/health"
        }

    const val CONNECT_TIMEOUT_MS = 5_000L
    const val RECONNECT_GRACE_MS = 10_000L
    const val INPUT_SEND_INTERVAL_MS = 40L // 25 Hz
    const val INTERPOLATION_DELAY_MS = 100L // ~100ms smooth remote snake interpolation

    private val blockedWords = listOf(
        "fuck", "shit", "bitch", "asshole", "dick", "pussy",
        "cunt", "nigger", "faggot", "slut", "whore", "bastard"
    )

    fun validateNickname(raw: String): String? {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
        if (cleaned.length !in 3..12) return null
        val compact = cleaned.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (blockedWords.any { compact.contains(it) }) return null
        return cleaned
    }
}
