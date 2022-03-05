package sushi.hardcore.droidfs

import android.os.Parcel
import android.os.Parcelable
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File

class Volume(val name: String, val isHidden: Boolean = false, var encryptedHash: ByteArray? = null, var iv: ByteArray? = null): Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readByte() != 0.toByte(),
        parcel.createByteArray(),
        parcel.createByteArray()
    )

    val shortName: String by lazy {
        File(name).name
    }

    fun getFullPath(filesDir: String): String {
        return if (isHidden)
            PathUtils.pathJoin(filesDir, name)
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
            writeByteArray(encryptedHash)
            writeByteArray(iv)
        }
    }

    companion object CREATOR : Parcelable.Creator<Volume> {
        override fun createFromParcel(parcel: Parcel): Volume {
            return Volume(parcel)
        }

        override fun newArray(size: Int): Array<Volume?> {
            return arrayOfNulls(size)
        }
    }
}