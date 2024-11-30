package sushi.hardcore.droidfs.util

/**
 * Minimal ring buffer implementation.
 */
class RingBuffer<T>(private val capacity: Int) {
    private val buffer = arrayOfNulls<Any?>(capacity)

    /**
     * Position of the first cell.
     */
    private var head = 0

    /**
     * Position of the next free (or to be overwritten) cell.
     */
    private var tail = 0
    var size = 0
        private set

    fun addLast(e: T) {
        buffer[tail] = e
        tail = (tail + 1) % capacity
        if (size < capacity) {
            size += 1
        } else {
            head = (head + 1) % capacity
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun popFirst() = buffer[head].also {
        head = (head + 1) % capacity
        size -= 1
    } as T

    /**
     * Empty the buffer and call [f] for each element.
     */
    fun drain(f: (T) -> Unit) {
        repeat(size) {
            f(popFirst())
        }
    }
}