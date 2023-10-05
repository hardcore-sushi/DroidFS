package sushi.hardcore.droidfs.explorers

import android.app.Activity
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.IntentUtils
import sushi.hardcore.droidfs.util.PathUtils

class ExplorerActivityPick : BaseExplorerActivity() {
    private var resultIntent = Intent()
    private var isFinishingIntentionally = false
    override fun init() {
        setContentView(R.layout.activity_explorer_pick)
        resultIntent.putExtra("volumeId", volumeId)
    }

    override fun bindFileOperationService() {
        //don't bind
    }

    override fun onExplorerElementClick(position: Int) {
        if (explorerAdapter.selectedItems.isEmpty()) {
            val fullPath = PathUtils.pathJoin(currentDirectoryPath, explorerElements[position].name)
            when {
                explorerElements[position].isDirectory -> {
                    setCurrentPath(fullPath)
                }
                explorerElements[position].isParentFolder -> {
                    setCurrentPath(PathUtils.getParentPath(currentDirectoryPath))
                }
                else -> {
                    resultIntent.putExtra("path", fullPath)
                    returnActivityResult()
                }
            }
        }
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explorer_pick, menu)
        val result = super.onCreateOptionsMenu(menu)
        val anyItemSelected = explorerAdapter.selectedItems.isNotEmpty()
        menu.findItem(R.id.select_all).isVisible = anyItemSelected
        menu.findItem(R.id.validate).isVisible = anyItemSelected
        return result
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
                    paths.add(PathUtils.pathJoin(currentDirectoryPath, e.name))
                    types.add(e.stat.type)
                }
                resultIntent.putStringArrayListExtra("paths", paths)
                resultIntent.putIntegerArrayListExtra("types", types)
                returnActivityResult()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun returnActivityResult() {
        setResult(Activity.RESULT_OK, resultIntent)
        isFinishingIntentionally = true
        finish()
    }
}