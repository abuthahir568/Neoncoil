package com.example.services

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.exp
import kotlin.math.sin

/**
 * Cheerful Cartoon Procedural Audio Synthesizer (zero external audio files).
 * Generates bubbly button pops, happy treat chimes, boost hums, victory jingles, and upbeat background melody.
 */
class AudioService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile var soundEnabled: Boolean = true
    @Volatile var musicEnabled: Boolean = true
    @Volatile private var appInForeground: Boolean = true
    @Volatile private var gameplayPaused: Boolean = false

    private var musicJob: Job? = null
    private var lastBoostMs: Long = 0L

    fun onForegroundChanged(inForeground: Boolean) {
        appInForeground = inForeground
        syncMusicState()
    }

    fun setGameplayPaused(paused: Boolean) {
        gameplayPaused = paused
        syncMusicState()
    }

    fun applySettings(sound: Boolean, music: Boolean) {
        soundEnabled = sound
        musicEnabled = music
        syncMusicState()
    }

    private fun syncMusicState() {
        if (musicEnabled && appInForeground && !gameplayPaused) {
            startMusicIfNeeded()
        } else {
            stopMusic()
        }
    }

    fun playButtonClick() {
        if (!soundEnabled || !appInForeground) return
        // Bubbly two-note cartoon pop
        playSequence(
            listOf(
                NoteStep(580.0, 32, 0.24, Wave.SINE),
                NoteStep(860.0, 42, 0.26, Wave.SINE)
            )
        )
    }

    fun playEat(rarity: Int = 1) {
        if (!soundEnabled || !appInForeground) return
        val f = 540.0 + rarity * 115.0
        playSequence(
            listOf(
                NoteStep(f, 45, 0.25, Wave.SINE),
                NoteStep(f * 1.26, 60, 0.24, Wave.TRIANGLE)
            )
        )
    }

    fun playCoin() {
        if (!soundEnabled || !appInForeground) return
        playSequence(
            listOf(
                NoteStep(1046.5, 48, 0.25, Wave.SINE),
                NoteStep(1396.9, 95, 0.28, Wave.SINE)
            )
        )
    }

    fun playBoostPulse() {
        if (!soundEnabled || !appInForeground) return
        val now = System.currentTimeMillis()
        if (now - lastBoostMs < 200L) return
        lastBoostMs = now
        playSequence(listOf(NoteStep(320.0, 55, 0.11, Wave.TRIANGLE)))
    }

    fun playKill() {
        if (!soundEnabled || !appInForeground) return
        playSequence(
            listOf(
                NoteStep(523.25, 60, 0.28, Wave.TRIANGLE),
                NoteStep(659.25, 60, 0.28, Wave.TRIANGLE),
                NoteStep(783.99, 120, 0.32, Wave.SINE)
            )
        )
    }

    fun playDeath() {
        if (!soundEnabled || !appInForeground) return
        playSequence(
            listOf(
                NoteStep(440.0, 95, 0.28, Wave.TRIANGLE),
                NoteStep(349.2, 95, 0.28, Wave.TRIANGLE),
                NoteStep(261.6, 190, 0.28, Wave.SINE)
            )
        )
    }

    private fun startMusicIfNeeded() {
        if (musicJob?.isActive == true) return
        musicJob = scope.launch {
            // Happy, bouncy C-major cartoon marimba loop
            val notes = doubleArrayOf(
                523.25, 659.25, 783.99, 659.25,
                587.33, 698.46, 880.00, 698.46,
                523.25, 659.25, 783.99, 1046.50,
                783.99, 659.25, 587.33, 493.88
            )
            var i = 0
            while (isActive && musicEnabled && appInForeground && !gameplayPaused) {
                try {
                    val hz = notes[i % notes.size]
                    renderOneShot(listOf(NoteStep(hz, 165, 0.06, Wave.SINE)))
                    i++
                    delay(250L)
                } catch (_: Throwable) {
                    delay(500L)
                }
            }
        }
    }

    private fun stopMusic() {
        musicJob?.cancel()
        musicJob = null
    }

    private enum class Wave { SINE, TRIANGLE }
    private data class NoteStep(val hz: Double, val ms: Int, val vol: Double, val wave: Wave)

    private fun playSequence(steps: List<NoteStep>) {
        scope.launch { renderOneShot(steps) }
    }

    private fun renderOneShot(steps: List<NoteStep>) {
        try {
            val rate = 22050
            val total = steps.sumOf { (rate * it.ms) / 1000 }.coerceAtLeast(256)
            val pcm = ShortArray(total)
            var idx = 0
            for (st in steps) {
                val count = (rate * st.ms) / 1000
                var ph = 0.0
                val inc = 2.0 * PI * st.hz / rate
                for (k in 0 until count) {
                    if (idx >= pcm.size) break
                    val norm = k.toDouble() / count.coerceAtLeast(1)
                    val env = (k / 60.0).coerceAtMost(1.0) * exp(-2.8 * norm)
                    val sampleVal = when (st.wave) {
                        Wave.SINE -> sin(ph)
                        Wave.TRIANGLE -> (2.0 / PI) * asin(sin(ph))
                    }
                    ph += inc
                    pcm[idx++] = (sampleVal * env * st.vol * Short.MAX_VALUE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes((pcm.size * 2).coerceAtLeast(2048))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((steps.sumOf { it.ms } + 35).toLong())
            track.stop()
            track.release()
        } catch (_: Throwable) {
            // Safe fallback
        }
    }
}
