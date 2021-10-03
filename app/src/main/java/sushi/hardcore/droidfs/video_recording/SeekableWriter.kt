package sushi.hardcore.droidfs.video_recording

interface SeekableWriter {
    fun write(byteArray: ByteArray)
    fun seek(offset: Long)
    fun close()
}