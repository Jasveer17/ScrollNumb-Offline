package com.scrollnumb.app.core.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Dependency-free premium sound effects, synthesized as PCM at play time.
 * Soft sine bells with exponential decay — no audio assets to ship, no
 * disk reads, ~50ms generation on a background thread.
 */
object SoundFx {

    private const val SAMPLE_RATE = 44_100
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Two-tone ascending bell — ring completion / XP gained. */
    fun playChime() = play(
        tones = listOf(
            Tone(660f, 0.00f, 0.45f),  // E5
            Tone(990f, 0.12f, 0.55f)   // B5, slightly later, rings longer
        ),
        durationSec = 0.9f,
        volume = 0.35f
    )

    /** Soothing, cheerful major-triad arpeggio (C-E-G-C) with warm bell
     *  decay — correct answer / gentle reward. Rounder and calmer than the
     *  bright playPop sparkle. */
    fun playSuccess() = play(
        tones = listOf(
            Tone(523.25f, 0.00f, 0.55f), // C5
            Tone(659.25f, 0.09f, 0.55f), // E5
            Tone(783.99f, 0.18f, 0.60f), // G5
            Tone(1046.50f, 0.27f, 0.70f) // C6 — settles, rings longest
        ),
        durationSec = 1.1f,
        volume = 0.30f
    )

    /** Soft pop + sparkle — game win, milestone. */
    fun playPop() = play(
        tones = listOf(
            Tone(523f, 0.00f, 0.10f),  // C5 thump
            Tone(1046f, 0.05f, 0.30f), // C6 sparkle
            Tone(1568f, 0.10f, 0.35f)  // G6 shimmer
        ),
        durationSec = 0.6f,
        volume = 0.3f
    )

    private data class Tone(val freqHz: Float, val startSec: Float, val decaySec: Float)

    private fun play(tones: List<Tone>, durationSec: Float, volume: Float) {
        scope.launch {
            try {
                val frames = (SAMPLE_RATE * durationSec).toInt()
                val pcm = ShortArray(frames)
                for (i in 0 until frames) {
                    val t = i.toFloat() / SAMPLE_RATE
                    var sample = 0f
                    for (tone in tones) {
                        if (t < tone.startSec) continue
                        val lt = t - tone.startSec
                        val envelope = exp((-lt / tone.decaySec).toDouble()).toFloat()
                        // Fundamental + faint 2nd harmonic = warm bell timbre
                        sample += envelope * (
                            sin(2.0 * PI * tone.freqHz * lt).toFloat() * 0.8f +
                                sin(2.0 * PI * tone.freqHz * 2 * lt).toFloat() * 0.2f
                            )
                    }
                    pcm[i] = (sample.coerceIn(-1f, 1f) * volume * Short.MAX_VALUE).toInt().toShort()
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
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Release after the sound finishes
                kotlinx.coroutines.delay((durationSec * 1000).toLong() + 100)
                track.release()
            } catch (_: Exception) {
                // Sound is decoration — never crash for it.
            }
        }
    }
}
