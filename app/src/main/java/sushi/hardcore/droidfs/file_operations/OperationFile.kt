package sushi.hardcore.droidfs.file_operations

import sushi.hardcore.droidfs.explorers.ExplorerElement

class OperationFile(val explorerElement: ExplorerElement, var dstPath: String? = null, var overwriteConfirmed: Boolean = false) {
    companion object {
        fun fromExplorerElement(e: ExplorerElement): OperationFile {
            return OperationFile(e, null)
        }
    }
}