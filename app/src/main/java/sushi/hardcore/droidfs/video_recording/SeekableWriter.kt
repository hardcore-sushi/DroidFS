package sushi.hardcore.droidfs.video_recording

interface SeekableWriter {
    fun write(buffer: ByteArray, size: Int)
    fun seek(offset: Long)
    fun close()
}