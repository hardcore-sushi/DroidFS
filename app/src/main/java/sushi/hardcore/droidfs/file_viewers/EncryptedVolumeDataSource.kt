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
import kotlin.math.min

@OptIn(UnstableApi::class)
class EncryptedVolumeDataSource(
    private val encryptedVolume: EncryptedVolume,
    private val filePath: String
) : DataSource {
    private var fileHandle = -1L
    private var fileOffset: Long = 0
    private var bytesRemaining: Long = -1

    override fun open(dataSpec: DataSpec): Long {
        fileHandle = encryptedVolume.openFileReadMode(filePath)
        fileOffset = dataSpec.position
        val fileSize = encryptedVolume.getAttr(filePath)!!.size
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

        val totalRead = readPlain(buffer, offset, bytesToRead)
        bytesRemaining -= totalRead
        return if (totalRead == 0L) {
            C.RESULT_END_OF_INPUT
        } else {
            totalRead.toInt()
        }
    }

    private fun readPlain(buffer: ByteArray, offset: Int, readLength: Int): Long {
        val startOffset = fileOffset
        val endOffset = startOffset + readLength
        while (fileOffset < endOffset && bytesRemaining > 0) {
            val read = encryptedVolume.read(
                fileHandle,
                fileOffset,
                buffer,
                offset + fileOffset - startOffset,
                endOffset - fileOffset
            )
            if (read <= 0) {
                break
            }
            fileOffset += read
        }
        return fileOffset - startOffset
    }

    class Factory(
        private val encryptedVolume: EncryptedVolume,
        private val filePath: String
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedVolumeDataSource(encryptedVolume, filePath)
        }
    }
}
