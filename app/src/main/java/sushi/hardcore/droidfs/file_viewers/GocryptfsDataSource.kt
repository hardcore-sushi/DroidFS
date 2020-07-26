package sushi.hardcore.droidfs.file_viewers

import android.net.Uri
import com.google.android.exoplayer2.upstream.*
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.util.GocryptfsVolume

class GocryptfsDataSource(private val gocryptfsVolume: GocryptfsVolume, private val filePath: String): DataSource {
    private var handleID = -1
    private var fileSize: Long = -1
    private var fileOffset: Long = 0
    override fun open(dataSpec: DataSpec?): Long {
        dataSpec?.let {
            fileOffset = dataSpec.position
        }
        handleID = gocryptfsVolume.open_read_mode(filePath)
        fileSize = gocryptfsVolume.get_size(filePath)
        return fileSize
    }

    override fun getUri(): Uri {
        return ConstValues.fakeUri
    }

    override fun close() {
        gocryptfsVolume.close_file(handleID)
    }

    override fun addTransferListener(transferListener: TransferListener?) {
        //too lazy to implement this
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (fileOffset >= fileSize){
            return -1
        }
        val tmpBuff = if (fileOffset+readLength > fileSize){
            ByteArray((fileSize-fileOffset).toInt())
        } else {
            ByteArray(readLength)
        }
        val read =  gocryptfsVolume.read_file(handleID, fileOffset, tmpBuff)
        fileOffset += read
        System.arraycopy(tmpBuff, 0, buffer, offset, read)
        return read
    }

    class Factory(private val gocryptfsVolume: GocryptfsVolume, private val filePath: String): DataSource.Factory{
        override fun createDataSource(): DataSource {
            return GocryptfsDataSource(gocryptfsVolume, filePath)
        }
    }
}