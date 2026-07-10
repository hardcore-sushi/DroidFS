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
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class VlcMediaSource private constructor(
    val parcelFileDescriptor: ParcelFileDescriptor,
    private val closeAction: () -> Unit
) : Closeable {
    val fileDescriptor = parcelFileDescriptor.fileDescriptor
    private val closed = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closeAction()
        }
    }

    companion object {
        fun open(
            context: Context,
            encryptedVolume: EncryptedVolume,
            path: String,
            allowUnsafeExport: Boolean = false
        ): VlcMediaSource? {
            val size = encryptedVolume.getAttr(path)?.size ?: return null
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                openProxy(context, encryptedVolume, path, size)
            } else if (allowUnsafeExport) {
                openExported(context, encryptedVolume, path, size)
            } else {
                null
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
                return null
            }

            val thread = HandlerThread("VlcMediaSource").apply { start() }
            val handler = Handler(thread.looper)
            val backingFileReleased = AtomicBoolean()
            fun releaseBackingFile() {
                if (backingFileReleased.compareAndSet(false, true)) {
                    encryptedVolume.closeFile(fileHandle)
                    thread.quitSafely()
                }
            }
            val callback = object : ProxyFileDescriptorCallback() {
                override fun onGetSize() = size

                override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                    return encryptedVolume.read(fileHandle, offset, data, 0, size.toLong()).coerceAtLeast(0)
                }

                override fun onRelease() {
                    releaseBackingFile()
                }
            }

            val storageManager = context.getSystemService(StorageManager::class.java)
            val pfd = try {
                storageManager.openProxyFileDescriptor(
                    ParcelFileDescriptor.MODE_READ_ONLY,
                    callback,
                    handler
                )
            } catch (e: Exception) {
                releaseBackingFile()
                throw e
            }
            return VlcMediaSource(pfd) {
                try {
                    pfd.close()
                } finally {
                    // Run after any reads already queued on the proxy callback thread.
                    handler.post { releaseBackingFile() }
                }
            }
        }

        private fun openExported(
            context: Context,
            encryptedVolume: EncryptedVolume,
            path: String,
            size: Long
        ): VlcMediaSource? {
            // Android before O has no ProxyFileDescriptorCallback. This export path
            // is permitted only when the caller has verified that the user enabled
            // the existing external-open unsafe feature.
            val provider = EncryptedFileProvider(context)
            val exportedFile = provider.createFile(path, size) ?: return null
            if (!provider.exportFile(exportedFile, encryptedVolume)) {
                exportedFile.free()
                return null
            }
            val pfd = exportedFile.open(ParcelFileDescriptor.MODE_READ_ONLY, true)
            return VlcMediaSource(pfd) {
                pfd.close()
            }
        }
    }
}
