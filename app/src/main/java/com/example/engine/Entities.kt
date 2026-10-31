package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.config.SnakeSkin
import com.example.services.GraphicsQuality

enum class BotPersonality {
    AGGRESSIVE,
    CAUTIOUS,
    FOOD_HUNTER
}

enum class FoodKind(val growthValue: Float, val scoreValue: Int, val baseRadius: Float, val rarityLevel: Int) {
    BOOST_PELLET(0.32f, 3, 6.5f, 1),
    GLOW_ORB(0.55f, 8, 9.0f, 1),
    APPLE(1.30f, 22, 14.5f, 2),
    CUPCAKE(1.90f, 34, 16.0f, 3),
    CROISSANT(2.35f, 45, 17.0f, 3),
    DEATH_DROP(1.45f, 25, 13.0f, 2),
    COIN(0.75f, 15, 13.5f, 3)
}

class SegmentPoint(var x: Float = 0f, var y: Float = 0f)

class SnakeEntity(
    val id: Int,
    var name: String,
    val isPlayer: Boolean,
    var personality: BotPersonality = BotPersonality.FOOD_HUNTER,
    var skin: SnakeSkin
) {
    val segments: ArrayList<SegmentPoint> = ArrayList(256)
    var angle: Float = 0f
    var targetAngle: Float = 0f
    var desiredLength: Float = 18f
    var score: Int = 0
    var kills: Int = 0
    var wantsBoost: Boolean = false
    var activelyBoosting: Boolean = false
    var boostEnergy: Float = 1.0f // 0f..1f
    var alive: Boolean = true
    var invulnerableTimer: Float = 0f
    var boostPelletAccum: Float = 0f
    var aiDecisionTimer: Float = 0f
    var aiWanderOffset: Float = 0f

    val headX: Float get() = if (segments.isNotEmpty()) segments[0].x else 0f
    val headY: Float get() = if (segments.isNotEmpty()) segments[0].y else 0f

    val bodyRadius: Float
        get() {
            val base = 14.5f
            val bonus = (kotlin.math.sqrt(desiredLength.coerceAtLeast(10f)) * 1.15f)
                .coerceAtMost(15f)
            return base + bonus
        }
}

class FoodItem {
    var active: Boolean = false
    var x: Float = 0f
    var y: Float = 0f
    var kind: FoodKind = FoodKind.GLOW_ORB
    var color: Color = Color(0xFF56E84D)
    var phase: Float = 0f
    var radius: Float = 9f
}

class Particle {
    var active: Boolean = false
    var x: Float = 0f
    var y: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var life: Float = 0f
    var maxLife: Float = 0.5f
    var radius: Float = 4.5f
    var color: Color = Color.Yellow
}

class FloatingPopup {
    var active: Boolean = false
    var x: Float = 0f
    var y: Float = 0f
    var text: String = ""
    var life: Float = 0f
    var maxLife: Float = 0.85f
    var color: Color = Color.Yellow
}

data class MinimapDot(
    val id: Int,
    val x: Float,
    val y: Float,
    val isPlayer: Boolean
)

/**
 * Unified read-only view consumed by ArenaRenderer so both Offline ArenaEngine
 * and OnlineArenaSession render with the exact same glossy cartoon snake art!
 */
interface ArenaRenderSource {
    val arenaRadius: Float
    val cameraX: Float
    val cameraY: Float
    val cameraZoom: Float
    val shakeIntensity: Float
    val elapsedSec: Float
    val effectiveQuality: GraphicsQuality
    val renderSnakes: List<SnakeEntity>
    val renderFoods: Iterable<FoodItem>
    val renderParticles: Array<Particle>
    val renderPopups: Array<FloatingPopup>
    val minimapDots: List<MinimapDot>
}

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val score: Int,
    val isPlayer: Boolean
)

data class MatchEndSummary(
    val score: Int,
    val length: Int,
    val kills: Int,
    val rank: Int,
    val coinsEarned: Int,
    val wasOnline: Boolean = false
)

data class KillFeedEntry(
    val id: Long,
    val text: String,
    val createdAtMs: Long
)

data class HudState(
    val score: Int = 0,
    val length: Int = 20,
    val kills: Int = 0,
    val playerRank: Int = 1,
    val aliveSnakesCount: Int = 21,
    val leaderboard: List<LeaderboardEntry> = emptyList(), // Top 5
    val boostEnergy: Float = 1.0f,
    val hudCoinProgress: Int = 0,
    val hudCoinTarget: Int = 5,
    val isOnline: Boolean = false,
    val pingMs: Int? = null,
    val killFeed: List<KillFeedEntry> = emptyList(),
    val roomCode: String? = null,
    val isPrivateHost: Boolean = false,
    val privateBotsEnabled: Boolean = true
)
