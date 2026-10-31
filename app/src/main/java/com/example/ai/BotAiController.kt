package com.example.ai

import com.example.engine.BotPersonality
import com.example.engine.FoodItem
import com.example.engine.FoodKind
import com.example.engine.SegmentRef
import com.example.engine.SnakeEntity
import com.example.engine.SpatialHash
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object BotAiController {
    val botNames = listOf(
        "Sunny", "Bubbles", "Ziggy", "Mochi", "Noodle",
        "Sprinkle", "Pebble", "Mango", "Waffles", "Pickles",
        "CupcakeJr", "Twisty", "Biscuit", "Skittles", "Jellybean",
        "Zippy", "Coco", "Taffy", "Poppy", "Clover",
        "orbit_kid", "Pippin", "Doodle", "Choco", "Starry"
    )

    fun updateBot(
        bot: SnakeEntity,
        dt: Float,
        arenaRadius: Float,
        playerScore: Int,
        allSnakes: List<SnakeEntity>,
        foodGrid: SpatialHash<FoodItem>,
        segmentGrid: SpatialHash<SegmentRef>
    ) {
        if (!bot.alive) return
        bot.aiDecisionTimer -= dt

        val skill = (0.40f + (playerScore / 2600f) * 0.60f).coerceIn(0.40f, 1.0f)

        val hx = bot.headX
        val hy = bot.headY
        val distFromCenter = hypot(hx, hy)

        // 1. Wall avoidance
        val wallMargin = 250f
        if (distFromCenter > arenaRadius - wallMargin) {
            bot.targetAngle = atan2(-hy, -hx)
            bot.wantsBoost = distFromCenter > arenaRadius - 125f && bot.boostEnergy > 0.25f
            return
        }

        // 2. Rival body segment collision avoidance
        var avoidX = 0f
        var avoidY = 0f
        var hazard = false
        val lookRadius = (115f + 120f * skill) + bot.bodyRadius

        segmentGrid.queryRadius(hx, hy, lookRadius) { segRef ->
            if (segRef.snake.id != bot.id && segRef.snake.alive) {
                val dx = hx - segRef.x
                val dy = hy - segRef.y
                val dSq = dx * dx + dy * dy
                if (dSq > 1f && dSq < lookRadius * lookRadius) {
                    val d = kotlin.math.sqrt(dSq)
                    val w = (lookRadius - d) / lookRadius
                    avoidX += (dx / d) * w * 2.4f
                    avoidY += (dy / d) * w * 2.4f
                    hazard = true
                }
            }
        }

        if (hazard) {
            bot.targetAngle = atan2(avoidY, avoidX)
            bot.wantsBoost = bot.personality == BotPersonality.CAUTIOUS && bot.boostEnergy > 0.35f
            return
        }

        if (bot.aiDecisionTimer > 0f) return
        bot.aiDecisionTimer = Random.nextFloat() * 0.09f + 0.08f

        // 3. Personality logic (flee bigger snakes / intercept smaller ones)
        var predator: SnakeEntity? = null
        var predDist = 400f
        var prey: SnakeEntity? = null
        var preyDist = 440f

        for (other in allSnakes) {
            if (other.id == bot.id || !other.alive) continue
            val d = hypot(other.headX - hx, other.headY - hy)
            if (other.desiredLength > bot.desiredLength * 1.25f && d < predDist) {
                predDist = d
                predator = other
            } else if (bot.desiredLength > other.desiredLength * 1.2f && d < preyDist) {
                preyDist = d
                prey = other
            }
        }

        if (bot.personality == BotPersonality.CAUTIOUS && predator != null) {
            bot.targetAngle = atan2(hy - predator.headY, hx - predator.headX)
            bot.wantsBoost = predDist < 230f && bot.boostEnergy > 0.3f
            return
        }

        if (bot.personality == BotPersonality.AGGRESSIVE && prey != null && skill > 0.45f) {
            val lead = 130f * skill
            val ix = prey.headX + cos(prey.angle) * lead
            val iy = prey.headY + sin(prey.angle) * lead
            bot.targetAngle = atan2(iy - hy, ix - hx)
            bot.wantsBoost = preyDist < 290f && bot.boostEnergy > 0.4f
            return
        }

        // 4. Chase nearby orbs & fruit/desserts
        var bestFood: FoodItem? = null
        var bestScore = -1f
        val searchR = if (bot.personality == BotPersonality.FOOD_HUNTER) 440f else 320f

        foodGrid.queryRadius(hx, hy, searchR) { food ->
            if (food.active) {
                val d = hypot(food.x - hx, food.y - hy).coerceAtLeast(12f)
                val valScore = (food.kind.scoreValue * 10f) / d
                if (valScore > bestScore) {
                    bestScore = valScore
                    bestFood = food
                }
            }
        }

        val target = bestFood
        if (target != null) {
            bot.targetAngle = atan2(target.y - hy, target.x - hx)
            bot.wantsBoost = bot.personality == BotPersonality.FOOD_HUNTER &&
                target.kind == FoodKind.DEATH_DROP &&
                bot.boostEnergy > 0.45f
        } else {
            bot.aiWanderOffset += (Random.nextFloat() - 0.5f) * 0.65f
            bot.targetAngle = wrapAngle(bot.angle + sin(bot.aiWanderOffset) * 0.4f)
            bot.wantsBoost = false
        }
    }

    private fun wrapAngle(a: Float): Float {
        var res = a
        val twoPi = (2.0 * PI).toFloat()
        while (res > PI) res -= twoPi
        while (res < -PI) res += twoPi
        return res
    }
}
