package sushi.hardcore.droidfs

import sushi.hardcore.droidfs.filesystems.EncryptedVolume

class VolumeManager {
    private var id = 0
    private val volumes = HashMap<Int, EncryptedVolume>()
    private val volumesData = HashMap<VolumeData, Int>()

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

    fun closeVolume(id: Int) {
        volumes.remove(id)?.let { volume ->
            volume.close()
            volumesData.filter { it.value == id }.forEach {
                volumesData.remove(it.key)
            }
        }
    }

    fun closeAll() {
        volumes.forEach { it.value.close() }
        volumes.clear()
        volumesData.clear()
    }
}