package sushi.hardcore.droidfs

import android.os.ParcelFileDescriptor
import android.system.Os

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

    fun dup(): ParcelFileDescriptor = ParcelFileDescriptor.fromFd(fd)
    fun toParcelFileDescriptor(): ParcelFileDescriptor = ParcelFileDescriptor.adoptFd(fd)
    fun close() = Os.close(ParcelFileDescriptor.adoptFd(fd).fileDescriptor)
}