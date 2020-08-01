package sushi.hardcore.droidfs.explorers

import sushi.hardcore.droidfs.util.PathUtils
import java.util.*

class ExplorerElement(val name: String, val elementType: Short, val size: Long, mtime: Long, private val parentPath: String) {
    val mTime = Date((mtime * 1000).toString().toLong())

    val isDirectory: Boolean
        get() = elementType.toInt() == 0

    val isParentFolder: Boolean
        get() = elementType.toInt() == -1

    val isRegularFile: Boolean
        get() = elementType.toInt() == 1

    fun getFullPath(): String {
        return PathUtils.path_join(parentPath, name)
    }
}