package sushi.hardcore.droidfs.explorers

import android.content.Intent
import android.content.SharedPreferences
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
import kotlinx.android.synthetic.main.activity_explorer_ro.*
import kotlinx.android.synthetic.main.explorer_info_bar.*
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.ColoredActivity
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
import sushi.hardcore.droidfs.provider.TemporaryFileProvider
import sushi.hardcore.droidfs.util.ExternalProvider
import sushi.hardcore.droidfs.util.FilesUtils
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.util.*

open class ExplorerActivityRO : ColoredActivity() {
    private lateinit var shared_prefs_editor: SharedPreferences.Editor
    private lateinit var sort_modes_entries: Array<String>
    private lateinit var sort_modes_values: Array<String>
    private var current_sort_mode_index = 0
    protected lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var volume_name: String
    protected var current_path = ""
    protected lateinit var explorer_elements: MutableList<ExplorerElement>
    protected lateinit var explorer_adapter: ExplorerElementAdapter
    private var usf_open = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!sharedPrefs.getBoolean("usf_screenshot", false)){
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        usf_open = sharedPrefs.getBoolean("usf_open", false)
        val intent = intent
        volume_name = intent.getStringExtra("volume_name")
        val sessionID = intent.getIntExtra("sessionID", -1)
        gocryptfsVolume = GocryptfsVolume(sessionID)
        sort_modes_entries = resources.getStringArray(R.array.sort_orders_entries)
        sort_modes_values = resources.getStringArray(R.array.sort_orders_values)
        current_sort_mode_index = resources.getStringArray(R.array.sort_orders_values).indexOf(sharedPrefs.getString(ConstValues.sort_order_key, "name"))
        shared_prefs_editor = sharedPrefs.edit()
        init()
        setSupportActionBar(toolbar)
        title = ""
        title_text.text = getString(R.string.volume, volume_name)
        explorer_adapter = ExplorerElementAdapter(this)
        setCurrentPath(current_path)
        list_explorer.adapter = explorer_adapter
        list_explorer.onItemClickListener = OnItemClickListener { _, _, position, _ -> onExplorerItemClick(position) }
        list_explorer.onItemLongClickListener = OnItemLongClickListener { _, _, position, _ ->
            explorer_adapter.onItemLongClick(position)
            invalidateOptionsMenu()
            true
        }
        refresher.setOnRefreshListener {
            setCurrentPath(current_path)
            refresher.isRefreshing = false
        }
    }

    protected open fun init() {
        setContentView(R.layout.activity_explorer_ro)
    }

    private fun startFileViewer(cls: Class<*>, filePath: String){
        val intent = Intent(this, cls)
        intent.putExtra("path", filePath)
        intent.putExtra("sessionID", gocryptfsVolume.sessionID)
        startActivity(intent)
    }

    protected open fun onExplorerItemClick(position: Int) {
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
                    isImage(full_path) -> {
                        startFileViewer(ImageViewer::class.java, full_path)
                    }
                    isVideo(full_path) -> {
                        startFileViewer(VideoPlayer::class.java, full_path)
                    }
                    isText(full_path) -> {
                        startFileViewer(TextEditor::class.java, full_path)
                    }
                    isAudio(full_path) -> {
                        startFileViewer(AudioPlayer::class.java, full_path)
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
                                "image" -> startFileViewer(ImageViewer::class.java, full_path)
                                "video" -> startFileViewer(VideoPlayer::class.java, full_path)
                                "audio" -> startFileViewer(AudioPlayer::class.java, full_path)
                                "text" -> startFileViewer(TextEditor::class.java, full_path)
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

    private fun sort_explorer_elements() {
        when (sort_modes_values[current_sort_mode_index]) {
            "name" -> {
                explorer_elements.sortWith(Comparator { o1, o2 -> o1.name.compareTo(o2.name) })
            }
            "size" -> {
                explorer_elements.sortWith(Comparator { o1, o2 -> (o1.size - o2.size).toInt() })
            }
            "date" -> {
                explorer_elements.sortWith(Comparator { o1, o2 -> o1.mTime.compareTo(o2.mTime) })
            }
            "name_desc" -> {
                explorer_elements.sortWith(Comparator { o1, o2 -> o2.name.compareTo(o1.name) })
            }
            "size_desc" -> {
                explorer_elements.sortWith(Comparator { o1, o2 -> (o2.size - o1.size).toInt() })
            }
            "date_desc" -> {
                explorer_elements.sortWith(Comparator { o1, o2 -> o2.mTime.compareTo(o1.mTime) })
            }
        }
        shared_prefs_editor.putString(ConstValues.sort_order_key, sort_modes_values[current_sort_mode_index])
        shared_prefs_editor.apply()
    }

    protected fun setCurrentPath(path: String) {
        explorer_elements = gocryptfsVolume.list_dir(path)
        text_dir_empty.visibility = if (explorer_elements.size == 0) View.VISIBLE else View.INVISIBLE
        sort_explorer_elements()
        if (path.isNotEmpty()) { //not root
            explorer_elements.add(0, ExplorerElement("..", (-1).toShort(), -1, -1))
        }
        explorer_adapter.setExplorerElements(explorer_elements)
        current_path = path
        current_path_text.text = getString(R.string.location, current_path)
        total_size_text.text = getString(R.string.total_size, FilesUtils.formatSize(explorer_adapter.currentDirectoryTotalSize))
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
        TemporaryFileProvider.wipeAll() //additional security
    }

    override fun onBackPressed() {
        if (explorer_adapter.selectedItems.isEmpty()) {
            val parent_path = FilesUtils.get_parent_path(current_path)
            if (parent_path == current_path) {
                askCloseVolume()
            } else {
                setCurrentPath(FilesUtils.get_parent_path(current_path))
            }
        } else {
            explorer_adapter.unSelectAll()
            invalidateOptionsMenu()
        }
    }

    fun createFolder(folder_name: String){
        if (folder_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.mkdir(FilesUtils.path_join(current_path, folder_name))) {
                ColoredAlertDialog(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_mkdir)
                        .setPositiveButton(R.string.ok, null)
                        .show()
            } else {
                setCurrentPath(current_path)
                invalidateOptionsMenu()
            }
        }
    }

    fun onClickAddFolder(view: View?) {
        findViewById<FloatingActionMenu>(R.id.fam_explorer).close(true)
        val dialog_edit_text_view = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val dialog_edit_text = dialog_edit_text_view.findViewById<EditText>(R.id.dialog_edit_text)
        val dialog = ColoredAlertDialog(this)
                .setView(dialog_edit_text_view)
                .setTitle(R.string.enter_folder_name)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val folder_name = dialog_edit_text.text.toString()
                    createFolder(folder_name)
                }
                .setNegativeButton(R.string.cancel, null)
                .create()
        dialog_edit_text.setOnEditorActionListener { _, _, _ ->
            val folder_name = dialog_edit_text.text.toString()
            dialog.dismiss()
            createFolder(folder_name)
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    fun rename(old_name: String, new_name: String){
        if (new_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.rename(FilesUtils.path_join(current_path, old_name), FilesUtils.path_join(current_path, new_name))) {
                ColoredAlertDialog(this)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.rename_failed, old_name))
                        .setPositiveButton(R.string.ok, null)
                        .show()
            } else {
                setCurrentPath(current_path)
                invalidateOptionsMenu()
            }
        }
    }

    fun handle_menu_items(menu: Menu){
        menu.findItem(R.id.explorer_menu_rename).isVisible = false
        if (usf_open){
            menu.findItem(R.id.explorer_menu_external_open)?.isVisible = false
        }
        val selectedItems = explorer_adapter.selectedItems
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
                if (usf_open && explorer_elements[selectedItems[0]].isRegularFile) {
                    menu.findItem(R.id.explorer_menu_external_open)?.isVisible = true
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                explorer_adapter.unSelectAll()
                invalidateOptionsMenu()
                true
            }
            R.id.explorer_menu_sort -> {
                ColoredAlertDialog(this)
                        .setTitle(R.string.sort_order)
                        .setSingleChoiceItems(sort_modes_entries, current_sort_mode_index) { dialog, which ->
                            current_sort_mode_index = which
                            setCurrentPath(current_path)
                            dialog.dismiss()
                        }.show()
                true
            }
            R.id.explorer_menu_rename -> {
                val dialog_edit_text_view = layoutInflater.inflate(R.layout.dialog_edit_text, null)
                val old_name = explorer_elements[explorer_adapter.selectedItems[0]].name
                val dialog_edit_text = dialog_edit_text_view.findViewById<EditText>(R.id.dialog_edit_text)
                dialog_edit_text.setText(old_name)
                dialog_edit_text.selectAll()
                val dialog = ColoredAlertDialog(this)
                        .setView(dialog_edit_text_view)
                        .setTitle(R.string.rename_title)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            val new_name = dialog_edit_text.text.toString()
                            rename(old_name, new_name)
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .create()
                dialog_edit_text.setOnEditorActionListener { _, _, _ ->
                    val new_name = dialog_edit_text.text.toString()
                    dialog.dismiss()
                    rename(old_name, new_name)
                    true
                }
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                dialog.show()
                true
            }
            R.id.explorer_menu_external_open -> {
                if (usf_open){
                    ExternalProvider.open(this, gocryptfsVolume, FilesUtils.path_join(current_path, explorer_elements[explorer_adapter.selectedItems[0]].name))
                    explorer_adapter.unSelectAll()
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
        ExternalProvider.clear_cache(this)
    }
}