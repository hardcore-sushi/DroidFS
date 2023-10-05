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
class EncryptedVolumeDataSource(private val encryptedVolume: EncryptedVolume, private val filePath: String):
    DataSource {
    private var fileHandle = -1L
    private var fileOffset: Long = 0
    private var bytesRemaining: Long = -1

    override fun open(dataSpec: DataSpec): Long {
        fileHandle = encryptedVolume.openFileReadMode(filePath)
        fileOffset = dataSpec.position
        val fileSize = encryptedVolume.getAttr(filePath)!!.size
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            fileSize - fileOffset
        } else {
            min(fileSize, dataSpec.length)
        }
        return bytesRemaining
    }

    override fun getUri(): Uri {
        return Constants.FAKE_URI
    }

    override fun close() {
        encryptedVolume.closeFile(fileHandle)
    }

    override fun addTransferListener(transferListener: TransferListener) {
        //too lazy to implement this
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val originalOffset = fileOffset
        while (fileOffset < originalOffset+readLength && encryptedVolume.read(
                fileHandle,
                fileOffset,
                buffer,
                offset+(fileOffset-originalOffset),
                (originalOffset+readLength)-fileOffset
            ).also { fileOffset += it } > 0
        ) {}
        val totalRead = fileOffset-originalOffset
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