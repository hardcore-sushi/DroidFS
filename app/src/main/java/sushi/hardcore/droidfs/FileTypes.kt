package sushi.hardcore.droidfs

import android.content.SharedPreferences
import java.io.File

object FileTypes {
    private const val EXTENSIONS_PREFERENCE_PREFIX = "file_extensions_"
    private val EXTENSION_TYPES = listOf("image", "video", "audio", "pdf", "text")

    private val DEFAULT_FILE_EXTENSIONS = mapOf(
        Pair("image", listOf("png", "jpg", "jpeg", "gif", "avif", "webp", "bmp", "heic")),
        Pair("video", listOf("mp4", "webm", "mkv", "mov", "m4v", "mts", "m2ts", "avi")),
        Pair("audio", listOf("mp3", "ogg", "m4a", "wav", "flac", "opus")),
        Pair("pdf", listOf("pdf")),
        Pair("text", listOf(
            "asc",
            "asm",
            "awk",
            "bash",
            "c",
            "cfg",
            "conf",
            "cpp",
            "css",
            "csv",
            "desktop",
            "dot",
            "g4",
            "go",
            "gradle",
            "h",
            "hpp",
            "hs",
            "html",
            "ini",
            "java",
            "js",
            "json",
            "kt",
            "lisp",
            "log",
            "lua",
            "markdown",
            "md",
            "mod",
            "org",
            "php",
            "pl",
            "pro",
            "properties",
            "py",
            "qml",
            "rb",
            "rc",
            "rs",
            "sh",
            "smali",
            "sql",
            "srt",
            "tex",
            "toml",
            "ts",
            "txt",
            "vala",
            "vim",
            "xml",
            "yaml",
            "yml",
        ))
    )
    private var sharedPrefs: SharedPreferences? = null

    fun init(sharedPrefs: SharedPreferences) {
        this.sharedPrefs = sharedPrefs
    }

    fun getExtensionsPreferenceKey(extensionType: String): String {
        return EXTENSIONS_PREFERENCE_PREFIX+extensionType
    }

    fun getExtensionTypes(): List<String> {
        return EXTENSION_TYPES
    }

    fun getDefaultExtensions(extensionType: String): String {
        return DEFAULT_FILE_EXTENSIONS[extensionType]?.joinToString(", ") ?: ""
    }

    fun normalizeExtensions(extensions: String): String {
        return parseExtensions(extensions).joinToString(", ")
    }

    fun isExtensionType(extensionType: String, path: String): Boolean {
        return getExtensions(extensionType).contains(File(path).extension.lowercase())
    }

    fun getDuplicateExtensions(overrides: Map<String, String> = emptyMap()): Map<String, List<String>> {
        return EXTENSION_TYPES
            .flatMap { extensionType ->
                getExtensions(extensionType, overrides[extensionType]).map { extension ->
                    Pair(extension, extensionType)
                }
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }
            .toSortedMap()
    }

    private fun getExtensions(extensionType: String, override: String? = null): List<String> {
        val defaultExtensions = DEFAULT_FILE_EXTENSIONS[extensionType] ?: return emptyList()
        return override?.let(::parseExtensions)
            ?: sharedPrefs?.getString(getExtensionsPreferenceKey(extensionType), null)?.let(::parseExtensions)
            ?: defaultExtensions
    }

    private fun parseExtensions(extensions: String): List<String> {
        return extensions
            .split(',', ';', ' ', '\n', '\t')
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
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
