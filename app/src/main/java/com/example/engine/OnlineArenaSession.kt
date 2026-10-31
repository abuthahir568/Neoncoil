package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.config.ServerConfig
import com.example.config.SkinsConfig
import com.example.services.AudioService
import com.example.services.GraphicsQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

enum class OnlineJoinMode {
    PUBLIC,
    CREATE_PRIVATE,
    JOIN_PRIVATE
}

data class OnlineConnectParams(
    val joinMode: OnlineJoinMode,
    val nickname: String,
    val skinId: String,
    val roomCode: String = "",
    val privateBotsEnabled: Boolean = true
)

/**
 * Client-side Online Multiplayer Session with:
 * - 25 Hz input sender ({ t: "i", a, b, q })
 * - 60 FPS local snake prediction + smooth exponential error reconciliation (zero jitter)
 * - ~100ms snapshot interpolation buffer for remote snakes
 * - Spatial viewport food delta tracking (fa / fr)
 * - 5s connect timeout fallback & 10s sessionToken automatic reconnect
 */
class OnlineArenaSession(
    private val audio: AudioService
) : ArenaRenderSource {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val healthClient = OkHttpClient.Builder()
        .connectTimeout(2500, TimeUnit.MILLISECONDS)
        .readTimeout(2500, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var connectTimeoutJob: Job? = null
    private var inputSenderJob: Job? = null
    private var reconnectJob: Job? = null

    @Volatile var isConnectedAndJoined: Boolean = false
        private set
    @Volatile private var intentionalClose: Boolean = false

    private var lastParams: OnlineConnectParams? = null
    private var sessionToken: String? = null
    private var mySnakeId: Int = -1
    var roomCode: String? = null
        private set
    var isPrivateRoom: Boolean = false
        private set
    var isPrivateHost: Boolean = false
        private set
    var privateBotsEnabled: Boolean = true
        private set

    override var arenaRadius: Float = 2800f
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
    override var effectiveQuality: GraphicsQuality = GraphicsQuality.HIGH

    // Input state sent at 25 Hz
    @Volatile private var latestSteerAngle: Float? = null
    @Volatile private var latestBoost: Boolean = false
    private var inputSeq: Int = 0

    // Local prediction & reconciliation offsets
    private var localPredictedSnake: SnakeEntity? = null
    private var reconcileOffsetX: Float = 0f
    private var reconcileOffsetY: Float = 0f

    // Remote snake interpolation state (~100ms delay)
    private class RemoteSnakeInterp(
        val entity: SnakeEntity,
        var prevSnapMs: Long,
        var currSnapMs: Long,
        var prevCoords: FloatArray,
        var currCoords: FloatArray,
        var prevAngle: Float,
        var currAngle: Float
    )

    private val remoteInterpMap = ConcurrentHashMap<Int, RemoteSnakeInterp>()
    private val activeFoodsMap = ConcurrentHashMap<Int, FoodItem>()

    override val renderParticles: Array<Particle> = emptyArray()
    override val renderPopups: Array<FloatingPopup> = emptyArray()

    @Volatile private var cachedRenderSnakes: List<SnakeEntity> = emptyList()
    @Volatile private var cachedMinimapDots: List<MinimapDot> = emptyList()

    override val renderSnakes: List<SnakeEntity>
        get() = cachedRenderSnakes

    override val renderFoods: Iterable<FoodItem>
        get() = activeFoodsMap.values

    override val minimapDots: List<MinimapDot>
        get() = cachedMinimapDots

    // HUD state
    @Volatile private var latestPingMs: Int? = null
    @Volatile private var latestScore: Int = 0
    @Volatile private var latestKills: Int = 0
    @Volatile private var latestRank: Int = 1
    @Volatile private var latestAliveCount: Int = 12
    @Volatile private var latestBoostEnergy: Float = 1.0f
    @Volatile private var latestTop5: List<LeaderboardEntry> = emptyList()
    private val killFeedList = ArrayDeque<KillFeedEntry>()
    private var nextKillId = 1L
    private var hudEmitAccum = 0f

    // Callbacks (dispatched safely)
    var onWelcomeJoined: (() -> Unit)? = null
    var onConnectionFailed: ((String?) -> Unit)? = null
    var onCoinsPickedUp: ((Int) -> Unit)? = null
    var onPlayerDied: ((MatchEndSummary) -> Unit)? = null

    private val candyOrbColors = listOf(
        Color(0xFFFFDE38),
        Color(0xFF56E84D),
        Color(0xFF2FE6DE),
        Color(0xFFFF7AD9),
        Color(0xFFA87BFF),
        Color(0xFFFF8A3D)
    )

    suspend fun fetchOnlineCount(): Int = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(ServerConfig.healthHttpUrl).get().build()
            healthClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext 0
                val body = resp.body?.string().orEmpty()
                JSONObject(body).optInt("online", 0)
            }
        } catch (_: Throwable) {
            0
        }
    }

    fun connect(params: OnlineConnectParams, quality: GraphicsQuality) {
        disconnect(sendLeave = false)
        intentionalClose = false
        lastParams = params
        effectiveQuality = quality
        isConnectedAndJoined = false
        sessionToken = null
        mySnakeId = -1
        localPredictedSnake = null
        reconcileOffsetX = 0f
        reconcileOffsetY = 0f
        remoteInterpMap.clear()
        activeFoodsMap.clear()
        synchronized(killFeedList) { killFeedList.clear() }

        openWebSocket(isReconnect = false)
    }

    private fun openWebSocket(isReconnect: Boolean) {
        val params = lastParams ?: return
        connectTimeoutJob?.cancel()
        connectTimeoutJob = ioScope.launch {
            delay(ServerConfig.CONNECT_TIMEOUT_MS)
            if (!isConnectedAndJoined && !intentionalClose) {
                runCatching { webSocket?.cancel() }
                onConnectionFailed?.invoke(null)
            }
        }

        val req = Request.Builder().url(ServerConfig.wsUrl).build()
        webSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val joinObj = JSONObject()
                joinObj.put("t", "join")
                joinObj.put("nick", params.nickname)
                joinObj.put("skin", params.skinId)
                if (isReconnect && !sessionToken.isNullOrBlank()) {
                    joinObj.put("tok", sessionToken)
                }
                when (params.joinMode) {
                    OnlineJoinMode.PUBLIC -> joinObj.put("mode", "public")
                    OnlineJoinMode.CREATE_PRIVATE -> {
                        joinObj.put("mode", "create_private")
                        joinObj.put("bots", params.privateBotsEnabled)
                    }
                    OnlineJoinMode.JOIN_PRIVATE -> {
                        joinObj.put("mode", "join_private")
                        joinObj.put("code", params.roomCode.trim().uppercase())
                    }
                }
                webSocket.send(joinObj.toString())
                startInputAndPingLoops(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleUnexpectedDrop()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleUnexpectedDrop()
            }
        })
    }

    private fun startInputAndPingLoops(ws: WebSocket) {
        inputSenderJob?.cancel()
        inputSenderJob = ioScope.launch {
            var pingTick = 0
            while (isActive && !intentionalClose) {
                if (isConnectedAndJoined) {
                    inputSeq++
                    val pkt = JSONObject()
                    pkt.put("t", "i")
                    latestSteerAngle?.let { pkt.put("a", (it * 1000f).toInt() / 1000.0) }
                    pkt.put("b", if (latestBoost) 1 else 0)
                    pkt.put("q", inputSeq)
                    runCatching { ws.send(pkt.toString()) }

                    pingTick++
                    if (pingTick >= 30) { // Every ~1.2s
                        pingTick = 0
                        val ping = JSONObject()
                        ping.put("t", "p")
                        ping.put("ts", System.currentTimeMillis())
                        runCatching { ws.send(ping.toString()) }
                    }
                }
                delay(ServerConfig.INPUT_SEND_INTERVAL_MS)
            }
        }
    }

    private fun handleUnexpectedDrop() {
        inputSenderJob?.cancel()
        if (intentionalClose) return

        if (!isConnectedAndJoined) {
            connectTimeoutJob?.cancel()
            onConnectionFailed?.invoke(null)
            return
        }

        // Attempt automatic reconnect within the 10-second server grace window
        isConnectedAndJoined = false
        val tok = sessionToken
        if (tok.isNullOrBlank()) {
            onConnectionFailed?.invoke(null)
            return
        }

        if (reconnectJob?.isActive == true) return
        reconnectJob = ioScope.launch {
            val deadline = System.currentTimeMillis() + ServerConfig.RECONNECT_GRACE_MS - 1200L
            while (isActive && !intentionalClose && !isConnectedAndJoined && System.currentTimeMillis() < deadline) {
                delay(900L)
                if (!intentionalClose && !isConnectedAndJoined) {
                    openWebSocket(isReconnect = true)
                    delay(2000L)
                }
            }
            if (!isConnectedAndJoined && !intentionalClose) {
                onConnectionFailed?.invoke(null)
            }
        }
    }

    private fun handleServerMessage(rawText: String) {
        try {
            val msg = JSONObject(rawText)
            when (msg.optString("t")) {
                "welcome" -> {
                    connectTimeoutJob?.cancel()
                    reconnectJob?.cancel()
                    sessionToken = msg.optString("tok", sessionToken ?: "")
                    mySnakeId = msg.optInt("id", -1)
                    isPrivateRoom = msg.optInt("prv", 0) == 1
                    roomCode = if (isPrivateRoom) msg.optString("rm", "") else null
                    isPrivateHost = msg.optInt("hst", 0) == 1
                    privateBotsEnabled = msg.optInt("bots", 1) == 1
                    arenaRadius = msg.optInt("ar", 2800).toFloat()
                    activeFoodsMap.clear()
                    isConnectedAndJoined = true
                    onWelcomeJoined?.invoke()
                }

                "meta" -> {
                    isPrivateHost = msg.optInt("hst", 0) == 1
                    privateBotsEnabled = msg.optInt("bots", 1) == 1
                }

                "p" -> {
                    val sentTs = msg.optLong("ts", 0L)
                    if (sentTs > 0L) {
                        latestPingMs = (System.currentTimeMillis() - sentTs).toInt().coerceIn(1, 999)
                    }
                }

                "k" -> {
                    val kr = msg.optString("kr", "")
                    val vc = msg.optString("vc", "")
                    if (kr.isNotBlank() && vc.isNotBlank()) {
                        val entry = KillFeedEntry(
                            id = nextKillId++,
                            text = if (kr == "Border") "$vc hit the wall" else "$kr ate $vc",
                            createdAtMs = System.currentTimeMillis()
                        )
                        synchronized(killFeedList) {
                            killFeedList.addFirst(entry)
                            while (killFeedList.size > 3) killFeedList.removeLast()
                        }
                    }
                }

                "err" -> {
                    connectTimeoutJob?.cancel()
                    val errText = msg.optString("msg", "Room error")
                    onConnectionFailed?.invoke(errText)
                }

                "dead" -> {
                    audio.playDeath()
                    val summary = MatchEndSummary(
                        score = msg.optInt("sc", latestScore),
                        length = msg.optInt("ln", 20),
                        kills = msg.optInt("kl", latestKills),
                        rank = msg.optInt("rk", latestRank),
                        coinsEarned = msg.optInt("cn", 5),
                        wasOnline = true
                    )
                    localPredictedSnake?.alive = false
                    onPlayerDied?.invoke(summary)
                }

                "s" -> {
                    applyStateSnapshot(msg)
                }
            }
        } catch (_: Throwable) {
            // Safely ignore malformed server frames
        }
    }

    private fun applyStateSnapshot(msg: JSONObject) {
        val nowMs = System.currentTimeMillis()
        latestBoostEnergy = (msg.optInt("e", 100) / 100f).coerceIn(0f, 1f)
        latestScore = msg.optInt("sc", 0)
        latestKills = msg.optInt("kl", 0)
        latestRank = msg.optInt("rk", 1)
        latestAliveCount = msg.optInt("ac", 12)

        val coinsAdded = msg.optInt("cn", 0)
        if (coinsAdded > 0) {
            audio.playCoin()
            onCoinsPickedUp?.invoke(coinsAdded)
        }

        // Leaderboard Top 5
        msg.optJSONArray("lb")?.let { lbArr ->
            val list = ArrayList<LeaderboardEntry>(5)
            for (i in 0 until minOf(5, lbArr.length())) {
                val item = lbArr.optJSONObject(i) ?: continue
                val sid = item.optInt("i", -1)
                list.add(
                    LeaderboardEntry(
                        rank = i + 1,
                        name = item.optString("n", "Bot"),
                        score = item.optInt("s", 0),
                        isPlayer = sid == mySnakeId
                    )
                )
            }
            latestTop5 = list
        }

        // Food deltas (fa = added, fr = removed)
        msg.optJSONArray("fa")?.let { fa ->
            val kinds = FoodKind.entries
            for (i in 0 until fa.length()) {
                val tup = fa.optJSONArray(i) ?: continue
                if (tup.length() < 5) continue
                val fid = tup.optInt(0)
                val fx = tup.optInt(1).toFloat()
                val fy = tup.optInt(2).toFloat()
                val kindCode = tup.optInt(3, 1).coerceIn(0, kinds.lastIndex)
                val colorIdx = tup.optInt(4, 0)
                val kind = kinds[kindCode]
                val item = FoodItem().apply {
                    active = true
                    x = fx
                    y = fy
                    this.kind = kind
                    radius = kind.baseRadius
                    color = if (kind == FoodKind.COIN) {
                        Color(0xFFFFD83D)
                    } else {
                        candyOrbColors[colorIdx.mod(candyOrbColors.size)]
                    }
                    phase = (fid % 6) * 1.0f
                }
                activeFoodsMap[fid] = item
            }
        }

        msg.optJSONArray("fr")?.let { fr ->
            for (i in 0 until fr.length()) {
                activeFoodsMap.remove(fr.optInt(i))
            }
        }

        // Minimap dots
        msg.optJSONArray("mm")?.let { mm ->
            val dots = ArrayList<MinimapDot>(mm.length() / 3)
            var idx = 0
            while (idx + 2 < mm.length()) {
                val sid = mm.optInt(idx)
                val mx = mm.optInt(idx + 1).toFloat()
                val my = mm.optInt(idx + 2).toFloat()
                dots.add(MinimapDot(sid, mx, my, sid == mySnakeId))
                idx += 3
            }
            cachedMinimapDots = dots
        }

        // Visible snakes (local reconciled + remote buffered for 100ms interpolation)
        val seenRemoteIds = HashSet<Int>()
        msg.optJSONArray("sn")?.let { snArr ->
            for (i in 0 until snArr.length()) {
                val sObj = snArr.optJSONObject(i) ?: continue
                val sid = sObj.optInt("i", -1)
                if (sid < 0) continue

                val name = sObj.optString("n", "Player")
                val skin = SkinsConfig.getById(sObj.optString("k", "rainbow_coil"))
                val angle = sObj.optInt("a", 0) / 1000f
                val desLen = sObj.optInt("l", 20).toFloat()
                val score = sObj.optInt("s", 0)
                val boosting = sObj.optInt("b", 0) == 1
                val ptsJson = sObj.optJSONArray("p") ?: continue
                val coords = FloatArray(ptsJson.length())
                for (c in 0 until ptsJson.length()) {
                    coords[c] = ptsJson.optInt(c, 0).toFloat()
                }
                if (coords.size < 2) continue

                if (sid == mySnakeId) {
                    reconcileLocalSnake(name, skin, angle, desLen, score, boosting, coords)
                } else {
                    seenRemoteIds.add(sid)
                    val existing = remoteInterpMap[sid]
                    if (existing == null) {
                        val ent = SnakeEntity(sid, name, isPlayer = false, skin = skin)
                        ent.desiredLength = desLen
                        ent.score = score
                        ent.activelyBoosting = boosting
                        ent.angle = angle
                        syncEntitySegmentsFromFlat(ent, coords)
                        remoteInterpMap[sid] = RemoteSnakeInterp(
                            entity = ent,
                            prevSnapMs = nowMs - 50L,
                            currSnapMs = nowMs,
                            prevCoords = coords,
                            currCoords = coords,
                            prevAngle = angle,
                            currAngle = angle
                        )
                    } else {
                        existing.entity.name = name
                        existing.entity.skin = skin
                        existing.entity.desiredLength = desLen
                        existing.entity.score = score
                        existing.entity.activelyBoosting = boosting
                        existing.prevCoords = existing.currCoords
                        existing.prevAngle = existing.currAngle
                        existing.prevSnapMs = existing.currSnapMs
                        existing.currCoords = coords
                        existing.currAngle = angle
                        existing.currSnapMs = nowMs
                    }
                }
            }
        }

        // Prune remote snakes no longer in viewport
        val it = remoteInterpMap.keys.iterator()
        while (it.hasNext()) {
            val k = it.next()
            if (!seenRemoteIds.contains(k)) {
                it.remove()
            }
        }
    }

    private fun reconcileLocalSnake(
        name: String,
        skin: com.example.config.SnakeSkin,
        serverAngle: Float,
        desLen: Float,
        score: Int,
        boosting: Boolean,
        serverCoords: FloatArray
    ) {
        val srvHx = serverCoords[0]
        val srvHy = serverCoords[1]
        val current = localPredictedSnake
        if (current == null || !current.alive || current.segments.isEmpty()) {
            val created = SnakeEntity(mySnakeId, name, isPlayer = true, skin = skin)
            created.angle = serverAngle
            created.targetAngle = latestSteerAngle ?: serverAngle
            created.desiredLength = desLen
            created.score = score
            created.activelyBoosting = boosting
            syncEntitySegmentsFromFlat(created, serverCoords)
            localPredictedSnake = created
            cameraX = srvHx
            cameraY = srvHy
            reconcileOffsetX = 0f
            reconcileOffsetY = 0f
            return
        }

        // Measure current rendered head position before snapping authoritative body
        val oldVisualHx = current.headX + reconcileOffsetX
        val oldVisualHy = current.headY + reconcileOffsetY

        current.name = name
        current.skin = skin
        current.desiredLength = desLen
        current.score = score
        current.activelyBoosting = boosting
        current.angle = lerpAngle(current.angle, serverAngle, 0.45f)

        // Blend authoritative head + segments while carrying smooth visual offset so there is ZERO snap!
        val errDist = hypot(oldVisualHx - srvHx, oldVisualHy - srvHy)
        if (errDist > 260f) {
            // Only hard-correct if wildly desynced (e.g. respawn)
            syncEntitySegmentsFromFlat(current, serverCoords)
            reconcileOffsetX = 0f
            reconcileOffsetY = 0f
        } else {
            syncEntitySegmentsFromFlat(current, serverCoords)
            reconcileOffsetX = oldVisualHx - srvHx
            reconcileOffsetY = oldVisualHy - srvHy
        }
    }

    fun setPlayerInput(angleRadians: Float?, boosting: Boolean) {
        if (angleRadians != null) {
            latestSteerAngle = angleRadians
        }
        latestBoost = boosting
    }

    /**
     * Runs at 60 FPS on the UI frame clock: predicts local snake smoothly and interpolates remote snakes at -100ms.
     */
    fun updateRenderFrame(rawDt: Float): HudState? {
        val dt = rawDt.coerceIn(0.001f, 0.06f)
        elapsedSec += dt

        // 1. Predict local snake forward at 60 FPS & decay reconciliation offset smoothly
        val me = localPredictedSnake
        if (me != null && me.alive && me.segments.isNotEmpty()) {
            val decay = exp(-14f * dt)
            reconcileOffsetX *= decay
            reconcileOffsetY *= decay

            val targetAng = latestSteerAngle ?: me.angle
            val turnRate = 5.3f - (me.bodyRadius * 0.028f).coerceAtMost(1.3f)
            val diff = shortestAngleDiff(me.angle, targetAng)
            val maxTurn = turnRate * dt
            me.angle += diff.coerceIn(-maxTurn, maxTurn)

            val canBoost = latestBoost && latestBoostEnergy > 0.06f && me.desiredLength > 13f
            me.activelyBoosting = canBoost
            if (canBoost) audio.playBoostPulse()

            val speed = if (canBoost) 225f * 1.66f else 225f
            me.segments[0].x += cos(me.angle) * speed * dt
            me.segments[0].y += sin(me.angle) * speed * dt

            for (i in 1 until me.segments.size) {
                val prev = me.segments[i - 1]
                val curr = me.segments[i]
                val dx = prev.x - curr.x
                val dy = prev.y - curr.y
                val dist = hypot(dx, dy)
                if (dist > 11f && dist > 0.001f) {
                    val pull = dist - 11f
                    curr.x += (dx / dist) * pull
                    curr.y += (dy / dist) * pull
                }
            }

            val visualHx = me.headX + reconcileOffsetX
            val visualHy = me.headY + reconcileOffsetY
            val lead = if (me.activelyBoosting) 48f else 24f
            val tx = visualHx + cos(me.angle) * lead
            val ty = visualHy + sin(me.angle) * lead
            cameraX += (tx - cameraX) * (10f * dt).coerceAtMost(1f)
            cameraY += (ty - cameraY) * (10f * dt).coerceAtMost(1f)
            val desiredZoom = (1.03f - (me.desiredLength - 20f) * 0.0016f).coerceIn(0.68f, 1.02f)
            cameraZoom += (desiredZoom - cameraZoom) * (3f * dt).coerceAtMost(1f)
        }

        // 2. Interpolate remote snakes (~100ms render buffer)
        val renderTimeMs = System.currentTimeMillis() - ServerConfig.INTERPOLATION_DELAY_MS
        val outSnakes = ArrayList<SnakeEntity>(remoteInterpMap.size + 1)

        for (interp in remoteInterpMap.values) {
            val span = (interp.currSnapMs - interp.prevSnapMs).coerceAtLeast(1L)
            // Allow slight extrapolation up to 1.25 if a packet is slightly late
            val alpha = ((renderTimeMs - interp.prevSnapMs).toFloat() / span.toFloat()).coerceIn(0f, 1.2f)
            interp.entity.angle = lerpAngle(interp.prevAngle, interp.currAngle, alpha.coerceIn(0f, 1f))

            val pairCount = minOf(interp.prevCoords.size, interp.currCoords.size) / 2
            val currTotalPairs = interp.currCoords.size / 2
            ensureSegmentCount(interp.entity, currTotalPairs)

            for (sIdx in 0 until currTotalPairs) {
                val cx = interp.currCoords[sIdx * 2]
                val cy = interp.currCoords[sIdx * 2 + 1]
                if (sIdx < pairCount) {
                    val px = interp.prevCoords[sIdx * 2]
                    val py = interp.prevCoords[sIdx * 2 + 1]
                    interp.entity.segments[sIdx].x = px + (cx - px) * alpha
                    interp.entity.segments[sIdx].y = py + (cy - py) * alpha
                } else {
                    interp.entity.segments[sIdx].x = cx
                    interp.entity.segments[sIdx].y = cy
                }
            }
            outSnakes.add(interp.entity)
        }

        if (me != null && me.alive && me.segments.isNotEmpty()) {
            // Build display copy shifted by smooth reconcileOffset so rendering has zero snap
            val displayMe = SnakeEntity(me.id, me.name, isPlayer = true, skin = me.skin).also {
                it.angle = me.angle
                it.desiredLength = me.desiredLength
                it.score = latestScore
                it.activelyBoosting = me.activelyBoosting
                for (pt in me.segments) {
                    it.segments.add(SegmentPoint(pt.x + reconcileOffsetX, pt.y + reconcileOffsetY))
                }
            }
            outSnakes.add(displayMe)
        }
        cachedRenderSnakes = outSnakes

        hudEmitAccum += dt
        if (hudEmitAccum >= 0.10f) {
            hudEmitAccum = 0f
            val nowMs = System.currentTimeMillis()
            val activeKills = synchronized(killFeedList) {
                while (killFeedList.isNotEmpty() && nowMs - killFeedList.last().createdAtMs > 4200L) {
                    killFeedList.removeLast()
                }
                killFeedList.toList()
            }
            return HudState(
                score = latestScore,
                length = localPredictedSnake?.desiredLength?.toInt() ?: 20,
                kills = latestKills,
                playerRank = latestRank,
                aliveSnakesCount = latestAliveCount,
                leaderboard = latestTop5,
                boostEnergy = latestBoostEnergy,
                isOnline = true,
                pingMs = latestPingMs,
                killFeed = activeKills,
                roomCode = roomCode,
                isPrivateHost = isPrivateHost,
                privateBotsEnabled = privateBotsEnabled
            )
        }
        return null
    }

    fun respawnInCurrentRoom(skinId: String) {
        val ws = webSocket
        if (ws != null && isConnectedAndJoined) {
            val obj = JSONObject()
            obj.put("t", "respawn")
            obj.put("skin", skinId)
            ws.send(obj.toString())
        } else {
            lastParams?.let { connect(it.copy(skinId = skinId), effectiveQuality) }
        }
    }

    fun togglePrivateRoomBots(enabled: Boolean) {
        if (!isPrivateHost) return
        privateBotsEnabled = enabled
        val obj = JSONObject()
        obj.put("t", "set_bots")
        obj.put("bots", enabled)
        runCatching { webSocket?.send(obj.toString()) }
    }

    fun disconnect(sendLeave: Boolean = true) {
        intentionalClose = true
        isConnectedAndJoined = false
        connectTimeoutJob?.cancel()
        inputSenderJob?.cancel()
        reconnectJob?.cancel()
        val ws = webSocket
        webSocket = null
        if (ws != null) {
            runCatching {
                if (sendLeave) {
                    ws.send("""{"t":"leave"}""")
                }
                ws.close(1000, "Leaving")
            }
        }
    }

    private fun syncEntitySegmentsFromFlat(entity: SnakeEntity, coords: FloatArray) {
        val count = coords.size / 2
        ensureSegmentCount(entity, count)
        for (i in 0 until count) {
            entity.segments[i].x = coords[i * 2]
            entity.segments[i].y = coords[i * 2 + 1]
        }
    }

    private fun ensureSegmentCount(entity: SnakeEntity, count: Int) {
        while (entity.segments.size < count) {
            entity.segments.add(SegmentPoint(0f, 0f))
        }
        while (entity.segments.size > count && entity.segments.isNotEmpty()) {
            entity.segments.removeAt(entity.segments.lastIndex)
        }
    }

    private fun shortestAngleDiff(from: Float, to: Float): Float {
        var diff = (to - from) % (2f * PI.toFloat())
        if (diff > PI) diff -= 2f * PI.toFloat()
        if (diff < -PI) diff += 2f * PI.toFloat()
        return diff
    }

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        return a + shortestAngleDiff(a, b) * t.coerceIn(0f, 1f)
    }
}
