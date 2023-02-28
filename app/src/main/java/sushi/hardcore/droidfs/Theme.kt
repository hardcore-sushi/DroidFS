package sushi.hardcore.droidfs

import android.content.SharedPreferences
import android.os.Parcel
import android.os.Parcelable

class Theme(var color: String, var black: Boolean) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readByte() != 0.toByte(),
    )

    fun toResourceId(): Int {
        return if (black) {
            when (color) {
                "red" -> R.style.BlackRed
                "blue" -> R.style.BlackBlue
                "yellow" -> R.style.BlackYellow
                "orange" -> R.style.BlackOrange
                "purple" -> R.style.BlackPurple
                "pink" -> R.style.BlackPink
                else -> R.style.BlackGreen
            }
        } else {
            when (color) {
                "red" -> R.style.DarkRed
                "blue" -> R.style.DarkBlue
                "yellow" -> R.style.DarkYellow
                "orange" -> R.style.DarkOrange
                "purple" -> R.style.DarkPurple
                "pink" -> R.style.DarkPink
                else -> R.style.BaseTheme
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Theme) {
            return false
        }
        return other.color == color && other.black == black
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        with(dest) {
            writeString(color)
            writeByte(if (black) 1 else 0)
        }
    }


    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<Theme> {
            override fun createFromParcel(parcel: Parcel) = Theme(parcel)
            override fun newArray(size: Int) = arrayOfNulls<Theme>(size)
        }

        fun fromSharedPrefs(sharedPrefs: SharedPreferences): Theme {
            val color = sharedPrefs.getString("color", "green")!!
            val black = sharedPrefs.getBoolean("black_theme", false)
            return Theme(color, black)
        }
    }
}