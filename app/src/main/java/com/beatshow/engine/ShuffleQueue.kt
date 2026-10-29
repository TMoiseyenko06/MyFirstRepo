package com.beatshow.engine

import kotlin.random.Random

/**
 * Endless shuffled sequence. Every item is returned once per round (Fisher-Yates),
 * and a new round never starts with the item that ended the previous one.
 */
class ShuffleQueue<T>(items: List<T>, private val random: Random = Random.Default) {
    private val items: List<T> = items.toList()
    private val order = ArrayList<T>(items.size)
    private var index = 0
    private var last: T? = null

    init {
        require(this.items.isNotEmpty()) { "ShuffleQueue needs at least one item" }
        reshuffle()
    }

    fun next(): T {
        if (index >= order.size) reshuffle()
        val item = order[index++]
        last = item
        return item
    }

    private fun reshuffle() {
        order.clear()
        order.addAll(items)
        fisherYates(order, random)
        val previous = last
        if (order.size > 1 && previous != null && order[0] == previous) {
            // Swap the repeat with any other position so the boundary has no repeat.
            val swapWith = 1 + random.nextInt(order.size - 1)
            order[0] = order[swapWith].also { order[swapWith] = order[0] }
        }
        index = 0
    }

    companion object {
        fun <T> fisherYates(list: MutableList<T>, random: Random) {
            for (i in list.size - 1 downTo 1) {
                val j = random.nextInt(i + 1)
                list[i] = list[j].also { list[j] = list[i] }
            }
        }
    }
}

/** Shuffled playback with back/forward history on top of a [ShuffleQueue]. */
class SlideNavigator<T>(private val queue: ShuffleQueue<T>) {
    private val history = ArrayList<T>()
    private var cursor = -1

    val current: T? get() = history.getOrNull(cursor)

    fun next(): T {
        cursor++
        if (cursor >= history.size) history.add(queue.next())
        trimHistory()
        return history[cursor]
    }

    /** The photo [next] will return, drawn ahead so it can be preloaded. */
    fun peekNext(): T {
        if (cursor + 1 >= history.size) history.add(queue.next())
        return history[cursor + 1]
    }

    fun previous(): T? {
        if (cursor > 0) cursor--
        return current
    }

    private fun trimHistory() {
        val excess = cursor - MAX_HISTORY
        if (excess > 0) {
            repeat(excess) { history.removeAt(0) }
            cursor -= excess
        }
    }

    private companion object {
        const val MAX_HISTORY = 200
    }
}
