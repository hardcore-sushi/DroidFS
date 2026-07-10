package sushi.hardcore.droidfs.file_viewers

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import sushi.hardcore.droidfs.EncryptedFileProvider
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.CrashDiagnostics
import java.io.Closeable

class VlcMediaSource private constructor(
    val parcelFileDescriptor: ParcelFileDescriptor,
    private val closeAction: () -> Unit
) : Closeable {
    val fileDescriptor = parcelFileDescriptor.fileDescriptor

    override fun close() {
        closeAction()
    }

    companion object {
        fun open(context: Context, encryptedVolume: EncryptedVolume, path: String): VlcMediaSource? {
            val size = encryptedVolume.getAttr(path)?.size ?: return null
            CrashDiagnostics.record(context, "VlcMediaSource.open path=$path size=$size sdk=${Build.VERSION.SDK_INT}")
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                openProxy(context, encryptedVolume, path, size)
            } else {
                openExported(context, encryptedVolume, path, size)
            }
        }

        private fun openProxy(
            context: Context,
            encryptedVolume: EncryptedVolume,
            path: String,
            size: Long
        ): VlcMediaSource? {
            val fileHandle = encryptedVolume.openFileReadMode(path)
            if (fileHandle == -1L) {
                CrashDiagnostics.record(context, "VlcMediaSource.openProxy openFileReadMode failed")
                return null
            }
            CrashDiagnostics.record(context, "VlcMediaSource.openProxy fileHandle=$fileHandle")

            val thread = HandlerThread("VlcMediaSource").apply { start() }
            var closed = false
            val callback = object : ProxyFileDescriptorCallback() {
                override fun onGetSize() = size

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                    return encryptedVolume.read(fileHandle, offset, data, 0, size.toLong()).coerceAtLeast(0)
                }

                override fun onRelease() {
                    CrashDiagnostics.record(context, "VlcMediaSource proxy released")
                    if (!closed) {
                        closed = true
                        encryptedVolume.closeFile(fileHandle)
                        thread.quitSafely()
                    }
                }
            }

            val storageManager = context.getSystemService(StorageManager::class.java)
            val pfd = storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                callback,
                Handler(thread.looper)
            )
            return VlcMediaSource(pfd) {
                pfd.close()
                if (!closed) {
                    closed = true
                    encryptedVolume.closeFile(fileHandle)
                    thread.quitSafely()
                }
            }
        }

        private fun openExported(
            context: Context,
            encryptedVolume: EncryptedVolume,
            path: String,
            size: Long
        ): VlcMediaSource? {
            val provider = EncryptedFileProvider(context)
            val exportedFile = provider.createFile(path, size) ?: return null
            CrashDiagnostics.record(context, "VlcMediaSource.openExported exporting whole file")
            if (!provider.exportFile(exportedFile, encryptedVolume)) {
                exportedFile.free()
                CrashDiagnostics.record(context, "VlcMediaSource.openExported export failed")
                return null
            }
            val pfd = exportedFile.open(ParcelFileDescriptor.MODE_READ_ONLY, true)
            return VlcMediaSource(pfd) {
                pfd.close()
            }
        }
    }
}
