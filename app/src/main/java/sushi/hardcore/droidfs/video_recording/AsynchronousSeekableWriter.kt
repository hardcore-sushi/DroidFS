package sushi.hardcore.droidfs.video_recording

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.Constants
import java.nio.ByteBuffer

class AsynchronousSeekableWriter(private val internalWriter: SeekableWriter): SeekableWriter {

    internal enum class Operation { WRITE, SEEK, CLOSE }

    internal class Task(
        val operation: Operation,
        val buffer: ByteArray? = null,
        val offset: Long? = null,
    )

    private val channel = Channel<Task>(Channel.UNLIMITED)

    private fun flush(buffer: ByteBuffer) {
        internalWriter.write(buffer.array(), buffer.position())
        buffer.position(0)
    }

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteBuffer.allocate(Constants.IO_BUFF_SIZE)
            while (true) {
                val task = channel.receive()
                when (task.operation) {
                    Operation.WRITE -> {
                        if (task.buffer!!.size > buffer.remaining()) {
                            flush(buffer)
                        }
                        buffer.put(task.buffer)
                    }
                    Operation.SEEK -> {
                        if (buffer.position() > 0) {
                            flush(buffer)
                        }
                        internalWriter.seek(task.offset!!)
                    }
                    Operation.CLOSE -> {
                        if (buffer.position() > 0) {
                            flush(buffer)
                        }
                        internalWriter.close()
                        break
                    }
                }
            }
        }
    }

    override fun write(buffer: ByteArray, size: Int) {
        channel.trySend(Task(Operation.WRITE, buffer)).exceptionOrNull()?.let { throw it }
    }

    override fun seek(offset: Long) {
        channel.trySend(Task(Operation.SEEK, offset = offset)).exceptionOrNull()?.let { throw it }
    }

    override fun close() {
        channel.trySend(Task(Operation.CLOSE)).exceptionOrNull()?.let { throw it }
    }
}