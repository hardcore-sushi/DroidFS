package sushi.hardcore.droidfs.video_recording

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

class MediaMuxer(val writer: SeekableWriter) {
    external fun allocContext(): Long
    external fun addVideoTrack(formatContext: Long, bitrate: Int, width: Int, height: Int): Int
    external fun addAudioTrack(formatContext: Long, bitrate: Int, sampleRate: Int, channelCount: Int): Int
    external fun writeHeaders(formatContext: Long): Int
    external fun writePacket(formatContext: Long, buffer: ByteArray, pts: Long, streamIndex: Int, isKeyFrame: Boolean)
    external fun writeTrailer(formatContext: Long)
    external fun release(formatContext: Long)

    companion object {
        const val VIDEO_TRACK_INDEX = 0
        const val AUDIO_TRACK_INDEX = 1
    }

    var formatContext: Long?

    var realVideoTrackIndex: Int? = null
    var audioFrameSize: Int? = null
    var firstPts: Long? = null
    private var audioPts = 0L

    init {
        System.loadLibrary("mux")
        formatContext = allocContext()
    }

    fun writeSampleData(trackIndex: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val byteArray = ByteArray(bufferInfo.size)
        buffer.get(byteArray)
        if (firstPts == null) {
            firstPts = bufferInfo.presentationTimeUs
        }
        if (trackIndex == AUDIO_TRACK_INDEX) {
            writePacket(formatContext!!, byteArray, audioPts, -1, false)
            audioPts += audioFrameSize!!
        } else {
            writePacket(
                formatContext!!, byteArray, bufferInfo.presentationTimeUs - firstPts!!, realVideoTrackIndex!!,
                bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
            )
        }
    }

    fun addTrack(format: MediaFormat): Int {
        val mime = format.getString("mime")!!.split('/')
        val bitrate = format.getInteger("bitrate")
        return if (mime[0] == "audio") {
            audioFrameSize = addAudioTrack(
                formatContext!!,
                bitrate,
                format.getInteger("sample-rate"),
                format.getInteger("channel-count")
            )
            AUDIO_TRACK_INDEX
        } else {
            realVideoTrackIndex = addVideoTrack(
                formatContext!!,
                bitrate,
                format.getInteger("width"),
                format.getInteger("height")
            )
            VIDEO_TRACK_INDEX
        }
    }

    fun start() {
        writeHeaders(formatContext!!)
    }
    fun stop() {
        writeTrailer(formatContext!!)
    }
    fun release() {
        writer.close()
        release(formatContext!!)
        firstPts = null
        audioPts = 0
        formatContext = null
    }

    fun writePacket(buff: ByteArray) {
        writer.write(buff)
    }
    fun seek(offset: Long) {
        writer.seek(offset)
    }
}