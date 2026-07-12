package sushi.hardcore.droidfs.file_viewers

import android.net.Uri
import androidx.media3.common.C
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import java.io.IOException
import kotlin.math.min

@OptIn(UnstableApi::class)
class EncryptedVolumeDataSource(private val encryptedVolume: EncryptedVolume, private val filePath: String):
    DataSource {
    private var fileHandle = -1L
    private var fileOffset: Long = 0
    private var bytesRemaining: Long = -1

    override fun open(dataSpec: DataSpec): Long {
        close()
        fileHandle = encryptedVolume.openFileReadMode(filePath)
        if (fileHandle == -1L) {
            throw IOException("Failed to open encrypted media")
        }
        fileOffset = dataSpec.position
        val fileSize = encryptedVolume.getAttr(filePath)?.size ?: run {
            close()
            throw IOException("Failed to read encrypted media attributes")
        }
        val availableBytes = (fileSize - fileOffset).coerceAtLeast(0)
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            availableBytes
        } else {
            min(availableBytes, dataSpec.length)
        }
        return bytesRemaining
    }

    override fun getUri(): Uri {
        return Constants.FAKE_URI
    }

    override fun close() {
        if (fileHandle != -1L) {
            encryptedVolume.closeFile(fileHandle)
            fileHandle = -1L
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        //too lazy to implement this
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) {
            return 0
        }
        val bytesToRead = min(readLength.toLong(), bytesRemaining).toInt()
        if (bytesToRead == 0) {
            return C.RESULT_END_OF_INPUT
        }
        val originalOffset = fileOffset
        while (fileOffset < originalOffset + bytesToRead) {
            val read = encryptedVolume.read(
                fileHandle,
                fileOffset,
                buffer,
                offset + (fileOffset - originalOffset),
                (originalOffset + bytesToRead) - fileOffset
            )
            if (read < 0) {
                throw IOException("Failed to read encrypted media")
            }
            if (read == 0) {
                break
            }
            fileOffset += read
        }
        val totalRead = fileOffset - originalOffset
        bytesRemaining -= totalRead
        return if (totalRead == 0L) {
            C.RESULT_END_OF_INPUT
        } else {
            totalRead.toInt()
        }
    }

    class Factory(private val encryptedVolume: EncryptedVolume, private val filePath: String): DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedVolumeDataSource(encryptedVolume, filePath)
        }
    }
}
