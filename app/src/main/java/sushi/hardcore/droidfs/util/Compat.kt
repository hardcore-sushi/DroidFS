package sushi.hardcore.droidfs.util

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

object Compat {
    inline fun <reified T: Parcelable> getParcelable(bundle: Bundle, name: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(name, T::class.java)
        } else {
            @Suppress("Deprecation")
            bundle.getParcelable(name)
        }
    }
}