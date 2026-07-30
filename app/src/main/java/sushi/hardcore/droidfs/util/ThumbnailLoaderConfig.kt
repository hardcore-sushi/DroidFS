package sushi.hardcore.droidfs.util

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.preferVideoFrameEmbeddedThumbnailKey
import coil3.video.videoFramePercent
import sushi.hardcore.droidfs.filesystems.EncryptedFileReaderFileSystem
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

object ThumbnailLoaderConfig {
    fun imageLoader(context: Context, encryptedVolume: EncryptedVolume) =
        ImageLoader.Builder(context).diskCache(null)
            .fileSystem(EncryptedFileReaderFileSystem(encryptedVolume)).components {
                add(VideoFrameDecoder.Factory())
            }.build()

    fun applyVideoConfig(builder: ImageRequest.Builder) = builder.apply {
        videoFramePercent(0.1)
        preferVideoFrameEmbeddedThumbnailKey(true)
    }
}