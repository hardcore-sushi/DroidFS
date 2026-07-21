package sushi.hardcore.droidfs.video_recording

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.ParcelFileDescriptor
import androidx.camera.video.internal.muxer.Muxer
import androidx.camera.video.internal.muxer.MuxerException
import java.nio.ByteBuffer

class FFmpegMuxer(val writer: SeekableWriter): Muxer {
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

    override fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val byteArray = ByteArray(bufferInfo.size)
        byteBuffer.get(byteArray)
        if (firstPts == null) {
            firstPts = bufferInfo.presentationTimeUs
        }
        writePacket(
            formatContext!!, byteArray, bufferInfo.presentationTimeUs - firstPts!!, trackIndex,
            bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        )
    }

    override fun addTrack(format: MediaFormat): Int {
        val mime = format.getString("mime")!!.split('/')
        val bitrate = format.getInteger("bitrate")
        return if (mime[0] == "audio") {
            addAudioTrack(
                formatContext!!,
                bitrate,
                format.getInteger("sample-rate"),
                format.getInteger("channel-count")
            ).also {
                audioTrackIndex = it
            }
        } else {
            addVideoTrack(
                formatContext!!,
                bitrate,
                format.getInteger("frame-rate"),
                format.getInteger("width"),
                format.getInteger("height"),
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

    override fun setOrientationDegrees(degrees: Int) {
        orientation = degrees
    }

    override fun setLocation(latitude: Double, longitude: Double) {}
    override fun setCaptureFps(captureFps: Int) {}
    override fun setOutput(path: String, format: Int) {}
    override fun setOutput(parcelFileDescriptor: ParcelFileDescriptor, format: Int) {}

    override fun isInterruptionResilient(): Boolean = false

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