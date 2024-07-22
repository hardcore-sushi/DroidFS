package sushi.hardcore.droidfs.add_volume

import sushi.hardcore.droidfs.R

enum class Action {
    OPEN,
    ADD,
    CREATE,
    ;

    fun getStringResId() = when (this) {
        OPEN -> R.string.open
        ADD -> R.string.add_volume
        CREATE -> R.string.create_volume
    }
}