package sushi.hardcore.droidfs.explorers

import sushi.hardcore.droidfs.util.PathUtils
import java.util.*

class ExplorerElement(val name: String, val elementType: Short, var size: Long = -1, mTime: Long = -1, val parentPath: String) {
    val mTime = Date((mTime * 1000).toString().toLong())
    val fullPath: String = PathUtils.pathJoin(parentPath, name)

    val isDirectory: Boolean
        get() = elementType.toInt() == 0

    val isParentFolder: Boolean
        get() = elementType.toInt() == -1

    val isRegularFile: Boolean
        get() = elementType.toInt() == 1

    companion object {
        @JvmStatic
        //this function is needed because I had some problems calling the constructor from JNI, probably due to arguments with default values
        fun new(name: String, elementType: Short, size: Long, mTime: Long, parentPath: String): ExplorerElement {
            return ExplorerElement(name, elementType, size, mTime, parentPath)
        }

        private fun foldersFirst(a: ExplorerElement, b: ExplorerElement, default: () -> Int): Int {
            return if (a.isDirectory && b.isRegularFile) {
                -1
            } else if (b.isDirectory && a.isRegularFile) {
                1
            } else {
                default()
            }
        }
        private fun doSort(a: ExplorerElement, b: ExplorerElement, foldersFirst: Boolean, sorter: () -> Int): Int {
            return if (foldersFirst) {
                foldersFirst(a, b, sorter)
            } else {
                sorter()
            }
        }
        fun sortBy(sortOrder: String, foldersFirst: Boolean, explorerElements: MutableList<ExplorerElement>) {
            when (sortOrder) {
                "name" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { a.fullPath.compareTo(b.fullPath, true) }
                    }
                }
                "size" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { (a.size - b.size).toInt() }
                    }
                }
                "date" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { a.mTime.compareTo(b.mTime) }
                    }
                }
                "name_desc" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { b.fullPath.compareTo(a.fullPath, true) }
                    }
                }
                "size_desc" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { (b.size - a.size).toInt() }
                    }
                }
                "date_desc" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { b.mTime.compareTo(a.mTime) }
                    }
                }
            }
        }
    }
}