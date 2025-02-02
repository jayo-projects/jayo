package jayo.internal

import jayo.tools.BasicFifoQueue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BasicFifoQueueTest {
    @Test
    fun testOffer() {
        val queue = BasicFifoQueue.create<Int>()
        assertThat(queue.offer(1)).isTrue
        assertThat(queue.offer(2)).isFalse
        assertThat(queue.offer(3)).isFalse
    }

    @Test
    fun testIterator() {
        val queue = BasicFifoQueue.create<Int>()
        queue.offer(1)
        queue.offer(2)
        queue.offer(3)

        val iterator = queue.iterator()
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(1)
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(2)
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(3)
        assertThat(iterator.hasNext()).isFalse
    }

    @Test
    fun testIteratorRemove() {
        val queue = BasicFifoQueue.create<Int>()
        queue.offer(1)
        queue.offer(2)
        queue.offer(3)

        var iterator = queue.iterator()
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(1)
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(2)
        iterator.remove()
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(3)
        iterator.remove()
        assertThat(iterator.hasNext()).isFalse

        iterator = queue.iterator()
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo(1)
        assertThat(iterator.hasNext()).isFalse
    }

    @Test
    fun testPoll() {
        val queue = BasicFifoQueue.create<String>()
        queue.offer("1")
        queue.offer("2")

        assertThat(queue.poll()).isEqualTo("1")
        assertThat(queue.poll()).isEqualTo("2")
        assertThat(queue.poll()).isNull()
    }

    @Test
    fun testRemove() {
        val queue = BasicFifoQueue.create<Int>()
        queue.offer(1)
        queue.offer(2)
        queue.offer(3)
        queue.offer(4)

        assertThat(queue.remove(42)).isFalse
        assertThat(queue.remove(1)).isTrue
        assertThat(queue.remove(1)).isFalse
        assertThat(queue.remove(4)).isTrue
        assertThat(queue.remove(3)).isTrue

        var iterator = queue.iterator()
        assertThat(iterator.next()).isEqualTo(2)
        assertThat(iterator.hasNext()).isFalse
    }

    @Test
    fun testIsEmpty() {
        val queue = BasicFifoQueue.create<Int>()
        assertThat(queue.isEmpty()).isTrue

        queue.offer(1)
        assertThat(queue.isEmpty()).isFalse

        queue.remove(1)
        assertThat(queue.isEmpty()).isTrue
    }

    @Test
    fun testPeek() {
        val queue = BasicFifoQueue.create<String>()
        queue.offer("1")
        queue.offer("2")
        queue.offer("3")

        assertThat(queue.peek()).isEqualTo("1")
        queue.poll()
        assertThat(queue.peek()).isEqualTo("2")
        queue.poll()
        assertThat(queue.peek()).isEqualTo("3")
        queue.poll()
        assertThat(queue.peek()).isNull()
    }

    @Test
    fun testContains() {
        val queue = BasicFifoQueue.create<Int>()
        queue.offer(1)

        assertThat(queue.contains(1)).isTrue
        assertThat(queue.contains(42)).isFalse
    }

    @Test
    fun testRemovePollOffer() {
        val queue = BasicFifoQueue.create<Int>()
        queue.offer(1)
        queue.offer(2)
        queue.offer(3)

        assertThat(queue.remove(2)).isTrue
        assertThat(queue.offer(4)).isFalse
        assertThat(queue.peek()).isEqualTo(1)
        assertThat(queue.poll()).isEqualTo(1)
        assertThat(queue.peek()).isEqualTo(3)
        assertThat(queue.poll()).isEqualTo(3)
        assertThat(queue.offer(5)).isFalse
        assertThat(queue.remove(4)).isTrue
        assertThat(queue.poll()).isEqualTo(5)
        assertThat(queue.isEmpty()).isTrue
        assertThat(queue.offer(6)).isTrue
    }

    @Test
    fun testIteratorRemoveThenPeekThenOffer() {
        val queue = BasicFifoQueue.create<String>()
        queue.offer("1")

        var iterator = queue.iterator()
        assertThat(iterator.hasNext()).isTrue
        assertThat(iterator.next()).isEqualTo("1")
        iterator.remove()
        assertThat(iterator.hasNext()).isFalse

        assertThat(queue.peek()).isNull()
        assertThat(queue.offer("1")).isTrue
    }
}