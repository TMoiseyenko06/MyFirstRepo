package com.beatshow.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.random.Random

class ShuffleQueueTest {

    @Test
    fun everyPhotoShownOnceBeforeAnyRepeat() {
        for (size in 1..12) {
            for (seed in 0 until 50) {
                val items = (0 until size).toList()
                val queue = ShuffleQueue(items, Random(seed))
                repeat(20) { round ->
                    val draws = List(size) { queue.next() }
                    assertEquals("size=$size seed=$seed round=$round", items.toSet(), draws.toSet())
                }
            }
        }
    }

    @Test
    fun noRepeatAcrossShuffleBoundary() {
        for (size in 2..12) {
            for (seed in 0 until 200) {
                val queue = ShuffleQueue((0 until size).toList(), Random(seed))
                val draws = List(size * 30) { queue.next() }
                for (i in 1 until draws.size) {
                    assertNotEquals("size=$size seed=$seed i=$i", draws[i - 1], draws[i])
                }
            }
        }
    }

    @Test
    fun navigatorGoesBackAndForwardThroughHistory() {
        val nav = SlideNavigator(ShuffleQueue(listOf("a", "b", "c", "d"), Random(1)))
        val first = nav.next()
        val upcoming = nav.peekNext()
        val second = nav.next()
        assertEquals(upcoming, second)
        assertEquals(first, nav.previous())
        assertEquals(second, nav.next())
    }
}
