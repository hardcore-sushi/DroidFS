package sushi.hardcore.droidfs.file_viewers

import androidx.media3.common.MimeTypes

object MediaContainers {
    private val mpegTransportStreamExtensions = setOf("mts", "m2ts")

    fun mimeTypeForFileName(fileName: String): String? {
        return if (extension(fileName) in mpegTransportStreamExtensions) {
            MimeTypes.VIDEO_MP2T
        } else {
            null
        }
    }

    fun hasPacketizedTransportStream(fileName: String): Boolean {
        return extension(fileName) in mpegTransportStreamExtensions
    }

    private fun extension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }
}
