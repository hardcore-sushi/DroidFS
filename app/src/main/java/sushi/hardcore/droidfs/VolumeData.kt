package sushi.hardcore.droidfs

import android.os.Parcel
import android.os.Parcelable
import sushi.hardcore.droidfs.filesystems.CryfsVolume
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.GocryptfsVolume
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File
import java.io.FileInputStream

class VolumeData(val name: String, val isHidden: Boolean = false, val type: Byte, var encryptedHash: ByteArray? = null, var iv: ByteArray? = null): Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readByte() != 0.toByte(),
        parcel.readByte(),
        parcel.createByteArray(),
        parcel.createByteArray()
    )

    val shortName: String by lazy {
        File(name).name
    }

    fun getFullPath(filesDir: String): String {
        return if (isHidden)
            getHiddenVolumeFullPath(filesDir, name)
        else
            name
    }

    fun canRead(filesDir: String): Boolean {
        val volumePath = getFullPath(filesDir)
        if (!File(volumePath).canRead()) {
            return false
        }
        val configFile = when (type) {
            EncryptedVolume.GOCRYPTFS_VOLUME_TYPE -> PathUtils.pathJoin(volumePath, GocryptfsVolume.CONFIG_FILE_NAME)
            EncryptedVolume.CRYFS_VOLUME_TYPE -> PathUtils.pathJoin(volumePath, CryfsVolume.CONFIG_FILE_NAME)
            else -> return false
        }
        var success = true
        try {
            with (FileInputStream(configFile)) {
                read()
                close()
            }
        } catch (e: Exception) {
            success = false
        }
        return success
    }

    fun canWrite(filesDir: String): Boolean {
        return File(getFullPath(filesDir)).canWrite()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        with (dest) {
            writeString(name)
            writeByte(if (isHidden) 1 else 0)
            writeByte(type)
            writeByteArray(encryptedHash)
            writeByteArray(iv)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is VolumeData) {
            return false
        }
        return other.name == name && other.isHidden == isHidden
    }

    override fun hashCode(): Int {
        return name.hashCode()+isHidden.hashCode()
    }

    companion object {
        const val VOLUMES_DIRECTORY = "volumes"

        @JvmField
        val CREATOR = object : Parcelable.Creator<VolumeData> {
            override fun createFromParcel(parcel: Parcel) = VolumeData(parcel)
            override fun newArray(size: Int) = arrayOfNulls<VolumeData>(size)
        }

        fun getHiddenVolumeFullPath(filesDir: String, name: String): String {
            return PathUtils.pathJoin(filesDir, VOLUMES_DIRECTORY, name)
        }
    }
}