package sushi.hardcore.droidfs

import android.os.ParcelFileDescriptor

class MemFile private constructor(private val fd: Int) {
    companion object {
        private external fun createMemFile(name: String, size: Long): Int
        init {
            System.loadLibrary("memfile")
        }

        fun create(name: String, size: Long): MemFile? {
            val fd = createMemFile(name, size)
            return if (fd > 0) MemFile(fd) else null
        }
    }

    private external fun close(fd: Int)

    fun getParcelFileDescriptor(): ParcelFileDescriptor = ParcelFileDescriptor.fromFd(fd)
    fun close() = close(fd)
}