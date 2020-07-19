package sushi.hardcore.droidfs.explorers

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.provider.TemporaryFileProvider
import sushi.hardcore.droidfs.util.FilesUtils
import java.util.*

class ExplorerActivityPick : BaseExplorerActivity() {
    private var result_intent = Intent()
    override fun init() {
        super.init()
        result_intent.putExtra("sessionID", gocryptfsVolume.sessionID)
    }

    override fun onExplorerItemClick(position: Int) {
        val wasSelecting = explorer_adapter.selectedItems.isNotEmpty()
        explorer_adapter.onItemClick(position)
        if (explorer_adapter.selectedItems.isEmpty()) {
            if (!wasSelecting) {
                val full_path = FilesUtils.path_join(current_path, explorer_elements[position].name)
                when {
                    explorer_elements[position].isDirectory -> {
                        setCurrentPath(full_path)
                    }
                    explorer_elements[position].isParentFolder -> {
                        setCurrentPath(FilesUtils.get_parent_path(current_path))
                    }
                    else -> {
                        result_intent.putExtra("path", full_path)
                        return_activity_result()
                    }
                }
            }
        }
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer_pick, menu)
        handleMenuItems(menu)
        val any_item_selected = explorer_adapter.selectedItems.isNotEmpty()
        menu.findItem(R.id.explorer_menu_select_all).isVisible = any_item_selected
        menu.findItem(R.id.explorer_menu_validate).isVisible = any_item_selected
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.explorer_menu_select_all -> {
                explorer_adapter.selectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.explorer_menu_validate -> {
                val paths = ArrayList<String>()
                val types = ArrayList<Int>()
                for (i in explorer_adapter.selectedItems) {
                    val e = explorer_elements[i]
                    paths.add(FilesUtils.path_join(current_path, e.name))
                    types.add(e.elementType.toInt())
                }
                result_intent.putStringArrayListExtra("paths", paths)
                result_intent.putIntegerArrayListExtra("types", types)
                return_activity_result()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun return_activity_result() {
        setResult(Activity.RESULT_OK, result_intent)
        finish()
    }

    override fun closeVolumeOnDestroy() {
        //don't close volume
        TemporaryFileProvider.wipeAll()
    }

    override fun closeVolumeOnUserExit() {
        super.closeVolumeOnUserExit()
        super.closeVolumeOnDestroy()
    }
}