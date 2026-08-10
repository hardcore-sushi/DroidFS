package sushi.hardcore.droidfs.filesystems

import java.io.IOException
import java.io.OutputStream

class EncryptedFileOutputStream(
    private val volume: EncryptedVolume,
    private val fileHandle: Long,
    private val path: String,
) : OutputStream() {
    private var offset: Long = 0

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var written = 0
        while (written < len) {
            val w = volume.write(fileHandle, offset, b, (off + written).toLong(), (len - written).toLong())
            if (w <= 0) {
                throw IOException("Encrypted file write failed")
            }
            written += w
            offset += w
        }
    }

    override fun close() {
        volume.truncate(path, offset)
        volume.closeFile(fileHandle)
    }
}