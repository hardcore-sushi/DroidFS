package sushi.hardcore.droidfs

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.system.Os
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.Compat
import sushi.hardcore.droidfs.util.Wiper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class EncryptedFileProvider(context: Context) {
    companion object {
        private const val TAG = "EncryptedFileProvider"
        fun getTmpFilesDir(context: Context) = File(context.cacheDir, "tmp")

        var exportMethod = ExportMethod.AUTO
    }

    enum class ExportMethod {
        AUTO,
        DISK,
        MEMORY;

        companion object {
            fun parse(value: String) = when (value) {
                "auto" -> EncryptedFileProvider.ExportMethod.AUTO
                "disk" -> EncryptedFileProvider.ExportMethod.DISK
                "memory" -> EncryptedFileProvider.ExportMethod.MEMORY
                else -> throw IllegalArgumentException("Invalid export method: $value")
            }
        }
    }

    private val memoryInfo = ActivityManager.MemoryInfo()
    private val isMemFileSupported = Compat.isMemFileSupported()
    private val tmpFilesDir by lazy { getTmpFilesDir(context) }
    private val handler by lazy { Handler(context.mainLooper) }

    init {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(
            memoryInfo
        )

        PreferenceManager.getDefaultSharedPreferences(context)
            .getString("export_method", null)?.let {
                exportMethod = ExportMethod.parse(it)
            }
    }

    class ExportedDiskFile private constructor(
        path: String,
        private val file: File,
        private val handler: Handler
    ) : ExportedFile(path) {
        companion object {
            fun create(path: String, tmpFilesDir: File, handler: Handler): ExportedDiskFile? {
                val uuid = UUID.randomUUID().toString()
                val file = File(tmpFilesDir, uuid)
                return if (file.createNewFile()) {
                    ExportedDiskFile(path, file, handler)
                } else {
                    null
                }
            }
        }

        override fun open(mode: Int, furtive: Boolean): ParcelFileDescriptor {
            return if (furtive) {
                ParcelFileDescriptor.open(file, mode, handler) {
                    free()
                }
            } else {
                ParcelFileDescriptor.open(file, mode)
            }
        }

        override fun free() {
            GlobalScope.launch(Dispatchers.IO) {
                Wiper.wipe(file)
            }
        }
    }

    class ExportedMemFile private constructor(path: String, private val file: MemFile) :
        ExportedFile(path) {
        companion object {
            fun create(path: String, size: Long): ExportedMemFile? {
                val uuid = UUID.randomUUID().toString()
                MemFile.create(uuid, size)?.let {
                    return ExportedMemFile(path, it)
                }
                return null
            }
        }

        override fun open(mode: Int, furtive: Boolean): ParcelFileDescriptor {
            val fd = if (furtive) {
                file.toParcelFileDescriptor()
            } else {
                file.dup()
            }
            if (mode and ParcelFileDescriptor.MODE_TRUNCATE != 0) {
                Os.ftruncate(fd.fileDescriptor, 0)
            } else {
                FileInputStream(fd.fileDescriptor).apply {
                    channel.position(0)
                    close()
                }
            }
            return fd
        }

        override fun free() = file.close()
    }

    abstract class ExportedFile(val path: String) {
        var isValid = true
            private set

        fun invalidate() {
            isValid = false
        }

        /**
         * @param furtive If set to true, the file will be deleted when closed
         */
        abstract fun open(mode: Int, furtive: Boolean): ParcelFileDescriptor
        abstract fun free()
    }

    fun createFile(
        path: String,
        size: Long,
    ): ExportedFile? {
        val diskFile by lazy { ExportedDiskFile.create(path, tmpFilesDir, handler) }
        val memFile by lazy { ExportedMemFile.create(path, size) }
        return when (exportMethod) {
            ExportMethod.MEMORY -> memFile
            ExportMethod.DISK -> diskFile
            ExportMethod.AUTO -> {
                if (isMemFileSupported && size < memoryInfo.availMem * 0.8) {
                    memFile
                } else {
                    diskFile
                }
            }
        }
    }

    fun exportFile(
        exportedFile: ExportedFile,
        encryptedVolume: EncryptedVolume,
    ): Boolean {
        val pfd = exportedFile.open(ParcelFileDescriptor.MODE_WRITE_ONLY, false)
        return encryptedVolume.exportFile(exportedFile.path, FileOutputStream(pfd.fileDescriptor)).also {
            pfd.close()
        }
    }

    enum class Error {
        SUCCESS,
        INVALID_STATE,
        WRITE_ACCESS_DENIED,
        UNSUPPORTED_APPEND,
        UNSUPPORTED_RW,
        ;

        fun log() {
            Log.e(
                TAG, when (this) {
                    SUCCESS -> "No error"
                    INVALID_STATE -> "Read after write is not supported"
                    WRITE_ACCESS_DENIED -> "Write access unauthorized"
                    UNSUPPORTED_APPEND -> "Appending is not supported"
                    UNSUPPORTED_RW -> "Read-write access requires Android 11 or later"
                }
            )
        }
    }

    /**
     * @param furtive If set to true, the file will be deleted when closed
     */
    fun openFile(
        file: ExportedFile,
        mode: String,
        encryptedVolume: EncryptedVolume,
        volumeScope: CoroutineScope,
        furtive: Boolean,
        allowWrites: Boolean,
    ): Pair<ParcelFileDescriptor?, Error> {
        val mode = ParcelFileDescriptor.parseMode(mode)
        return if (mode and ParcelFileDescriptor.MODE_READ_ONLY != 0) {
            if (!file.isValid) return Pair(null, Error.INVALID_STATE)
            Pair(file.open(mode, furtive), Error.SUCCESS)
        } else {
            if (!allowWrites) {
                return Pair(null, Error.WRITE_ACCESS_DENIED)
            }

            fun import(input: InputStream): Boolean {
                return if (encryptedVolume.importFile(input, file.path)) {
                    true
                } else {
                    Log.e(TAG, "Failed to import file")
                    false
                }
            }

            if (mode and ParcelFileDescriptor.MODE_WRITE_ONLY != 0) {
                if (mode and ParcelFileDescriptor.MODE_APPEND != 0) {
                    return Pair(null, Error.UNSUPPORTED_APPEND)
                }
                if (mode and ParcelFileDescriptor.MODE_TRUNCATE == 0) {
                    Log.w(TAG, "Truncating file despite not being requested")
                }
                val pipe = ParcelFileDescriptor.createReliablePipe()
                val input = FileInputStream(pipe[0].fileDescriptor)
                volumeScope.launch {
                    if (import(input)) {
                        file.invalidate()
                    }
                }
                Pair(pipe[1], Error.SUCCESS)
            } else { // read-write
                if (!file.isValid) return Pair(null, Error.INVALID_STATE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val fd = file.open(mode, false)
                    Pair(ParcelFileDescriptor.wrap(fd, handler) { e ->
                        if (e == null) {
                            import(FileInputStream(fd.fileDescriptor))
                            if (furtive) {
                                file.free()
                            }
                        }
                    }, Error.SUCCESS)
                } else {
                    Pair(null, Error.UNSUPPORTED_RW)
                }
            }
        }
    }
}
