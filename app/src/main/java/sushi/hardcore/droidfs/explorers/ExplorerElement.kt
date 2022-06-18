package sushi.hardcore.droidfs.explorers

import sushi.hardcore.droidfs.collation.getCollationKeyForFileName
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils
import java.text.Collator

class ExplorerElement(val name: String, val stat: Stat, val parentPath: String) {
    val fullPath: String = PathUtils.pathJoin(parentPath, name)
    val collationKey = Collator.getInstance().getCollationKeyForFileName(fullPath)

    val isDirectory: Boolean
        get() = stat.type == Stat.S_IFDIR

    val isRegularFile: Boolean
        get() = stat.type == Stat.S_IFREG

    val isSymlink: Boolean
        get() = stat.type == Stat.S_IFLNK

    val isParentFolder: Boolean
        get() = stat.type == Stat.PARENT_FOLDER_TYPE

    companion object {
        @JvmStatic
        //this function is needed because I had some problems calling the constructor from JNI, probably due to arguments with default values
        fun new(name: String, elementType: Int, size: Long, mTime: Long, parentPath: String): ExplorerElement {
            return ExplorerElement(name, Stat(elementType, size, mTime*1000), parentPath)
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
            return if (b.isParentFolder) {
                1
            } else if (a.isParentFolder) {
                -1
            } else {
                if (foldersFirst) {
                    foldersFirst(a, b, sorter)
                } else {
                    sorter()
                }
            }
        }
        fun sortBy(sortOrder: String, foldersFirst: Boolean, explorerElements: MutableList<ExplorerElement>) {
            when (sortOrder) {
                "name" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { a.collationKey.compareTo(b.collationKey) }
                    }
                }
                "size" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { (a.stat.size - b.stat.size).toInt() }
                    }
                }
                "date" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { a.stat.mTime.compareTo(b.stat.mTime) }
                    }
                }
                "name_desc" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { b.collationKey.compareTo(a.collationKey) }
                    }
                }
                "size_desc" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { (b.stat.size - a.stat.size).toInt() }
                    }
                }
                "date_desc" -> {
                    explorerElements.sortWith { a, b ->
                        doSort(a, b, foldersFirst) { b.stat.mTime.compareTo(a.stat.mTime) }
                    }
                }
            }
        }
    }
}