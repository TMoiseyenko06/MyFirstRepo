package com.beatshow.engine

import kotlin.math.abs
import kotlin.random.Random

/** Random choices made at each tempo switch. */
class SwitchPlanner(private val random: Random = Random.Default) {

    /** Number of slides to show before the next switch, within [min]..[max]. */
    fun rollSlideCount(min: Int, max: Int): Int {
        val lo = minOf(min, max)
        val hi = maxOf(min, max)
        return lo + random.nextInt(hi - lo + 1)
    }

    fun initialBpm(min: Int, max: Int): Int {
        val lo = minOf(min, max)
        val hi = maxOf(min, max)
        return lo + random.nextInt(hi - lo + 1)
    }

    /**
     * A tempo in [min]..[max] that differs from [current] by at least [MIN_BPM_CHANGE].
     * If the range is too narrow for that, the endpoint farthest from [current] is used.
     */
    fun nextBpm(current: Int, min: Int, max: Int): Int {
        val lo = minOf(min, max)
        val hi = maxOf(min, max)
        // Candidates are [lo, current-5] and [current+5, hi]; pick uniformly across both.
        val belowHi = minOf(hi, current - MIN_BPM_CHANGE)
        val aboveLo = maxOf(lo, current + MIN_BPM_CHANGE)
        val belowCount = if (belowHi >= lo) belowHi - lo + 1 else 0
        val aboveCount = if (hi >= aboveLo) hi - aboveLo + 1 else 0
        val total = belowCount + aboveCount
        if (total == 0) return if (abs(current - lo) >= abs(hi - current)) lo else hi
        val pick = random.nextInt(total)
        return if (pick < belowCount) lo + pick else aboveLo + (pick - belowCount)
    }

    fun <T> pick(options: List<T>): T = options[random.nextInt(options.size)]

    companion object {
        const val MIN_BPM_CHANGE = 5
    }
}
