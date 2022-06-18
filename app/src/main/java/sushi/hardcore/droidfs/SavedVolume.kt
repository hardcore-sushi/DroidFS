package sushi.hardcore.droidfs

import android.os.Parcel
import android.os.Parcelable
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File

class SavedVolume(val name: String, val isHidden: Boolean = false, val type: Byte, var encryptedHash: ByteArray? = null, var iv: ByteArray? = null): Parcelable {

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

    companion object {
        const val VOLUMES_DIRECTORY = "volumes"

        @JvmField
        val CREATOR = object : Parcelable.Creator<SavedVolume> {
            override fun createFromParcel(parcel: Parcel) = SavedVolume(parcel)
            override fun newArray(size: Int) = arrayOfNulls<SavedVolume>(size)
        }

        fun getHiddenVolumeFullPath(filesDir: String, name: String): String {
            return PathUtils.pathJoin(filesDir, VOLUMES_DIRECTORY, name)
        }
    }
}