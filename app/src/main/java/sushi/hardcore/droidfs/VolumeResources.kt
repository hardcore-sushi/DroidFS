package sushi.hardcore.droidfs

import android.content.Context
import coil3.Extras
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import coil3.video.preferVideoFrameEmbeddedThumbnail
import coil3.video.videoFramePercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import sushi.hardcore.droidfs.filesystems.EncryptedFileReaderFileSystem
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

class VolumeResources(val volume: EncryptedVolume, context: Context) {
    private val scopeDelegate = lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    private val imageLoaderDelegate = lazy {
        ImageLoader.Builder(context).diskCache(null)
            .fileSystem(EncryptedFileReaderFileSystem(volume)).components {
                add(VideoFrameDecoder.Factory())
            }.also {
                it.extras[Extras.Key.videoFramePercent] = 0.1
                it.extras[Extras.Key.preferVideoFrameEmbeddedThumbnail] = true
            }.build()
    }
    val scope by scopeDelegate
    val imageLoader by imageLoaderDelegate

    fun evictImageCache(shouldEvict: (String) -> Boolean) {
        val cache = imageLoader.memoryCache ?: return
        cache.keys.filter { shouldEvict(it.key) }.forEach { cache.remove(it) }
    }

    fun destroy() {
        if (scopeDelegate.isInitialized()) scope.cancel()
        if (imageLoaderDelegate.isInitialized()) imageLoader.shutdown()
    }
}
