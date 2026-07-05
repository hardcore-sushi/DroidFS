package sushi.hardcore.droidfs.file_viewers

import androidx.media3.common.MimeTypes

data class MediaContainer(
    val extensions: Set<String>,
    val mimeType: String?
)

object MediaContainers {
    private val containers = listOf(
        MediaContainer(
            extensions = setOf("ts", "mts", "m2ts"),
            mimeType = MimeTypes.VIDEO_MP2T
        ),
        MediaContainer(
            extensions = setOf("avi"),
            mimeType = "video/x-msvideo"
        )
    )

    fun mimeTypeForFileName(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return containers.firstOrNull { extension in it.extensions }?.mimeType
    }
}
