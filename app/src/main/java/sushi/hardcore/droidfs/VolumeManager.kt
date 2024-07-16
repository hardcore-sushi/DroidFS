package sushi.hardcore.droidfs

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import sushi.hardcore.droidfs.content_providers.VolumeProvider
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.Observable

class VolumeManager(private val context: Context): Observable<VolumeManager.Observer>() {
    interface Observer {
        fun onVolumeStateChanged(volume: VolumeData) {}
        fun onAllVolumesClosed() {}
    }

    private var id = 0
    private val volumes = HashMap<Int, EncryptedVolume>()
    private val volumesData = HashMap<VolumeData, Int>()
    private val scopes = HashMap<Int, CoroutineScope>()

    fun insert(volume: EncryptedVolume, data: VolumeData): Int {
        volumes[id] = volume
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
        return volumes[id]
    }

    fun listVolumes(): List<Pair<Int, VolumeData>> {
        return volumesData.map { (data, id) -> Pair(id, data) }
    }

    fun getVolumeCount() = volumes.size

    fun getCoroutineScope(volumeId: Int): CoroutineScope {
        return scopes[volumeId] ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes[volumeId] = it }
    }

    fun closeVolume(id: Int) {
        volumes.remove(id)?.let { volume ->
            scopes[id]?.cancel()
            volume.closeVolume()
            volumesData.filter { it.value == id }.forEach { entry ->
                volumesData.remove(entry.key)
                observers.forEach { it.onVolumeStateChanged(entry.key) }
            }
            VolumeProvider.notifyRootsChanged(context)
        }
    }

    fun closeAll() {
        volumes.forEach {
            scopes[it.key]?.cancel()
            it.value.closeVolume()
        }
        volumes.clear()
        volumesData.clear()
        observers.forEach { it.onAllVolumesClosed() }
        VolumeProvider.notifyRootsChanged(context)
    }
}