package sushi.hardcore.droidfs

import android.net.Uri

object Constants {
    const val VOLUME_DATABASE_NAME = "SavedVolumes"
    const val CRYFS_LOCAL_STATE_DIR = "cryfsLocalState"
    const val SORT_ORDER_KEY = "sort_order"
    val FAKE_URI: Uri = Uri.parse("fakeuri://droidfs")
    const val WIPE_PASSES = 2
    const val IO_BUFF_SIZE = 16384
    const val SLIDESHOW_DELAY: Long = 4000
    const val DEFAULT_THEME_VALUE = "dark_green"
    const val DEFAULT_VOLUME_KEY = "default_volume"
    const val REMEMBER_VOLUME_KEY = "remember_volume"
    const val PIN_PASSWORDS_KEY = "pin_passwords"
}