package com.example.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.ai.BotAiController
import com.example.config.GameModeId
import com.example.config.SnakeSkin
import com.example.config.SkinsConfig
import com.example.services.AudioService
import com.example.services.GraphicsQuality
import com.example.services.HapticService
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 60 FPS Fixed-Timestep Infinite Arena Engine for NeonCoil: Snake Arena.
 * Supports exact pause/resume and full versioned JSON snapshot save/restore.
 */
class ArenaEngine(
    @Volatile var audio: AudioService,
    @Volatile var haptics: HapticService
) : ArenaRenderSource {

    val mode: GameModeId = GameModeId.START

    val snakes = ArrayList<SnakeEntity>(28)
    lateinit var player: SnakeEntity
        private set

    val foodPool = Array(650) { FoodItem() }
    val particlePool = Array(240) { Particle() }
    val popupPool = Array(48) { FloatingPopup() }
    private val segmentRefPool = Array(3200) {
        SegmentRef(SnakeEntity(0, "", false, skin = SkinsConfig.allSkins.first()), 0, 0f, 0f, 12f)
    }
    private var segRefUsed = 0

    private val foodGrid = SpatialHash<FoodItem>(180f)
    private val segmentGrid = SpatialHash<SegmentRef>(140f)

    override var arenaRadius: Float = GameModeId.START.arenaRadius
        private set
    override var cameraX: Float = 0f
        private set
    override var cameraY: Float = 0f
        private set
    override var cameraZoom: Float = 1f
        private set
    override var shakeIntensity: Float = 0f
        private set
    override var elapsedSec: Float = 0f
        private set

    var isPaused: Boolean = false
    var matchEnded: Boolean = false
        private set
    var matchSummary: MatchEndSummary? = null
        private set

    override var effectiveQuality: GraphicsQuality = GraphicsQuality.HIGH
        private set
    private var smoothedFps: Float = 60f
    private var lowFpsSec: Float = 0f

    private var timeAccumulator: Float = 0f
    private val fixedStep: Float = 1f / 60f
    private var hudTickTimer: Float = 0f
    private var nextBotId: Int = 100
    private var coinsCollectedThisRun: Int = 0

    var onCoinPickedUp: ((Int) -> Unit)? = null
    var onMatchFinished: ((MatchEndSummary) -> Unit)? = null

    val hasActiveRun: Boolean
        get() = ::player.isInitialized && player.alive && !matchEnded

    val currentPlayerScore: Int
        get() = if (::player.isInitialized) player.score else 0

    override val renderSnakes: List<SnakeEntity>
        get() = snakes

    override val renderFoods: Iterable<FoodItem>
        get() = foodPool.asIterable()

    override val renderParticles: Array<Particle>
        get() = particlePool

    override val renderPopups: Array<FloatingPopup>
        get() = popupPool

    override val minimapDots: List<MinimapDot>
        get() = snakes.filter { it.alive }.map {
            MinimapDot(id = it.id, x = it.headX, y = it.headY, isPlayer = it.isPlayer)
        }

    private val candyOrbColors = listOf(
        Color(0xFFFFDE38),
        Color(0xFF56E84D),
        Color(0xFF2FE6DE),
        Color(0xFFFF7AD9),
        Color(0xFFA87BFF),
        Color(0xFFFF8A3D)
    )

    fun startNewMatch(
        playerSkin: SnakeSkin,
        quality: GraphicsQuality,
        playerNickname: String = "YOU"
    ) {
        effectiveQuality = quality
        arenaRadius = mode.arenaRadius
        elapsedSec = 0f
        timeAccumulator = 0f
        isPaused = false
        matchEnded = false
        matchSummary = null
        shakeIntensity = 0f
        smoothedFps = 60f
        lowFpsSec = 0f
        coinsCollectedThisRun = 0
        nextBotId = 100

        snakes.clear()
        foodPool.forEach { it.active = false }
        particlePool.forEach { it.active = false }
        popupPool.forEach { it.active = false }

        val displayName = playerNickname.ifBlank { "YOU" }
        player = createSnake(
            id = 1,
            name = displayName,
            isPlayer = true,
            personality = BotPersonality.AGGRESSIVE,
            skin = playerSkin,
            startX = 0f,
            startY = 0f,
            initialLength = 20f
        )
        player.boostEnergy = 1.0f
        player.invulnerableTimer = 2.0f
        snakes.add(player)

        for (i in 0 until mode.botCount) {
            spawnBot(initialSpawn = true)
        }
        for (i in 0 until mode.foodCount) {
            spawnRandomArenaFood()
        }

        cameraX = player.headX
        cameraY = player.headY
        cameraZoom = 1f
    }

    fun updateGraphicsQuality(quality: GraphicsQuality) {
        effectiveQuality = quality
    }

    fun clearActiveRun() {
        matchEnded = true
        isPaused = false
        snakes.clear()
        foodPool.forEach { it.active = false }
        particlePool.forEach { it.active = false }
        popupPool.forEach { it.active = false }
    }

    fun setPlayerInput(angleRadians: Float?, boosting: Boolean) {
        if (!::player.isInitialized || !player.alive || isPaused || matchEnded) return
        if (angleRadians != null) {
            player.targetAngle = angleRadians
        }
        player.wantsBoost = boosting
    }

    fun updateFrame(rawDeltaSeconds: Float): HudState? {
        if (isPaused || matchEnded || !::player.isInitialized) return null

        val clampedDt = rawDeltaSeconds.coerceIn(0.001f, 0.08f)
        val instantFps = 1f / clampedDt
        smoothedFps = smoothedFps * 0.92f + instantFps * 0.08f
        if (smoothedFps < 45f) {
            lowFpsSec += clampedDt
            if (lowFpsSec > 1.8f) {
                effectiveQuality = when (effectiveQuality) {
                    GraphicsQuality.HIGH -> GraphicsQuality.MEDIUM
                    GraphicsQuality.MEDIUM -> GraphicsQuality.LOW
                    GraphicsQuality.LOW -> GraphicsQuality.LOW
                }
                lowFpsSec = 0f
            }
        }

        timeAccumulator += clampedDt
        var steps = 0
        while (timeAccumulator >= fixedStep && steps < 4) {
            stepSimulation(fixedStep)
            timeAccumulator -= fixedStep
            steps++
        }

        hudTickTimer += clampedDt
        return if (hudTickTimer >= 0.10f) {
            hudTickTimer = 0f
            buildHudState()
        } else {
            null
        }
    }

    fun buildHudState(): HudState {
        if (!::player.isInitialized) return HudState()
        val sorted = snakes.filter { it.alive || it.isPlayer }.sortedByDescending { it.score }
        val top5 = sorted.take(5).mapIndexed { index, s ->
            LeaderboardEntry(
                rank = index + 1,
                name = s.name,
                score = s.score,
                isPlayer = s.isPlayer
            )
        }
        val playerRank = (sorted.indexOfFirst { it.isPlayer } + 1).coerceAtLeast(1)

        return HudState(
            score = player.score,
            length = player.desiredLength.toInt(),
            kills = player.kills,
            playerRank = playerRank,
            aliveSnakesCount = snakes.count { it.alive },
            leaderboard = top5,
            boostEnergy = player.boostEnergy,
            isOnline = false
        )
    }

    /**
     * Serializes the exact in-progress offline session into a compact versioned JSON string.
     */
    fun toSnapshotJson(): String? {
        if (!hasActiveRun) return null
        return try {
            val root = JSONObject()
            root.put("v", SNAPSHOT_VERSION)
            root.put("elapsed", elapsedSec.toDouble())
            root.put("camX", cameraX.toDouble())
            root.put("camY", cameraY.toDouble())
            root.put("camZoom", cameraZoom.toDouble())
            root.put("nextBotId", nextBotId)
            root.put("coinsRun", coinsCollectedThisRun)

            val snakesArr = JSONArray()
            for (s in snakes) {
                if (!s.alive) continue
                val sObj = JSONObject()
                sObj.put("id", s.id)
                sObj.put("name", s.name)
                sObj.put("isP", s.isPlayer)
                sObj.put("pers", s.personality.name)
                sObj.put("skin", s.skin.id)
                sObj.put("ang", s.angle.toDouble())
                sObj.put("tAng", s.targetAngle.toDouble())
                sObj.put("len", s.desiredLength.toDouble())
                sObj.put("sc", s.score)
                sObj.put("kl", s.kills)
                sObj.put("en", s.boostEnergy.toDouble())
                sObj.put("inv", s.invulnerableTimer.toDouble())

                val pts = JSONArray()
                for (pt in s.segments) {
                    // Store tenths of a pixel for sub-pixel accuracy without float bloat
                    pts.put((pt.x * 10f).roundToInt())
                    pts.put((pt.y * 10f).roundToInt())
                }
                sObj.put("pts", pts)
                snakesArr.put(sObj)
            }
            root.put("snakes", snakesArr)

            val foodsArr = JSONArray()
            for (f in foodPool) {
                if (!f.active) continue
                val fArr = JSONArray()
                fArr.put((f.x * 10f).roundToInt())
                fArr.put((f.y * 10f).roundToInt())
                fArr.put(f.kind.ordinal)
                fArr.put(f.color.toArgb())
                foodsArr.put(fArr)
            }
            root.put("foods", foodsArr)
            root.toString()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Restores an exact paused offline session from a versioned JSON snapshot.
     */
    fun restoreFromSnapshotJson(rawJson: String, quality: GraphicsQuality): Boolean {
        return try {
            val root = JSONObject(rawJson)
            if (root.optInt("v", -1) != SNAPSHOT_VERSION) return false

            val snakesArr = root.optJSONArray("snakes") ?: return false
            if (snakesArr.length() == 0) return false

            val restoredSnakes = ArrayList<SnakeEntity>(snakesArr.length())
            var restoredPlayer: SnakeEntity? = null

            for (i in 0 until snakesArr.length()) {
                val sObj = snakesArr.getJSONObject(i)
                val isP = sObj.optBoolean("isP", false)
                val skin = SkinsConfig.getById(sObj.optString("skin", "rainbow_coil"))
                val pers = runCatching {
                    BotPersonality.valueOf(sObj.optString("pers", "FOOD_HUNTER"))
                }.getOrDefault(BotPersonality.FOOD_HUNTER)

                val entity = SnakeEntity(
                    id = sObj.getInt("id"),
                    name = sObj.optString("name", if (isP) "YOU" else "Bot"),
                    isPlayer = isP,
                    personality = pers,
                    skin = skin
                )
                entity.angle = sObj.optDouble("ang", 0.0).toFloat()
                entity.targetAngle = sObj.optDouble("tAng", entity.angle.toDouble()).toFloat()
                entity.desiredLength = sObj.optDouble("len", 20.0).toFloat().coerceAtLeast(10f)
                entity.score = sObj.optInt("sc", 0)
                entity.kills = sObj.optInt("kl", 0)
                entity.boostEnergy = sObj.optDouble("en", 1.0).toFloat().coerceIn(0f, 1f)
                entity.invulnerableTimer = sObj.optDouble("inv", 0.0).toFloat().coerceAtLeast(0f)
                entity.alive = true

                val pts = sObj.optJSONArray("pts") ?: continue
                var pIdx = 0
                while (pIdx + 1 < pts.length()) {
                    val px = pts.getInt(pIdx) / 10f
                    val py = pts.getInt(pIdx + 1) / 10f
                    entity.segments.add(SegmentPoint(px, py))
                    pIdx += 2
                }
                if (entity.segments.isEmpty()) continue

                restoredSnakes.add(entity)
                if (isP) restoredPlayer = entity
            }

            val validPlayer = restoredPlayer ?: return false

            snakes.clear()
            snakes.addAll(restoredSnakes)
            player = validPlayer

            foodPool.forEach { it.active = false }
            particlePool.forEach { it.active = false }
            popupPool.forEach { it.active = false }

            val foodsArr = root.optJSONArray("foods")
            if (foodsArr != null) {
                val maxFoods = minOf(foodsArr.length(), foodPool.size)
                val kinds = FoodKind.entries
                for (i in 0 until maxFoods) {
                    val fArr = foodsArr.optJSONArray(i) ?: continue
                    if (fArr.length() < 4) continue
                    val slot = foodPool[i]
                    val kindOrd = fArr.optInt(2, 1).coerceIn(0, kinds.lastIndex)
                    val kind = kinds[kindOrd]
                    slot.active = true
                    slot.x = fArr.optInt(0, 0) / 10f
                    slot.y = fArr.optInt(1, 0) / 10f
                    slot.kind = kind
                    slot.radius = kind.baseRadius
                    slot.color = Color(fArr.optInt(3, 0xFF56E84D.toInt()))
                    slot.phase = (i % 6) * 1.04f
                }
            }

            arenaRadius = mode.arenaRadius
            effectiveQuality = quality
            elapsedSec = root.optDouble("elapsed", 0.0).toFloat()
            cameraX = root.optDouble("camX", validPlayer.headX.toDouble()).toFloat()
            cameraY = root.optDouble("camY", validPlayer.headY.toDouble()).toFloat()
            cameraZoom = root.optDouble("camZoom", 1.0).toFloat().coerceIn(0.68f, 1.05f)
            nextBotId = root.optInt("nextBotId", 200)
            coinsCollectedThisRun = root.optInt("coinsRun", 0)
            timeAccumulator = 0f
            shakeIntensity = 0f
            matchEnded = false
            matchSummary = null
            isPaused = true
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun stepSimulation(dt: Float) {
        elapsedSec += dt
        if (shakeIntensity > 0f) {
            shakeIntensity = (shakeIntensity - dt * 28f).coerceAtLeast(0f)
        }

        // Rebuild Spatial Hashes
        foodGrid.clear()
        for (f in foodPool) {
            if (f.active) {
                f.phase += dt * 3.2f
                foodGrid.insert(f.x, f.y, f)
            }
        }

        segmentGrid.clear()
        segRefUsed = 0
        for (s in snakes) {
            if (!s.alive) continue
            val rad = s.bodyRadius
            var idx = 0
            while (idx < s.segments.size && segRefUsed < segmentRefPool.size) {
                val pt = s.segments[idx]
                val ref = segmentRefPool[segRefUsed++]
                ref.snake = s
                ref.segIndex = idx
                ref.x = pt.x
                ref.y = pt.y
                ref.radius = rad
                segmentGrid.insert(pt.x, pt.y, ref)
                idx += 2
            }
        }

        // Update AI Bots
        for (s in snakes) {
            if (!s.isPlayer && s.alive) {
                BotAiController.updateBot(
                    bot = s,
                    dt = dt,
                    arenaRadius = arenaRadius,
                    playerScore = player.score,
                    allSnakes = snakes,
                    foodGrid = foodGrid,
                    segmentGrid = segmentGrid
                )
            }
        }

        // Update Snake Movement & Boost Energy Bar
        for (s in snakes) {
            if (!s.alive) continue
            if (s.invulnerableTimer > 0f) s.invulnerableTimer = (s.invulnerableTimer - dt).coerceAtLeast(0f)

            val minBoostThreshold = if (s.activelyBoosting) 0.02f else 0.12f
            val canBoost = s.wantsBoost &&
                s.boostEnergy > minBoostThreshold &&
                s.desiredLength > MIN_BOOST_LENGTH

            s.activelyBoosting = canBoost

            if (canBoost) {
                s.boostEnergy = (s.boostEnergy - dt * 0.28f).coerceAtLeast(0f)
                s.desiredLength = (s.desiredLength - dt * 1.45f).coerceAtLeast(MIN_BOOST_LENGTH)
                s.boostPelletAccum += dt
                if (s.boostPelletAccum >= 0.17f) {
                    s.boostPelletAccum = 0f
                    s.segments.lastOrNull()?.let { tail ->
                        spawnFoodAt(
                            x = tail.x + Random.nextFloat() * 10f - 5f,
                            y = tail.y + Random.nextFloat() * 10f - 5f,
                            kind = FoodKind.BOOST_PELLET,
                            customColor = s.skin.colors.first()
                        )
                    }
                }
                if (s.isPlayer) audio.playBoostPulse()
            } else {
                s.boostEnergy = (s.boostEnergy + dt * 0.11f).coerceAtMost(1.0f)
            }

            val baseSpeed = 225f * mode.speedMultiplier
            val speed = if (canBoost) baseSpeed * 1.66f else baseSpeed

            val turnRate = 5.3f - (s.bodyRadius * 0.028f).coerceAtMost(1.3f)
            val diff = shortestAngleDiff(s.angle, s.targetAngle)
            val maxTurn = turnRate * dt
            s.angle += diff.coerceIn(-maxTurn, maxTurn)

            val nx = s.headX + cos(s.angle) * speed * dt
            val ny = s.headY + sin(s.angle) * speed * dt

            if (s.segments.isEmpty()) {
                s.segments.add(SegmentPoint(nx, ny))
            } else {
                s.segments[0].x = nx
                s.segments[0].y = ny
            }

            for (i in 1 until s.segments.size) {
                val prev = s.segments[i - 1]
                val curr = s.segments[i]
                val dx = prev.x - curr.x
                val dy = prev.y - curr.y
                val dist = hypot(dx, dy)
                if (dist > SEGMENT_SPACING && dist > 0.001f) {
                    val pull = dist - SEGMENT_SPACING
                    curr.x += (dx / dist) * pull
                    curr.y += (dy / dist) * pull
                }
            }

            val targetCount = s.desiredLength.toInt().coerceIn(10, 240)
            while (s.segments.size < targetCount) {
                val tail = s.segments.last()
                s.segments.add(SegmentPoint(tail.x, tail.y))
            }
            while (s.segments.size > targetCount && s.segments.size > 10) {
                s.segments.removeAt(s.segments.lastIndex)
            }
        }

        // Eat Food
        for (s in snakes) {
            if (!s.alive) continue
            val hx = s.headX
            val hy = s.headY
            val eatR = s.bodyRadius + 22f
            val magR = eatR + 32f

            foodGrid.queryRadius(hx, hy, magR) { food ->
                if (food.active) {
                    val dx = hx - food.x
                    val dy = hy - food.y
                    val d = hypot(dx, dy)
                    if (d < eatR) {
                        consumeFood(s, food)
                    } else if (d < magR && d > 1f) {
                        food.x += (dx / d) * 230f * dt
                        food.y += (dy / d) * 230f * dt
                    }
                }
            }
        }

        checkCollisions()

        // Replenish Food & Bots
        val activeFood = foodPool.count { it.active }
        if (activeFood < mode.foodCount) {
            repeat((mode.foodCount - activeFood).coerceAtMost(5)) {
                spawnRandomArenaFood()
            }
        }

        val aliveBots = snakes.count { !it.isPlayer && it.alive }
        if (aliveBots < mode.botCount) {
            snakes.removeAll { !it.isPlayer && !it.alive }
            spawnBot(initialSpawn = false)
        }

        // Particles & Popups
        for (p in particlePool) {
            if (!p.active) continue
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= 0.93f
            p.vy *= 0.93f
            p.life -= dt
            if (p.life <= 0f) p.active = false
        }
        for (pop in popupPool) {
            if (!pop.active) continue
            pop.y -= 44f * dt
            pop.life -= dt
            if (pop.life <= 0f) pop.active = false
        }

        // Smooth Camera Follow
        if (player.alive) {
            val lead = if (player.activelyBoosting) 52f else 26f
            val tx = player.headX + cos(player.angle) * lead
            val ty = player.headY + sin(player.angle) * lead
            cameraX += (tx - cameraX) * (10f * dt).coerceAtMost(1f)
            cameraY += (ty - cameraY) * (10f * dt).coerceAtMost(1f)

            val desiredZoom = (1.03f - (player.desiredLength - 20f) * 0.0016f).coerceIn(0.68f, 1.02f)
            cameraZoom += (desiredZoom - cameraZoom) * (3f * dt).coerceAtMost(1f)
        }
    }

    private fun consumeFood(snake: SnakeEntity, food: FoodItem) {
        food.active = false
        snake.desiredLength += food.kind.growthValue
        snake.score += food.kind.scoreValue
        snake.boostEnergy = (snake.boostEnergy + 0.035f).coerceAtMost(1.0f)

        if (snake.isPlayer) {
            if (food.kind == FoodKind.COIN) {
                coinsCollectedThisRun++
                audio.playCoin()
                haptics.lightTick()
                onCoinPickedUp?.invoke(1)
                spawnPopup(food.x, food.y, "+1 COIN", Color(0xFFFFD83D))
            } else {
                audio.playEat(food.kind.rarityLevel)
                if (food.kind.rarityLevel >= 2) {
                    haptics.lightTick()
                    spawnPopup(food.x, food.y, "+${food.kind.scoreValue}", food.color)
                }
            }
            if (effectiveQuality != GraphicsQuality.LOW) {
                val cnt = if (effectiveQuality == GraphicsQuality.HIGH) 5 else 2
                repeat(cnt) { spawnParticle(food.x, food.y, food.color, 115f) }
            }
        }
    }

    private fun checkCollisions() {
        for (s in snakes) {
            if (!s.alive) continue
            val hx = s.headX
            val hy = s.headY

            // Touching Arena Border = Death
            if (hypot(hx, hy) + s.bodyRadius * 0.76f >= arenaRadius) {
                killSnake(victim = s, killer = null)
                continue
            }

            if (s.invulnerableTimer > 0f) continue

            val checkR = s.bodyRadius + 38f
            var dead = false
            segmentGrid.queryRadius(hx, hy, checkR) { ref ->
                if (!dead && ref.snake.id != s.id && ref.snake.alive && ref.snake.invulnerableTimer <= 0f) {
                    val other = ref.snake
                    if (ref.segIndex == 0) {
                        val hd = hypot(hx - other.headX, hy - other.headY)
                        if (hd < (s.bodyRadius + other.bodyRadius) * 0.82f) {
                            if (s.desiredLength < other.desiredLength) {
                                killSnake(victim = s, killer = other)
                                dead = true
                            } else if (other.desiredLength < s.desiredLength) {
                                killSnake(victim = other, killer = s)
                            } else if (!s.isPlayer) {
                                killSnake(victim = s, killer = other)
                                dead = true
                            }
                        }
                    } else {
                        val iStart = (ref.segIndex - 1).coerceAtLeast(1)
                        val iEnd = (ref.segIndex + 1).coerceAtMost(other.segments.lastIndex)
                        for (k in iStart..iEnd) {
                            val seg = other.segments[k]
                            if (hypot(hx - seg.x, hy - seg.y) < (s.bodyRadius * 0.72f + other.bodyRadius * 0.78f)) {
                                killSnake(victim = s, killer = other)
                                dead = true
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    private fun killSnake(victim: SnakeEntity, killer: SnakeEntity?) {
        if (!victim.alive) return
        victim.alive = false

        for (i in victim.segments.indices step 2) {
            val pt = victim.segments[i]
            val jx = pt.x + Random.nextFloat() * 18f - 9f
            val jy = pt.y + Random.nextFloat() * 18f - 9f
            val isCoin = (i % 8 == 0)
            spawnFoodAt(
                x = jx,
                y = jy,
                kind = if (isCoin) FoodKind.COIN else FoodKind.DEATH_DROP,
                customColor = if (isCoin) Color(0xFFFFD83D) else victim.skin.colors[i % victim.skin.colors.size]
            )
        }

        val burst = when (effectiveQuality) {
            GraphicsQuality.HIGH -> 20
            GraphicsQuality.MEDIUM -> 10
            GraphicsQuality.LOW -> 4
        }
        repeat(burst) {
            spawnParticle(victim.headX, victim.headY, victim.skin.headColor, 210f)
        }

        if (killer != null && killer.alive) {
            killer.kills += 1
            killer.score += 150
            if (killer.isPlayer) {
                audio.playKill()
                haptics.mediumImpact()
                shakeIntensity = 10f
                spawnPopup(victim.headX, victim.headY, "+150!", Color(0xFF56E84D))
            }
        }

        if (victim.isPlayer) {
            audio.playDeath()
            haptics.heavyCrash()
            shakeIntensity = 14f
            finishMatch()
        }
    }

    private fun finishMatch() {
        if (matchEnded) return
        matchEnded = true
        val rank = computePlayerRank()
        val coins = (player.score / 30) + (player.kills * 6) + coinsCollectedThisRun + 5
        val summary = MatchEndSummary(
            score = player.score,
            length = player.desiredLength.toInt(),
            kills = player.kills,
            rank = rank,
            coinsEarned = coins.coerceAtLeast(3),
            wasOnline = false
        )
        matchSummary = summary
        onMatchFinished?.invoke(summary)
    }

    private fun computePlayerRank(): Int =
        snakes.count { it.id != player.id && it.alive && it.score > player.score } + 1

    private fun createSnake(
        id: Int,
        name: String,
        isPlayer: Boolean,
        personality: BotPersonality,
        skin: SnakeSkin,
        startX: Float,
        startY: Float,
        initialLength: Float
    ): SnakeEntity {
        val s = SnakeEntity(id, name, isPlayer, personality, skin)
        s.desiredLength = initialLength
        s.angle = if (isPlayer) 0f else Random.nextFloat() * 2f * PI.toFloat() - PI.toFloat()
        s.targetAngle = s.angle
        for (i in 0 until initialLength.toInt()) {
            s.segments.add(
                SegmentPoint(
                    startX - cos(s.angle) * i * SEGMENT_SPACING,
                    startY - sin(s.angle) * i * SEGMENT_SPACING
                )
            )
        }
        return s
    }

    private fun spawnBot(initialSpawn: Boolean) {
        val id = nextBotId++
        val name = BotAiController.botNames[id % BotAiController.botNames.size]
        val personality = when (id % 3) {
            0 -> BotPersonality.AGGRESSIVE
            1 -> BotPersonality.CAUTIOUS
            else -> BotPersonality.FOOD_HUNTER
        }
        val skin = SkinsConfig.allSkins[id % SkinsConfig.allSkins.size]
        var sx: Float
        var sy: Float
        var tries = 0
        do {
            val ang = Random.nextFloat() * 2f * PI.toFloat()
            val d = (0.18f + Random.nextFloat() * 0.72f) * (arenaRadius - 220f)
            sx = cos(ang) * d
            sy = sin(ang) * d
            tries++
        } while (::player.isInitialized && hypot(sx - player.headX, sy - player.headY) < 420f && tries < 10)

        val startLen = if (initialSpawn) Random.nextInt(16, 44).toFloat() else Random.nextInt(16, 32).toFloat()
        val bot = createSnake(id, name, false, personality, skin, sx, sy, startLen)
        bot.score = (startLen * 10f).toInt()
        bot.invulnerableTimer = 1.2f
        snakes.add(bot)
    }

    private fun spawnRandomArenaFood() {
        val ang = Random.nextFloat() * 2f * PI.toFloat()
        val r = kotlin.math.sqrt(Random.nextFloat()) * (arenaRadius - 95f)
        val fx = cos(ang) * r
        val fy = sin(ang) * r

        val roll = Random.nextFloat()
        val kind = when {
            roll < 0.60f -> FoodKind.GLOW_ORB
            roll < 0.76f -> FoodKind.APPLE
            roll < 0.88f -> FoodKind.CUPCAKE
            roll < 0.96f -> FoodKind.CROISSANT
            else -> FoodKind.COIN
        }
        spawnFoodAt(fx, fy, kind, null)
    }

    private fun spawnFoodAt(x: Float, y: Float, kind: FoodKind, customColor: Color?) {
        val slot = foodPool.firstOrNull { !it.active } ?: return
        slot.active = true
        slot.x = x
        slot.y = y
        slot.kind = kind
        slot.radius = kind.baseRadius
        slot.phase = Random.nextFloat() * 6.28f
        slot.color = customColor ?: candyOrbColors[Random.nextInt(candyOrbColors.size)]
    }

    private fun spawnParticle(x: Float, y: Float, color: Color, speed: Float) {
        val p = particlePool.firstOrNull { !it.active } ?: return
        val ang = Random.nextFloat() * 2f * PI.toFloat()
        val spd = (0.3f + Random.nextFloat() * 0.7f) * speed
        p.active = true
        p.x = x
        p.y = y
        p.vx = cos(ang) * spd
        p.vy = sin(ang) * spd
        p.maxLife = 0.42f + Random.nextFloat() * 0.22f
        p.life = p.maxLife
        p.radius = 4f + Random.nextFloat() * 4.5f
        p.color = color
    }

    private fun spawnPopup(x: Float, y: Float, text: String, color: Color) {
        val pop = popupPool.firstOrNull { !it.active } ?: return
        pop.active = true
        pop.x = x
        pop.y = y
        pop.text = text
        pop.maxLife = 0.85f
        pop.life = 0.85f
        pop.color = color
    }

    private fun shortestAngleDiff(from: Float, to: Float): Float {
        var diff = (to - from) % (2f * PI.toFloat())
        if (diff > PI) diff -= 2f * PI.toFloat()
        if (diff < -PI) diff += 2f * PI.toFloat()
        return diff
    }

    companion object {
        const val SNAPSHOT_VERSION = 1
        private const val SEGMENT_SPACING = 11f
        private const val MIN_BOOST_LENGTH = 13f
    }
}
