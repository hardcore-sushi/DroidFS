package sushi.hardcore.droidfs

import android.net.Uri
import java.io.File

object ConstValues {
    const val VOLUME_DATABASE_NAME = "SavedVolumes"
    const val CRYFS_LOCAL_STATE_DIR = "cryfsLocalState"
    const val SORT_ORDER_KEY = "sort_order"
    val FAKE_URI: Uri = Uri.parse("fakeuri://droidfs")
    const val WIPE_PASSES = 2
    const val IO_BUFF_SIZE = 16384
    const val SLIDESHOW_DELAY: Long = 4000
    const val DEFAULT_THEME_VALUE = "dark_green"
    const val THEME_VALUE_KEY = "theme"
    const val DEFAULT_VOLUME_KEY = "default_volume"
    const val REMEMBER_VOLUME_KEY = "remember_volume"
    const val THUMBNAIL_MAX_SIZE_KEY = "thumbnail_max_size"
    const val DEFAULT_THUMBNAIL_MAX_SIZE = 10_000L
    const val PIN_PASSWORDS_KEY = "pin_passwords"
    private val FILE_EXTENSIONS = mapOf(
        Pair("image", listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic")),
        Pair("video", listOf("mp4", "webm", "mkv", "mov")),
        Pair("audio", listOf("mp3", "ogg", "m4a", "wav", "flac")),
        Pair("pdf", listOf("pdf")),
        Pair("text", listOf("txt", "json", "conf", "log", "xml", "java", "kt", "py", "pl", "rb", "go", "c", "h", "cpp", "hpp", "rs", "sh", "bat", "js", "html", "css", "php", "yml", "yaml", "toml", "ini", "md", "properties"))
    )

    fun isExtensionType(extensionType: String, path: String): Boolean {
        return FILE_EXTENSIONS[extensionType]?.contains(File(path).extension.lowercase()) ?: false
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
    fun isPDF(path: String): Boolean {
        return isExtensionType("pdf", path)
    }
    fun isText(path: String): Boolean {
        return isExtensionType("text", path)
    }
}