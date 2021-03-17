package sushi.hardcore.droidfs.file_viewers

import android.net.Uri
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.TransferListener
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.GocryptfsVolume
import kotlin.math.ceil
import kotlin.math.min

class GocryptfsDataSource(private val gocryptfsVolume: GocryptfsVolume, private val filePath: String): DataSource {
    private var handleID = -1
    private var fileSize: Long = -1
    private var fileOffset: Long = 0
    override fun open(dataSpec: DataSpec): Long {
        fileOffset = dataSpec.position
        handleID = gocryptfsVolume.openReadMode(filePath)
        fileSize = gocryptfsVolume.getSize(filePath)
        return fileSize
    }

    override fun getUri(): Uri {
        return ConstValues.fakeUri
    }

    override fun close() {
        gocryptfsVolume.closeFile(handleID)
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
            val read = gocryptfsVolume.readFile(handleID, fileOffset, tmpBuff)
            System.arraycopy(tmpBuff, 0, buffer, offset+totalRead, read)
            fileOffset += read
            totalRead += read
        }
        return totalRead
    }

    class Factory(private val gocryptfsVolume: GocryptfsVolume, private val filePath: String): DataSource.Factory {
        override fun createDataSource(): DataSource {
            return GocryptfsDataSource(gocryptfsVolume, filePath)
        }
    }
}