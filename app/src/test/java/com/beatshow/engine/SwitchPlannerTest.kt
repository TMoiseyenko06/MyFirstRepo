package com.beatshow.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SwitchPlannerTest {

    @Test
    fun slideCountAlwaysWithinMinMax() {
        val planner = SwitchPlanner(Random(42))
        for (min in 1..30) for (max in min..30) {
            repeat(50) {
                val n = planner.rollSlideCount(min, max)
                assertTrue("n=$n not in $min..$max", n in min..max)
            }
        }
    }

    @Test
    fun slideCountCoversWholeRange() {
        val planner = SwitchPlanner(Random(7))
        val seen = List(5000) { planner.rollSlideCount(2, 10) }.toSet()
        assertEquals((2..10).toSet(), seen)
    }

    @Test
    fun slideCountReRolledAfterEverySwitch() {
        // Beats per slide equals beats per bar, so every slide change is on a bar boundary
        // and each switch must happen exactly when the current target is reached.
        val conductor = Conductor(config(SlideMode.BEAT_SYNC, beatsPerSlide = 4, beatsPerBar = 4), SwitchPlanner(Random(3)))
        var target = conductor.slideTarget
        val events = mutableListOf<ConductorEvent>()
        conductor.advance(SAMPLE_RATE * 60L * 30, events)
        val switches = events.filterIsInstance<TempoChangeEvent>()
        assertTrue("expected many switches, got ${switches.size}", switches.size > 50)
        for (s in switches) {
            assertEquals(target, s.slidesSinceLastSwitch)
            assertTrue(s.nextSlideTarget in 2..10)
            target = s.nextSlideTarget
        }
        assertTrue("targets should vary", switches.map { it.nextSlideTarget }.toSet().size > 3)
    }

    @Test
    fun switchNeverHappensBeforeTargetOrMidBar() {
        for (bpb in 2..7) for (beatsPerSlide in 1..16) {
            val conductor = Conductor(
                config(SlideMode.BEAT_SYNC, beatsPerSlide = beatsPerSlide, beatsPerBar = bpb, minSlides = 1, maxSlides = 5),
                SwitchPlanner(Random(bpb * 100 + beatsPerSlide)),
            )
            var target = conductor.slideTarget
            val events = mutableListOf<ConductorEvent>()
            conductor.advance(SAMPLE_RATE * 60L * 10, events)
            val slideFrames = events.filterIsInstance<SlideEvent>().map { it.frame }.toSet()
            for (s in events.filterIsInstance<TempoChangeEvent>()) {
                assertTrue(s.slidesSinceLastSwitch >= target)
                assertTrue("switch must coincide with a slide change", s.frame in slideFrames)
                target = s.nextSlideTarget
            }
            // After a switch the new tempo starts on beat 1 of a bar.
            events.forEachIndexed { i, e ->
                if (e is TempoChangeEvent) {
                    val beat = events[i + 1] as BeatEvent
                    assertEquals(e.frame, beat.frame)
                    assertEquals(0, beat.beatInBar)
                }
            }
        }
    }

    @Test
    fun bpmAlwaysInRangeAndAtLeastFiveAway() {
        val planner = SwitchPlanner(Random(99))
        for (min in 40..120 step 7) for (width in 10..120 step 11) {
            val max = min + width
            var current = planner.initialBpm(min, max)
            assertTrue(current in min..max)
            repeat(200) {
                val next = planner.nextBpm(current, min, max)
                assertTrue("next=$next not in $min..$max", next in min..max)
                assertTrue("next=$next too close to $current", abs(next - current) >= 5)
                current = next
            }
        }
    }

    @Test
    fun bpmDefaultsRange() {
        val planner = SwitchPlanner(Random(5))
        for (current in 70..140) repeat(100) {
            val next = planner.nextBpm(current, 70, 140)
            assertTrue(next in 70..140 && abs(next - current) >= 5)
        }
    }
}

const val SAMPLE_RATE = 48_000

fun config(
    mode: SlideMode,
    beatsPerSlide: Int = 4,
    beatsPerBar: Int = 4,
    secondsPerSlide: Float = 3f,
    minSlides: Int = 2,
    maxSlides: Int = 10,
    randomizeTimeSignature: Boolean = false,
) = ConductorConfig(
    sampleRate = SAMPLE_RATE,
    mode = mode,
    beatsPerSlide = beatsPerSlide,
    secondsPerSlide = secondsPerSlide,
    minSlides = minSlides,
    maxSlides = maxSlides,
    minBpm = 70,
    maxBpm = 140,
    beatsPerBar = beatsPerBar,
    sound = ClickSound.WOODBLOCK,
    randomizeSound = false,
    randomizeTimeSignature = randomizeTimeSignature,
)
