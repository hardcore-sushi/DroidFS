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
    const val THUMBNAIL_MAX_SIZE_KEY = "thumbnail_max_size"
    const val DEFAULT_THUMBNAIL_MAX_SIZE = 10_000L
    const val GRID_COLUMN_COUNT_KEY = "grid_column_count"
    const val DEFAULT_GRID_COLUMN_COUNT = 3
    const val PIN_PASSWORDS_KEY = "pin_passwords"
}
