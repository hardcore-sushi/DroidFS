package sushi.hardcore.droidfs

import android.net.Uri
import java.io.File
import java.util.*

class ConstValues {
    companion object {
        const val creator = "DroidFS"
        const val gocryptfsConfFilename = "gocryptfs.conf"
        const val volumeDatabaseName = "SavedVolumes"
        const val sort_order_key = "sort_order"
        val fakeUri: Uri = Uri.parse("fakeuri://droidfs")
        const val MAX_KERNEL_WRITE = 128*1024
        const val wipe_passes = 2
        const val slideshow_delay: Long = 4000
        private val fileExtensions = mapOf(
            Pair("image", listOf("png", "jpg", "jpeg", "gif", "bmp")),
            Pair("video", listOf("mp4", "webm", "mkv", "mov")),
            Pair("audio", listOf("mp3", "ogg", "m4a", "wav", "flac")),
            Pair("text", listOf("txt", "json", "conf", "log", "xml", "java", "kt", "py", "pl", "rb", "go", "c", "h", "cpp", "hpp", "sh", "bat", "js", "html", "css", "php", "yml", "yaml", "ini", "md"))
        )

        fun isExtensionType(extensionType: String, path: String): Boolean {
            return fileExtensions[extensionType]?.contains(File(path).extension.toLowerCase(Locale.ROOT)) ?: false
        }

        fun isImage(path: String): Boolean {
            return isExtensionType("image", path)
        }
        fun isVideo(path: String): Boolean {
            return isExtensionType("video", path)
        }
        fun isAudio(path: String): Boolean {
            return isExtensionType("audio", path)
        }
        fun isText(path: String): Boolean {
            return isExtensionType("text", path)
        }
        fun getAssociatedDrawable(path: String): Int {
            return when {
                isAudio(path) -> R.drawable.icon_file_audio
                isImage(path) -> R.drawable.icon_file_image
                isVideo(path) -> R.drawable.icon_file_video
                isText(path) -> R.drawable.icon_file_text
                else -> R.drawable.icon_file_unknown
            }
        }
    }
}