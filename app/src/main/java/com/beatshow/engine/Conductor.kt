package com.beatshow.engine

import kotlin.math.roundToLong

enum class ClickSound(val label: String) {
    WOODBLOCK("Woodblock"),
    BEEP("Beep"),
    HIHAT("Hi-hat"),
    COWBELL("Cowbell"),
}

enum class SlideMode { BEAT_SYNC, SECONDS }

data class ConductorConfig(
    val sampleRate: Int,
    val mode: SlideMode,
    val beatsPerSlide: Int,
    val secondsPerSlide: Float,
    val minSlides: Int,
    val maxSlides: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val beatsPerBar: Int,
    val sound: ClickSound,
    val randomizeSound: Boolean,
    val randomizeTimeSignature: Boolean,
)

sealed interface ConductorEvent {
    /** Position on the audio timeline, in sample frames. */
    val frame: Long
}

data class BeatEvent(
    override val frame: Long,
    val beatInBar: Int,
    val beatsPerBar: Int,
    val bpm: Int,
    val sound: ClickSound,
) : ConductorEvent {
    val isDownbeat: Boolean get() = beatInBar == 0
}

data class SlideEvent(override val frame: Long) : ConductorEvent

data class TempoChangeEvent(
    override val frame: Long,
    val bpm: Int,
    val beatsPerBar: Int,
    val sound: ClickSound,
    /** Slides shown between the previous switch and this one. */
    val slidesSinceLastSwitch: Int,
    /** Freshly rolled number of slides until the next switch. */
    val nextSlideTarget: Int,
) : ConductorEvent

/**
 * Sample-accurate schedule of beats, slide changes and tempo switches.
 *
 * Everything is computed in sample frames of the audio stream, so the click audio and the
 * slideshow visuals are driven by one clock. Beat k of a tempo segment lands exactly at
 * `segmentStart + round(k * framesPerBeat)`, which avoids accumulating rounding drift.
 */
class Conductor(
    private val config: ConductorConfig,
    private val planner: SwitchPlanner = SwitchPlanner(),
) {
    var bpm: Int = planner.initialBpm(config.minBpm, config.maxBpm)
        private set
    var beatsPerBar: Int = config.beatsPerBar
        private set
    var sound: ClickSound = config.sound
        private set
    var slideTarget: Int = planner.rollSlideCount(config.minSlides, config.maxSlides)
        private set
    var slidesSinceSwitch: Int = 0
        private set

    private var segmentStart = 0L
    private var beatInSegment = 0L
    private var pendingSwitch = false

    private val framesPerSlide = (config.secondsPerSlide.toDouble() * config.sampleRate)
    private var slideStart = 0L
    private var slideIndex = 1L

    private val framesPerBeat: Double get() = config.sampleRate * 60.0 / bpm

    init {
        restart(0L)
    }

    /** Starts a new bar at [startFrame], e.g. after resuming on a fresh audio stream. */
    fun restart(startFrame: Long) {
        segmentStart = startFrame
        beatInSegment = 0
        slideStart = startFrame
        slideIndex = 1
    }

    fun beatFrame(beatIndex: Long): Long = segmentStart + (beatIndex * framesPerBeat).roundToLong()

    private fun nextSecondsSlideFrame(): Long = slideStart + (slideIndex * framesPerSlide).roundToLong()

    /** Appends every event with a frame before [untilFrame] to [out], in order. */
    fun advance(untilFrame: Long, out: MutableList<ConductorEvent>) {
        while (true) {
            val beat = beatFrame(beatInSegment)
            val slide = if (config.mode == SlideMode.SECONDS) nextSecondsSlideFrame() else Long.MAX_VALUE
            if (minOf(beat, slide) >= untilFrame) return
            if (slide <= beat) secondsSlide(slide, out) else beat(beat, out)
        }
    }

    private fun secondsSlide(frame: Long, out: MutableList<ConductorEvent>) {
        out += SlideEvent(frame)
        slideIndex++
        slidesSinceSwitch++
        if (slidesSinceSwitch >= slideTarget) pendingSwitch = true
    }

    private fun beat(frame: Long, out: MutableList<ConductorEvent>) {
        val k = beatInSegment
        val downbeat = k % beatsPerBar == 0L
        when (config.mode) {
            SlideMode.BEAT_SYNC -> if (k > 0 && k % config.beatsPerSlide == 0L) {
                out += SlideEvent(frame)
                slidesSinceSwitch++
                if (slidesSinceSwitch >= slideTarget && downbeat) {
                    switchTempo(frame, out)
                    return
                }
            }
            // Slides aren't beat-aligned here, so the switch waits for the next downbeat.
            SlideMode.SECONDS -> if (k > 0 && pendingSwitch && downbeat) {
                switchTempo(frame, out)
                return
            }
        }
        out += BeatEvent(frame, (k % beatsPerBar).toInt(), beatsPerBar, bpm, sound)
        beatInSegment++
    }

    private fun switchTempo(frame: Long, out: MutableList<ConductorEvent>) {
        val slidesShown = slidesSinceSwitch
        bpm = planner.nextBpm(bpm, config.minBpm, config.maxBpm)
        beatsPerBar = if (config.randomizeTimeSignature) {
            planner.pick((MIN_BEATS_PER_BAR..MAX_BEATS_PER_BAR).toList())
        } else {
            config.beatsPerBar
        }
        sound = if (config.randomizeSound) planner.pick(ClickSound.entries) else config.sound
        slideTarget = planner.rollSlideCount(config.minSlides, config.maxSlides)
        slidesSinceSwitch = 0
        pendingSwitch = false
        segmentStart = frame
        beatInSegment = 0
        out += TempoChangeEvent(frame, bpm, beatsPerBar, sound, slidesShown, slideTarget)
        out += BeatEvent(frame, 0, beatsPerBar, bpm, sound)
        beatInSegment = 1
    }

    companion object {
        const val MIN_BEATS_PER_BAR = 2
        const val MAX_BEATS_PER_BAR = 7
    }
}
