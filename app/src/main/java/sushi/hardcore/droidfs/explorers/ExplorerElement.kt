package sushi.hardcore.droidfs.explorers

import sushi.hardcore.droidfs.util.PathUtils
import java.util.*

class ExplorerElement(val name: String, val elementType: Short, var size: Long, val mtime: Long, val parentPath: String) {
    val mTime = Date((mtime * 1000).toString().toLong())
    val fullPath: String = PathUtils.pathJoin(parentPath, name)

    val isDirectory: Boolean
        get() = elementType.toInt() == 0

    val isParentFolder: Boolean
        get() = elementType.toInt() == -1

    val isRegularFile: Boolean
        get() = elementType.toInt() == 1

    companion object {
        fun sortBy(sortOrder: String, explorerElements: MutableList<ExplorerElement>) {
            when (sortOrder) {
                "name" -> {
                    explorerElements.sortWith(Comparator { o1, o2 -> o1.fullPath.compareTo(o2.fullPath) })
                }
                "size" -> {
                    explorerElements.sortWith(Comparator { o1, o2 -> (o1.size - o2.size).toInt() })
                }
                "date" -> {
                    explorerElements.sortWith(Comparator { o1, o2 -> o1.mTime.compareTo(o2.mTime) })
                }
                "name_desc" -> {
                    explorerElements.sortWith(Comparator { o1, o2 -> o2.fullPath.compareTo(o1.fullPath) })
                }
                "size_desc" -> {
                    explorerElements.sortWith(Comparator { o1, o2 -> (o2.size - o1.size).toInt() })
                }
                "date_desc" -> {
                    explorerElements.sortWith(Comparator { o1, o2 -> o2.mTime.compareTo(o1.mTime) })
                }
            }
        }
    }
}