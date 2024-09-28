package sushi.hardcore.droidfs

import java.io.File

object FileTypes {
    private val FILE_EXTENSIONS = mapOf(
        Pair("image", listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic")),
        Pair("video", listOf("mp4", "webm", "mkv", "mov", "m4v")),
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