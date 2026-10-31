package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.ServerConfig
import com.example.config.SkinsConfig
import com.example.engine.ArenaEngine
import com.example.services.AudioService
import com.example.services.GraphicsQuality
import com.example.services.HapticService
import com.example.services.StorageService
import com.example.services.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name and 14 clean skin IDs load properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("NeonCoil", context.getString(R.string.app_name))
        assertEquals(14, SkinsConfig.allSkins.size)
        assertTrue(SkinsConfig.allSkins.none { it.id.contains(" ") })
        assertEquals("carnival_twist", SkinsConfig.getById(" carnival_twist ").id)
    }

    @Test
    fun `controls and HUD size sliders default to 85 and 80 and persist`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = StorageService(context)
        val initial = UserSettings()
        assertEquals(85, initial.controlsSizePercent)
        assertEquals(80, initial.hudSizePercent)

        val current = storage.loadProfile()
        val updated = current.copy(
            nickname = "Coco",
            settings = current.settings.copy(controlsSizePercent = 125, hudSizePercent = 95)
        )
        storage.saveProfile(updated)

        val reloaded = storage.loadProfile()
        assertEquals("Coco", reloaded.nickname)
        assertEquals(125, reloaded.settings.controlsSizePercent)
        assertEquals(95, reloaded.settings.hudSizePercent)
    }

    @Test
    fun `offline session snapshot saves and restores exact state for Continue`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine1 = ArenaEngine(AudioService(), HapticService(context))
        engine1.startNewMatch(
            playerSkin = SkinsConfig.getById("mint_clover"),
            quality = GraphicsQuality.HIGH,
            playerNickname = "Sunny"
        )

        // Advance simulation and build score/movement
        engine1.setPlayerInput(0.6f, boosting = true)
        repeat(45) { engine1.updateFrame(1f / 60f) }
        engine1.player.score = 340

        val savedScore = engine1.player.score
        val savedHeadX = engine1.player.headX
        val savedHeadY = engine1.player.headY
        val savedSegCount = engine1.player.segments.size
        val hud = engine1.buildHudState()
        assertTrue("Top leaderboard should cap at 5 entries", hud.leaderboard.size <= 5)

        val json = engine1.toSnapshotJson()
        assertNotNull("Expected valid snapshot JSON", json)

        // Restore into a brand-new engine instance and verify exact match
        val engine2 = ArenaEngine(AudioService(), HapticService(context))
        val ok = engine2.restoreFromSnapshotJson(json!!, GraphicsQuality.HIGH)
        assertTrue("Snapshot restore should succeed", ok)
        assertTrue("Restored engine starts paused ready for 3-2-1 countdown", engine2.isPaused)
        assertEquals(savedScore, engine2.player.score)
        assertEquals(savedSegCount, engine2.player.segments.size)
        assertEquals(savedHeadX, engine2.player.headX, 0.2f)
        assertEquals(savedHeadY, engine2.player.headY, 0.2f)
        assertEquals("mint_clover", engine2.player.skin.id)
    }

    @Test
    fun `nickname validation enforces 3 to 12 chars and filters bad words`() {
        assertEquals("Ziggy_9", ServerConfig.validateNickname("  Ziggy_9  "))
        assertNull(ServerConfig.validateNickname("ab"))
        assertNull(ServerConfig.validateNickname("bad_shit_nick"))
        assertFalse(ServerConfig.wsUrl.isBlank())
    }
}
