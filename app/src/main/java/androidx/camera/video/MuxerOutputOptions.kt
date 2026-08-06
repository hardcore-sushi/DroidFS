package androidx.camera.video

import android.location.Location

class MuxerOutputOptions(private val mediaMuxer: MediaMuxer): OutputOptions(MuxerOutputOptionsInternal()) {

    private class MuxerOutputOptionsInternal: OutputOptionsInternal() {
        override fun getFileSizeLimit(): Long = FILE_SIZE_UNLIMITED.toLong()

        override fun getDurationLimitMillis(): Long = DURATION_UNLIMITED.toLong()

        override fun getLocation(): Location? = null
    }

    fun getMediaMuxer(): MediaMuxer = mediaMuxer
}