package sushi.hardcore.droidfs

import android.content.Context
import coil3.Extras
import coil3.ImageLoader
import coil3.ImageLoader.Builder
import coil3.video.VideoFrameDecoder
import coil3.video.preferVideoFrameEmbeddedThumbnail
import coil3.video.videoFramePercent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import sushi.hardcore.droidfs.content_providers.VolumeProvider
import sushi.hardcore.droidfs.filesystems.EncryptedFileReaderFileSystem
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.Observable

class VolumeManager(private val context: Context): Observable<VolumeManager.Observer>() {
    interface Observer {
        fun onVolumeStateChanged(volume: VolumeData) {}
        fun onAllVolumesClosed() {}
    }

    private class VolumeResources(val volume: EncryptedVolume, context: Context) {
        private val scopeDelegate = lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
        private val imageLoaderDelegate = lazy {
            Builder(context).diskCache(null)
                .fileSystem(EncryptedFileReaderFileSystem(volume)).components {
                    add(VideoFrameDecoder.Factory())
                }.also {
                    it.extras[Extras.Key.videoFramePercent] = 0.1
                    it.extras[Extras.Key.preferVideoFrameEmbeddedThumbnail] = true
                }.build()
        }
        val scope by scopeDelegate
        val imageLoader by imageLoaderDelegate

        fun destroy() {
            if (scopeDelegate.isInitialized()) scope.cancel()
            if (imageLoaderDelegate.isInitialized()) imageLoader.shutdown()
        }
    }

    private var id = 0
    private val volumes = HashMap<Int, VolumeResources>()
    private val volumesData = HashMap<VolumeData, Int>()

    fun insert(volume: EncryptedVolume, data: VolumeData): Int {
        volumes[id] = VolumeResources(volume, context)
        volumesData[data] = id
        observers.forEach { it.onVolumeStateChanged(data) }
        VolumeProvider.notifyRootsChanged(context)
        return id++
    }

    fun isOpen(volume: VolumeData): Boolean {
        return volumesData.containsKey(volume)
    }

    fun getVolumeId(volume: VolumeData): Int? {
        return volumesData[volume]
    }

    fun getVolume(id: Int): EncryptedVolume? {
        return volumes[id]?.volume
    }

    fun getCoroutineScope(volumeId: Int): CoroutineScope {
        return volumes[volumeId]!!.scope
    }

    fun getImageLoader(volumeId: Int): ImageLoader? {
        return volumes[volumeId]?.imageLoader
    }

    fun listVolumes(): List<Pair<Int, VolumeData>> {
        return volumesData.map { (data, id) -> Pair(id, data) }
    }

    fun getVolumeCount() = volumes.size

    fun closeVolume(id: Int) {
        volumes.remove(id)?.let { resources ->
            resources.destroy()
            resources.volume.closeVolume()
            volumesData.filter { it.value == id }.forEach { entry ->
                volumesData.remove(entry.key)
                observers.forEach { it.onVolumeStateChanged(entry.key) }
            }
            VolumeProvider.notifyRootsChanged(context)
        }
    }

    fun closeAll() {
        volumes.values.forEach {
            it.destroy()
            it.volume.closeVolume()
        }
        volumes.clear()
        volumesData.clear()
        observers.forEach { it.onAllVolumesClosed() }
        VolumeProvider.notifyRootsChanged(context)
    }
}