package sushi.hardcore.droidfs.file_viewers

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import java.io.File

@OptIn(UnstableApi::class)
class MediaDataSourceFactory(
    encryptedVolume: EncryptedVolume,
    filePath: String
) : DataSource.Factory {
    private val upstreamFactory = EncryptedVolumeDataSource.Factory(encryptedVolume, filePath)
    private val stripPacketHeaders = MediaContainers.hasPacketizedTransportStream(File(filePath).name)

    override fun createDataSource(): DataSource {
        return if (stripPacketHeaders) {
            MpegTsPacketHeaderStrippingDataSource(upstreamFactory)
        } else {
            upstreamFactory.createDataSource()
        }
    }
}
