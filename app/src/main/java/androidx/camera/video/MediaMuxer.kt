package androidx.camera.video

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

interface MediaMuxer {
    fun setOrientationHint(degree: Int)
    fun release()
    fun addTrack(mediaFormat: MediaFormat): Int
    fun start()
    fun writeSampleData(trackIndex: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
    fun stop()
}