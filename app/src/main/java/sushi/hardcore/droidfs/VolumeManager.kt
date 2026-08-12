package sushi.hardcore.droidfs

import android.content.Context
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import sushi.hardcore.droidfs.content_providers.VolumeProvider
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.Observable

class VolumeManager(private val context: Context): Observable<VolumeManager.Observer>() {
    interface Observer {
        fun onVolumeStateChanged(volume: VolumeData) {}
        fun onAllVolumesClosed() {}
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

    fun getVolumeResources(volumeId: Int): VolumeResources? {
        return volumes[volumeId]
    }

    fun getCoroutineScope(volumeId: Int): CoroutineScope {
        return getVolumeResources(volumeId)!!.scope
    }

    fun getImageLoader(volumeId: Int): ImageLoader {
        return getVolumeResources(volumeId)!!.imageLoader
    }

    fun evictImageCache(volumeId: Int, shouldEvict: (String) -> Boolean) {
        getVolumeResources(volumeId)!!.evictImageCache(shouldEvict)
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