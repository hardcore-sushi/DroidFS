package sushi.hardcore.droidfs.file_viewers

import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import kotlin.math.ceil
import kotlin.math.min

class EncryptedVolumeDataSource(private val encryptedVolume: EncryptedVolume, private val filePath: String): DataSource {
    private var fileHandle = -1L
    private var fileSize: Long = -1
    private var fileOffset: Long = 0
    override fun open(dataSpec: DataSpec): Long {
        fileOffset = dataSpec.position
        fileHandle = encryptedVolume.openFile(filePath)
        fileSize = encryptedVolume.getAttr(filePath)!!.size
        return fileSize
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
        if (fileOffset >= fileSize){
            return -1
        }
        var totalRead = 0
        for (i in 0 until ceil(readLength.toDouble()/ConstValues.MAX_KERNEL_WRITE).toInt()){
            val tmpReadLength = min(readLength-totalRead, ConstValues.MAX_KERNEL_WRITE)
            val tmpBuff = if (fileOffset+tmpReadLength > fileSize){
                ByteArray((fileSize-fileOffset).toInt())
            } else {
                ByteArray(tmpReadLength)
            }
            val read = encryptedVolume.read(fileHandle, tmpBuff, fileOffset)
            System.arraycopy(tmpBuff, 0, buffer, offset+totalRead, read)
            fileOffset += read
            totalRead += read
        }
        return totalRead
    }

    class Factory(private val encryptedVolume: EncryptedVolume, private val filePath: String): DataSource.Factory {
        override fun createDataSource(): DataSource {
            return EncryptedVolumeDataSource(encryptedVolume, filePath)
        }
    }
}