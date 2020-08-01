package sushi.hardcore.droidfs.explorers

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.provider.RestrictedFileProvider
import sushi.hardcore.droidfs.util.PathUtils
import java.util.*

class ExplorerActivityPick : BaseExplorerActivity() {
    private var result_intent = Intent()
    override fun init() {
        super.init()
        result_intent.putExtra("sessionID", gocryptfsVolume.sessionID)
    }

    override fun onExplorerItemClick(position: Int) {
        val wasSelecting = explorerAdapter.selectedItems.isNotEmpty()
        explorerAdapter.onItemClick(position)
        if (explorerAdapter.selectedItems.isEmpty()) {
            if (!wasSelecting) {
                val full_path = PathUtils.path_join(currentDirectoryPath, explorerElements[position].name)
                when {
                    explorerElements[position].isDirectory -> {
                        setCurrentPath(full_path)
                    }
                    explorerElements[position].isParentFolder -> {
                        setCurrentPath(PathUtils.getParentPath(currentDirectoryPath))
                    }
                    else -> {
                        result_intent.putExtra("path", full_path)
                        returnActivityResult()
                    }
                }
            }
        }
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer_pick, menu)
        handleMenuItems(menu)
        val any_item_selected = explorerAdapter.selectedItems.isNotEmpty()
        menu.findItem(R.id.select_all).isVisible = any_item_selected
        menu.findItem(R.id.validate).isVisible = any_item_selected
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.select_all -> {
                explorerAdapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.validate -> {
                val paths = ArrayList<String>()
                val types = ArrayList<Int>()
                for (i in explorerAdapter.selectedItems) {
                    val e = explorerElements[i]
                    paths.add(PathUtils.path_join(currentDirectoryPath, e.name))
                    types.add(e.elementType.toInt())
                }
                result_intent.putStringArrayListExtra("paths", paths)
                result_intent.putIntegerArrayListExtra("types", types)
                returnActivityResult()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun returnActivityResult() {
        setResult(Activity.RESULT_OK, result_intent)
        finish()
    }

    override fun closeVolumeOnDestroy() {
        //don't close volume
        RestrictedFileProvider.wipeAll(this)
    }

    override fun closeVolumeOnUserExit() {
        super.closeVolumeOnUserExit()
        super.closeVolumeOnDestroy()
    }
}