package sushi.hardcore.droidfs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

class VolumeManager {
    private var id = 0
    private val volumes = HashMap<Int, EncryptedVolume>()
    private val volumesData = HashMap<VolumeData, Int>()
    private val scopes = HashMap<Int, CoroutineScope>()

    fun insert(volume: EncryptedVolume, data: VolumeData): Int {
        volumes[id] = volume
        volumesData[data] = id
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

    fun getCoroutineScope(volumeId: Int): CoroutineScope {
        return scopes[volumeId] ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes[volumeId] = it }
    }

    fun closeVolume(id: Int) {
        volumes.remove(id)?.let { volume ->
            scopes[id]?.cancel()
            volume.close()
            volumesData.filter { it.value == id }.forEach {
                volumesData.remove(it.key)
            }
        }
    }

    fun closeAll() {
        volumes.forEach {
            scopes[it.key]?.cancel()
            it.value.close()
        }
        volumes.clear()
        volumesData.clear()
    }
}