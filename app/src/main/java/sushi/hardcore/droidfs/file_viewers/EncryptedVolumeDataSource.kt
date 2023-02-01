package sushi.hardcore.droidfs.file_viewers

import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import kotlin.math.min

class EncryptedVolumeDataSource(private val encryptedVolume: EncryptedVolume, private val filePath: String): DataSource {
    private var fileHandle = -1L
    private var fileOffset: Long = 0
    private var bytesRemaining: Long = -1

    override fun open(dataSpec: DataSpec): Long {
        fileHandle = encryptedVolume.openFile(filePath)
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
        return ConstValues.FAKE_URI
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