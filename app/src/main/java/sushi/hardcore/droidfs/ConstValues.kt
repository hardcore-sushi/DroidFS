package sushi.hardcore.droidfs

import java.io.File

class ConstValues {
    companion object {
        const val creator = "DroidFS"
        const val saved_volumes_key = "saved_volumes"
        const val sort_order_key = "sort_order"
        const val wipe_passes = 2
        const val seek_bar_inc = 200
        private val fileExtensions = mapOf(
            Pair("image", listOf("png", "jpg", "jpeg")),
            Pair("video", listOf("mp4", "webm")),
            Pair("audio", listOf("mp3", "ogg")),
            Pair("text", listOf("txt", "json", "conf", "xml", "java", "kt", "py", "go", "c", "h", "cpp", "hpp", "sh", "js", "html", "css", "php"))
        )

        fun isImage(path: String): Boolean {
            return fileExtensions["image"]?.contains(File(path).extension) ?: false
        }
        fun isVideo(path: String): Boolean {
            return fileExtensions["video"]?.contains(File(path).extension) ?: false
        }
        fun isAudio(path: String): Boolean {
            return fileExtensions["audio"]?.contains(File(path).extension) ?: false
        }
        fun isText(path: String): Boolean {
            return fileExtensions["text"]?.contains(File(path).extension) ?: false
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