package com.example.config

/**
 * Simplified to ONE endless mode: START = Infinite endless arena vs AI bots (or Online Multiplayer).
 */
enum class GameModeId(
    val arenaRadius: Float,
    val botCount: Int,
    val speedMultiplier: Float,
    val foodCount: Int
) {
    START(
        arenaRadius = 2800f,
        botCount = 20,
        speedMultiplier = 1.0f,
        foodCount = 360
    )
}
