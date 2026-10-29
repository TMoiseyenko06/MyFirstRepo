package com.beatshow.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToLong
import kotlin.random.Random

class BeatSyncTest {

    @Test
    fun slideBoundariesFallOnExactBeatSamplePositions() {
        for (beatsPerSlide in 1..16) {
            val conductor = Conductor(
                config(SlideMode.BEAT_SYNC, beatsPerSlide = beatsPerSlide, randomizeTimeSignature = true),
                SwitchPlanner(Random(beatsPerSlide)),
            )
            val events = mutableListOf<ConductorEvent>()
            // Feed the conductor in audio-buffer-sized chunks, like the audio thread does.
            var frame = 0L
            while (frame < SAMPLE_RATE * 60L * 5) {
                frame += 960
                conductor.advance(frame, events)
            }
            val beatFrames = events.filterIsInstance<BeatEvent>().map { it.frame }.toSet()
            val slides = events.filterIsInstance<SlideEvent>()
            assertTrue(slides.isNotEmpty())
            for (s in slides) assertTrue("slide at ${s.frame} is not on a beat", s.frame in beatFrames)
        }
    }

    @Test
    fun beatsMatchIdealGridWithoutDrift() {
        val conductor = Conductor(config(SlideMode.BEAT_SYNC, beatsPerSlide = 3), SwitchPlanner(Random(11)))
        val events = mutableListOf<ConductorEvent>()
        var frame = 0L
        while (frame < SAMPLE_RATE * 60L * 10) {
            frame += 777
            conductor.advance(frame, events)
        }
        var segmentStart = 0L
        var bpm = events.filterIsInstance<BeatEvent>().first().bpm
        var k = 0L
        for (e in events) {
            when (e) {
                is TempoChangeEvent -> {
                    segmentStart = e.frame; bpm = e.bpm; k = 0
                }
                is BeatEvent -> {
                    val expected = segmentStart + (k * SAMPLE_RATE * 60.0 / bpm).roundToLong()
                    assertEquals(expected, e.frame)
                    k++
                }
                is SlideEvent -> {
                    // A slide lands on the beat about to be emitted, which is a multiple of 3.
                    assertEquals(0L, k % 3)
                }
            }
        }
    }

    @Test
    fun secondsModeSlidesUseFixedInterval() {
        val conductor = Conductor(config(SlideMode.SECONDS, secondsPerSlide = 2.5f), SwitchPlanner(Random(2)))
        val events = mutableListOf<ConductorEvent>()
        conductor.advance(SAMPLE_RATE * 60L, events)
        val slides = events.filterIsInstance<SlideEvent>().map { it.frame }
        slides.forEachIndexed { i, f -> assertEquals(((i + 1) * 2.5 * SAMPLE_RATE).roundToLong(), f) }
        // Tempo switches still only happen on downbeats.
        events.forEachIndexed { i, e ->
            if (e is TempoChangeEvent) assertEquals(0, (events[i + 1] as BeatEvent).beatInBar)
        }
    }
}
