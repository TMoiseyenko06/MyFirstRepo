package com.beatshow.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import com.beatshow.engine.BeatEvent
import com.beatshow.engine.ClickSound
import com.beatshow.engine.Conductor
import com.beatshow.engine.ConductorEvent
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Streams the metronome through an [AudioTrack] in MODE_STREAM.
 *
 * The audio thread asks the [Conductor] which events fall inside each buffer and mixes
 * clicks in at their exact sample offsets. The same events are queued for the UI, which
 * applies them once [playedFrame] (the audio clock) reaches their frame, so the visuals
 * follow what is actually coming out of the speaker.
 */
class MetronomeEngine(val sampleRate: Int = DEFAULT_SAMPLE_RATE) {

    /** Events produced ahead of playback, in frame order, waiting for the UI. */
    val events = ConcurrentLinkedQueue<ConductorEvent>()

    @Volatile var volume: Float = 0.8f
    @Volatile var clickEnabled: Boolean = true

    @Volatile private var running = false
    @Volatile private var track: AudioTrack? = null
    @Volatile private var framesWritten = 0L
    private var thread: Thread? = null
    private val timestamp = AudioTimestamp()
    private var lastReportedFrame = 0L

    private val samples = HashMap<Pair<ClickSound, Boolean>, FloatArray>()

    private class Voice(val data: FloatArray, var position: Int, var delay: Int)

    /** Starts a fresh audio stream at frame 0; [conductor] must already be restarted at 0. */
    fun start(conductor: Conductor) {
        stop()
        events.clear()
        framesWritten = 0
        lastReportedFrame = 0
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes, CHUNK_FRAMES * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack
        running = true
        thread = Thread({ renderLoop(audioTrack, conductor) }, "BeatShow-audio").apply { start() }
    }

    fun stop() {
        running = false
        try {
            track?.pause()
        } catch (_: IllegalStateException) {
        }
        thread?.join(1000)
        thread = null
        track = null
        events.clear()
    }

    /** The frame currently being heard, estimated from the audio hardware timestamp. */
    fun playedFrame(): Long {
        val t = track ?: return -1
        val frame = try {
            if (t.getTimestamp(timestamp)) {
                val elapsedNs = System.nanoTime() - timestamp.nanoTime
                timestamp.framePosition + elapsedNs * sampleRate / 1_000_000_000L
            } else {
                t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            }
        } catch (_: IllegalStateException) {
            return lastReportedFrame
        }
        // Never run ahead of the audio actually written, and never step backwards.
        lastReportedFrame = maxOf(lastReportedFrame, minOf(frame, framesWritten))
        return lastReportedFrame
    }

    private fun renderLoop(audioTrack: AudioTrack, conductor: Conductor) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val mix = FloatArray(CHUNK_FRAMES)
        val pcm = ShortArray(CHUNK_FRAMES)
        val pending = ArrayList<ConductorEvent>()
        val voices = ArrayList<Voice>()
        try {
            audioTrack.play()
            while (running) {
                val chunkStart = framesWritten
                pending.clear()
                conductor.advance(chunkStart + CHUNK_FRAMES, pending)
                for (e in pending) {
                    if (e is BeatEvent) {
                        val data = samples.getOrPut(e.sound to e.isDownbeat) {
                            ClickSynth.render(e.sound, e.isDownbeat, sampleRate)
                        }
                        voices += Voice(data, 0, (e.frame - chunkStart).toInt().coerceAtLeast(0))
                    }
                    events.add(e)
                }

                mix.fill(0f)
                val gain = if (clickEnabled) volume else 0f
                val iterator = voices.iterator()
                while (iterator.hasNext()) {
                    val v = iterator.next()
                    var out = v.delay
                    while (out < CHUNK_FRAMES && v.position < v.data.size) {
                        mix[out++] += v.data[v.position++] * gain
                    }
                    v.delay = 0
                    if (v.position >= v.data.size) iterator.remove()
                }
                for (i in 0 until CHUNK_FRAMES) {
                    pcm[i] = (mix[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
                }

                var offset = 0
                while (offset < CHUNK_FRAMES && running) {
                    val n = audioTrack.write(pcm, offset, CHUNK_FRAMES - offset)
                    if (n < 0) {
                        running = false
                        break
                    }
                    offset += n
                }
                framesWritten = chunkStart + CHUNK_FRAMES
            }
        } catch (_: IllegalStateException) {
        } finally {
            try {
                audioTrack.stop()
            } catch (_: IllegalStateException) {
            }
            audioTrack.release()
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        /** 10 ms per chunk; the AudioTrack buffer holds a few of these. */
        const val CHUNK_FRAMES = 480
    }
}
