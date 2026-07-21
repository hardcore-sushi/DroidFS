package androidx.camera.video

import androidx.camera.video.internal.muxer.Muxer

class MuxerOutputOptions(private val muxer: Muxer): OutputOptions(MuxerOutputOptionsInternal()) {

    private class MuxerOutputOptionsInternal: OutputOptionsInternal() {
        override fun getFileSizeLimit(): Long = FILE_SIZE_UNLIMITED.toLong()

        override fun getDurationLimitMillis(): Long = DURATION_UNLIMITED.toLong()

        override fun getLocation(): android.location.Location? = null
    }

    fun getMuxer(): Muxer = muxer
}
