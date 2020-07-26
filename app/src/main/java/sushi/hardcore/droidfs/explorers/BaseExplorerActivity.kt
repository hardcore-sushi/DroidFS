package sushi.hardcore.droidfs.explorers

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import com.github.clans.fab.FloatingActionMenu
import kotlinx.android.synthetic.main.activity_explorer_base.*
import kotlinx.android.synthetic.main.explorer_info_bar.*
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.ConstValues.Companion.isAudio
import sushi.hardcore.droidfs.ConstValues.Companion.isImage
import sushi.hardcore.droidfs.ConstValues.Companion.isText
import sushi.hardcore.droidfs.ConstValues.Companion.isVideo
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.adapters.OpenAsDialogAdapter
import sushi.hardcore.droidfs.adapters.ExplorerElementAdapter
import sushi.hardcore.droidfs.file_viewers.AudioPlayer
import sushi.hardcore.droidfs.file_viewers.ImageViewer
import sushi.hardcore.droidfs.file_viewers.TextEditor
import sushi.hardcore.droidfs.file_viewers.VideoPlayer
import sushi.hardcore.droidfs.provider.RestrictedFileProvider
import sushi.hardcore.droidfs.util.ExternalProvider
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.util.*

open class BaseExplorerActivity : BaseActivity() {
    private lateinit var sortModesEntries: Array<String>
    private lateinit var sortModesValues: Array<String>
    private var currentSortModeIndex = 0
    protected lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var volumeName: String
    protected var currentDirectoryPath = ""
    protected lateinit var explorerElements: MutableList<ExplorerElement>
    protected lateinit var explorerAdapter: ExplorerElementAdapter
    private var usf_open = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usf_open = sharedPrefs.getBoolean("usf_open", false)
        val intent = intent
        volumeName = intent.getStringExtra("volume_name") ?: ""
        val sessionID = intent.getIntExtra("sessionID", -1)
        gocryptfsVolume = GocryptfsVolume(sessionID)
        sortModesEntries = resources.getStringArray(R.array.sort_orders_entries)
        sortModesValues = resources.getStringArray(R.array.sort_orders_values)
        currentSortModeIndex = resources.getStringArray(R.array.sort_orders_values).indexOf(sharedPrefs.getString(ConstValues.sort_order_key, "name"))
        init()
        setSupportActionBar(toolbar)
        title = ""
        title_text.text = getString(R.string.volume, volumeName)
        explorerAdapter = ExplorerElementAdapter(this)
        setCurrentPath(currentDirectoryPath)
        list_explorer.adapter = explorerAdapter
        list_explorer.onItemClickListener = OnItemClickListener { _, _, position, _ -> onExplorerItemClick(position) }
        list_explorer.onItemLongClickListener = OnItemLongClickListener { _, _, position, _ ->
            explorerAdapter.onItemLongClick(position)
            invalidateOptionsMenu()
            true
        }
        refresher.setOnRefreshListener {
            setCurrentPath(currentDirectoryPath)
            refresher.isRefreshing = false
        }
    }

    protected open fun init() {
        setContentView(R.layout.activity_explorer_base)
    }

    private fun startFileViewer(cls: Class<*>, filePath: String){
        val intent = Intent(this, cls)
        intent.putExtra("path", filePath)
        intent.putExtra("sessionID", gocryptfsVolume.sessionID)
        startActivity(intent)
    }

    protected open fun onExplorerItemClick(position: Int) {
        val wasSelecting = explorerAdapter.selectedItems.isNotEmpty()
        explorerAdapter.onItemClick(position)
        if (explorerAdapter.selectedItems.isEmpty()) {
            if (!wasSelecting) {
                val fullPath = PathUtils.path_join(currentDirectoryPath, explorerElements[position].name)
                when {
                    explorerElements[position].isDirectory -> {
                        setCurrentPath(fullPath)
                    }
                    explorerElements[position].isParentFolder -> {
                        setCurrentPath(PathUtils.get_parent_path(currentDirectoryPath))
                    }
                    isImage(fullPath) -> {
                        startFileViewer(ImageViewer::class.java, fullPath)
                    }
                    isVideo(fullPath) -> {
                        startFileViewer(VideoPlayer::class.java, fullPath)
                    }
                    isText(fullPath) -> {
                        startFileViewer(TextEditor::class.java, fullPath)
                    }
                    isAudio(fullPath) -> {
                        startFileViewer(AudioPlayer::class.java, fullPath)
                    }
                    else -> {
                        val dialogListView = layoutInflater.inflate(R.layout.dialog_listview, null)
                        val listView = dialogListView.findViewById<ListView>(R.id.listview)
                        val adapter = OpenAsDialogAdapter(this)
                        listView.adapter = adapter
                        val dialog = ColoredAlertDialog(this)
                            .setView(dialogListView)
                            .setTitle(getString(R.string.open_as))
                            .setNegativeButton(R.string.cancel, null)
                            .create()
                        listView.setOnItemClickListener{_, _, fileTypePosition, _ ->
                            when (adapter.getItem(fileTypePosition)){
                                "image" -> startFileViewer(ImageViewer::class.java, fullPath)
                                "video" -> startFileViewer(VideoPlayer::class.java, fullPath)
                                "audio" -> startFileViewer(AudioPlayer::class.java, fullPath)
                                "text" -> startFileViewer(TextEditor::class.java, fullPath)
                            }
                            dialog.dismiss()
                        }
                        dialog.show()
                    }
                }
            }
        }
        invalidateOptionsMenu()
    }

    private fun sortExplorerElements() {
        when (sortModesValues[currentSortModeIndex]) {
            "name" -> {
                explorerElements.sortWith(Comparator { o1, o2 -> o1.name.compareTo(o2.name) })
            }
            "size" -> {
                explorerElements.sortWith(Comparator { o1, o2 -> (o1.size - o2.size).toInt() })
            }
            "date" -> {
                explorerElements.sortWith(Comparator { o1, o2 -> o1.mTime.compareTo(o2.mTime) })
            }
            "name_desc" -> {
                explorerElements.sortWith(Comparator { o1, o2 -> o2.name.compareTo(o1.name) })
            }
            "size_desc" -> {
                explorerElements.sortWith(Comparator { o1, o2 -> (o2.size - o1.size).toInt() })
            }
            "date_desc" -> {
                explorerElements.sortWith(Comparator { o1, o2 -> o2.mTime.compareTo(o1.mTime) })
            }
        }
        val sharedPrefsEditor = sharedPrefs.edit()
        sharedPrefsEditor.putString(ConstValues.sort_order_key, sortModesValues[currentSortModeIndex])
        sharedPrefsEditor.apply()
    }

    protected fun setCurrentPath(path: String) {
        explorerElements = gocryptfsVolume.list_dir(path)
        text_dir_empty.visibility = if (explorerElements.size == 0) View.VISIBLE else View.INVISIBLE
        sortExplorerElements()
        if (path.isNotEmpty()) { //not root
            explorerElements.add(0, ExplorerElement("..", (-1).toShort(), -1, -1))
        }
        explorerAdapter.setExplorerElements(explorerElements)
        currentDirectoryPath = path
        current_path_text.text = getString(R.string.location, currentDirectoryPath)
        total_size_text.text = getString(R.string.total_size, PathUtils.formatSize(explorerAdapter.currentDirectoryTotalSize))
    }

    private fun askCloseVolume() {
        ColoredAlertDialog(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.ask_close_volume)
                .setPositiveButton(R.string.ok) { _, _ -> closeVolumeOnUserExit() }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    protected open fun closeVolumeOnUserExit() {
        finish()
    }

    protected open fun closeVolumeOnDestroy() {
        gocryptfsVolume.close()
        RestrictedFileProvider.wipeAll() //additional security
    }

    override fun onBackPressed() {
        if (explorerAdapter.selectedItems.isEmpty()) {
            val parentPath = PathUtils.get_parent_path(currentDirectoryPath)
            if (parentPath == currentDirectoryPath) {
                askCloseVolume()
            } else {
                setCurrentPath(PathUtils.get_parent_path(currentDirectoryPath))
            }
        } else {
            explorerAdapter.unSelectAll()
            invalidateOptionsMenu()
        }
    }

    private fun createFolder(folder_name: String){
        if (folder_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.mkdir(PathUtils.path_join(currentDirectoryPath, folder_name))) {
                ColoredAlertDialog(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_mkdir)
                        .setPositiveButton(R.string.ok, null)
                        .show()
            } else {
                setCurrentPath(currentDirectoryPath)
                invalidateOptionsMenu()
            }
        }
    }

    fun onClickAddFolder(view: View?) {
        findViewById<FloatingActionMenu>(R.id.fam_explorer).close(true)
        val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
        val dialog = ColoredAlertDialog(this)
                .setView(dialogEditTextView)
                .setTitle(R.string.enter_folder_name)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val folderName = dialogEditText.text.toString()
                    createFolder(folderName)
                }
                .setNegativeButton(R.string.cancel, null)
                .create()
        dialogEditText.setOnEditorActionListener { _, _, _ ->
            val folderName = dialogEditText.text.toString()
            dialog.dismiss()
            createFolder(folderName)
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    fun rename(old_name: String, new_name: String){
        if (new_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.rename(PathUtils.path_join(currentDirectoryPath, old_name), PathUtils.path_join(currentDirectoryPath, new_name))) {
                ColoredAlertDialog(this)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.rename_failed, old_name))
                        .setPositiveButton(R.string.ok, null)
                        .show()
            } else {
                setCurrentPath(currentDirectoryPath)
                invalidateOptionsMenu()
            }
        }
    }

    fun handleMenuItems(menu: Menu){
        menu.findItem(R.id.explorer_menu_rename).isVisible = false
        if (usf_open){
            menu.findItem(R.id.explorer_menu_external_open)?.isVisible = false
        }
        val selectedItems = explorerAdapter.selectedItems
        if (selectedItems.isEmpty()){
            toolbar.navigationIcon = null
            menu.findItem(R.id.explorer_menu_close).isVisible = true
            menu.findItem(R.id.explorer_menu_sort).isVisible = true
        } else {
            toolbar.setNavigationIcon(R.drawable.icon_arrow_back)
            menu.findItem(R.id.explorer_menu_close).isVisible = false
            menu.findItem(R.id.explorer_menu_sort).isVisible = false
            if (selectedItems.size == 1) {
                menu.findItem(R.id.explorer_menu_rename).isVisible = true
                if (usf_open && explorerElements[selectedItems[0]].isRegularFile) {
                    menu.findItem(R.id.explorer_menu_external_open)?.isVisible = true
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                explorerAdapter.unSelectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.explorer_menu_sort -> {
                ColoredAlertDialog(this)
                        .setTitle(R.string.sort_order)
                        .setSingleChoiceItems(sortModesEntries, currentSortModeIndex) { dialog, which ->
                            currentSortModeIndex = which
                            setCurrentPath(currentDirectoryPath)
                            dialog.dismiss()
                        }.show()
                true
            }
            R.id.explorer_menu_rename -> {
                val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
                val oldName = explorerElements[explorerAdapter.selectedItems[0]].name
                val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
                dialogEditText.setText(oldName)
                dialogEditText.selectAll()
                val dialog = ColoredAlertDialog(this)
                        .setView(dialogEditTextView)
                        .setTitle(R.string.rename_title)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            val newName = dialogEditText.text.toString()
                            rename(oldName, newName)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                dialogEditText.setOnEditorActionListener { _, _, _ ->
                    val newName = dialogEditText.text.toString()
                    dialog.dismiss()
                    rename(oldName, newName)
                    true
                }
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                dialog.show()
                true
            }
            R.id.explorer_menu_external_open -> {
                if (usf_open){
                    ExternalProvider.open(this, gocryptfsVolume, PathUtils.path_join(currentDirectoryPath, explorerElements[explorerAdapter.selectedItems[0]].name))
                    explorerAdapter.unSelectAll()
                    invalidateOptionsMenu()
                }
                true
            }
            R.id.explorer_menu_close -> {
                askCloseVolume()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) { //activity won't be recreated
            closeVolumeOnDestroy()
        }
    }

    override fun onResume() {
        super.onResume()
        ExternalProvider.removeFiles(this)
    }
}