package sushi.hardcore.droidfs.file_operations

import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File

class OperationFile(val srcPath: String, val type: Int, var dstPath: String? = null, var overwriteConfirmed: Boolean = false) {
    val isDirectory = type == Stat.S_IFDIR
    val name: String by lazy {
        File(srcPath).name
    }
    val parentPath by lazy {
        PathUtils.getParentPath(srcPath)
    }

    companion object {
        fun fromExplorerElement(e: ExplorerElement): OperationFile {
            return OperationFile(e.fullPath, e.stat.type)
        }
    }
}