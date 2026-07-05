package sushi.hardcore.droidfs.file_viewers

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

@OptIn(UnstableApi::class)
class MediaDataSourceFactory(
    encryptedVolume: EncryptedVolume,
    filePath: String
) : DataSource.Factory {
    private val upstreamFactory = EncryptedVolumeDataSource.Factory(encryptedVolume, filePath)

    override fun createDataSource(): DataSource {
        return MpegTsPacketHeaderStrippingDataSource(upstreamFactory)
    }
}
