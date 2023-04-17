package sushi.hardcore.droidfs.video_recording

interface SeekableWriter {
    fun write(buffer: ByteArray)
    fun seek(offset: Long)
    fun close()
}