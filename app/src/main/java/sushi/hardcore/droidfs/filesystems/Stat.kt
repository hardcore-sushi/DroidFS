package sushi.hardcore.droidfs.filesystems

class Stat(mode: Int, var size: Long, val mTime: Long) {
    companion object {
        private const val S_IFMT = 0xF000
        const val S_IFDIR = 0x4000
        const val S_IFREG = 0x8000
        const val S_IFLNK = 0xA000
        const val PARENT_FOLDER_TYPE = 0xE000

        fun parentFolderStat(): Stat {
            return Stat(PARENT_FOLDER_TYPE, -1, -1)
        }
    }

    val type = mode and S_IFMT
}