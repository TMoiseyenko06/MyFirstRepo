package com.beatshow.audio

import com.beatshow.engine.ClickSound
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Generates click samples in code (mono float, -1..1), so the app ships no audio assets. */
object ClickSynth {

    fun render(sound: ClickSound, accent: Boolean, sampleRate: Int): FloatArray = when (sound) {
        ClickSound.WOODBLOCK -> woodblock(if (accent) 1250.0 else 820.0, sampleRate, if (accent) 1f else 0.75f)
        ClickSound.BEEP -> beep(if (accent) 1760.0 else 1100.0, sampleRate, if (accent) 0.8f else 0.6f)
        ClickSound.HIHAT -> hihat(accent, sampleRate)
        ClickSound.COWBELL -> cowbell(if (accent) 1.35 else 1.0, sampleRate, if (accent) 0.9f else 0.65f)
    }

    private fun woodblock(freq: Double, sr: Int, gain: Float): FloatArray {
        val n = (0.06 * sr).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            val env = exp(-t * 70.0)
            val tone = sin(2 * PI * freq * t) + 0.35 * sin(2 * PI * freq * 2.71 * t) * exp(-t * 160.0)
            (gain * env * tone / 1.35).toFloat()
        }
    }

    private fun beep(freq: Double, sr: Int, gain: Float): FloatArray {
        val n = (0.07 * sr).toInt()
        val fade = (0.004 * sr).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            val env = when {
                i < fade -> i.toDouble() / fade
                i > n - fade -> (n - i).toDouble() / fade
                else -> 1.0
            }
            (gain * env * sin(2 * PI * freq * t)).toFloat()
        }
    }

    private fun hihat(accent: Boolean, sr: Int): FloatArray {
        val n = ((if (accent) 0.09 else 0.05) * sr).toInt()
        val decay = if (accent) 45.0 else 80.0
        val gain = if (accent) 0.9 else 0.6
        val random = Random(if (accent) 1 else 2)
        var prev = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            val noise = random.nextDouble(-1.0, 1.0)
            // First difference acts as a simple high-pass, giving the metallic hiss.
            val bright = noise - prev
            prev = noise
            // The accent gets a high ring on top so beat 1 sounds higher pitched.
            val ring = if (accent) 0.25 * sin(2 * PI * 7200.0 * t) else 0.0
            (gain * exp(-t * decay) * (0.5 * bright + ring)).toFloat()
        }
    }

    private fun cowbell(pitch: Double, sr: Int, gain: Float): FloatArray {
        val n = (0.12 * sr).toInt()
        val f1 = 540.0 * pitch
        val f2 = 800.0 * pitch
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            val square1 = if (sin(2 * PI * f1 * t) >= 0) 1.0 else -1.0
            val square2 = if (sin(2 * PI * f2 * t) >= 0) 1.0 else -1.0
            (gain * exp(-t * 30.0) * 0.35 * (square1 + square2)).toFloat()
        }
    }
}
