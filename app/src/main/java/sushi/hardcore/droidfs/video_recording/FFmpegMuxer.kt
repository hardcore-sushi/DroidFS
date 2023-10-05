package sushi.hardcore.droidfs.video_recording

import android.media.MediaCodec
import android.media.MediaFormat
import androidx.camera.video.MediaMuxer
import java.nio.ByteBuffer

class FFmpegMuxer(val writer: SeekableWriter): MediaMuxer {
    external fun allocContext(): Long
    external fun addVideoTrack(formatContext: Long, bitrate: Int, frameRate: Int, width: Int, height: Int, orientationHint: Int): Int
    external fun addAudioTrack(formatContext: Long, bitrate: Int, sampleRate: Int, channelCount: Int): Int
    external fun writeHeaders(formatContext: Long): Int
    external fun writePacket(formatContext: Long, buffer: ByteArray, pts: Long, streamIndex: Int, isKeyFrame: Boolean)
    external fun writeTrailer(formatContext: Long)
    external fun release(formatContext: Long)

    var formatContext: Long?

    var orientation = 0
    private var videoTrackIndex: Int? = null
    private var audioTrackIndex: Int? = null
    private var firstPts: Long? = null

    init {
        System.loadLibrary("mux")
        formatContext = allocContext()
    }

    override fun writeSampleData(trackIndex: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val byteArray = ByteArray(bufferInfo.size)
        buffer.get(byteArray)
        if (firstPts == null) {
            firstPts = bufferInfo.presentationTimeUs
        }
        writePacket(
            formatContext!!, byteArray, bufferInfo.presentationTimeUs - firstPts!!, trackIndex,
            bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        )
    }

    override fun addTrack(mediaFormat: MediaFormat): Int {
        val mime = mediaFormat.getString("mime")!!.split('/')
        val bitrate = mediaFormat.getInteger("bitrate")
        return if (mime[0] == "audio") {
            addAudioTrack(
                formatContext!!,
                bitrate,
                mediaFormat.getInteger("sample-rate"),
                mediaFormat.getInteger("channel-count")
            ).also {
                audioTrackIndex = it
            }
        } else {
            addVideoTrack(
                formatContext!!,
                bitrate,
                mediaFormat.getInteger("frame-rate"),
                mediaFormat.getInteger("width"),
                mediaFormat.getInteger("height"),
                orientation
            ).also {
               videoTrackIndex = it
            }
        }
    }

    override fun start() {
        writeHeaders(formatContext!!)
    }
    override fun stop() {
        writeTrailer(formatContext!!)
    }

    override fun setOrientationHint(degree: Int) {
        orientation = degree
    }

    override fun release() {
        writer.close()
        release(formatContext!!)
        firstPts = null
        formatContext = null
    }

    fun writePacket(buff: ByteArray) {
        writer.write(buff, buff.size)
    }
    fun seek(offset: Long) {
        writer.seek(offset)
    }
}