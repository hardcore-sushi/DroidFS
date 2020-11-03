package sushi.hardcore.droidfs.explorers

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import sushi.hardcore.droidfs.adapters.DialogSingleChoiceAdapter
import sushi.hardcore.droidfs.adapters.ExplorerElementAdapter
import sushi.hardcore.droidfs.adapters.OpenAsDialogAdapter
import sushi.hardcore.droidfs.file_viewers.AudioPlayer
import sushi.hardcore.droidfs.file_viewers.ImageViewer
import sushi.hardcore.droidfs.file_viewers.TextEditor
import sushi.hardcore.droidfs.file_viewers.VideoPlayer
import sushi.hardcore.droidfs.provider.RestrictedFileProvider
import sushi.hardcore.droidfs.util.ExternalProvider
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.LoadingTask
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.io.FileNotFoundException

open class BaseExplorerActivity : BaseActivity() {
    private lateinit var sortOrderEntries: Array<String>
    private lateinit var sortOrderValues: Array<String>
    private var currentSortOrderIndex = 0
    protected lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var volumeName: String
    private lateinit var explorerViewModel: ExplorerViewModel
    protected var currentDirectoryPath: String = ""
        set(value) {
            field = value
            explorerViewModel.currentDirectoryPath = value
        }
    protected lateinit var explorerElements: MutableList<ExplorerElement>
    protected lateinit var explorerAdapter: ExplorerElementAdapter
    private var isCreating = true
    protected var isStartingActivity = false
    private var usf_open = false
    protected var usf_keep_open = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usf_open = sharedPrefs.getBoolean("usf_open", false)
        usf_keep_open = sharedPrefs.getBoolean("usf_keep_open", false)
        val intent = intent
        volumeName = intent.getStringExtra("volume_name") ?: ""
        val sessionID = intent.getIntExtra("sessionID", -1)
        gocryptfsVolume = GocryptfsVolume(sessionID)
        sortOrderEntries = resources.getStringArray(R.array.sort_orders_entries)
        sortOrderValues = resources.getStringArray(R.array.sort_orders_values)
        currentSortOrderIndex = resources.getStringArray(R.array.sort_orders_values).indexOf(sharedPrefs.getString(ConstValues.sort_order_key, "name"))
        init()
        setSupportActionBar(toolbar)
        title = ""
        title_text.text = getString(R.string.volume, volumeName)
        explorerAdapter = ExplorerElementAdapter(this)
        explorerViewModel= ViewModelProvider(this).get(ExplorerViewModel::class.java)
        currentDirectoryPath = explorerViewModel.currentDirectoryPath
        setCurrentPath(currentDirectoryPath)
        list_explorer.adapter = explorerAdapter
        list_explorer.onItemClickListener = OnItemClickListener { _, _, position, _ -> onExplorerItemClick(position) }
        list_explorer.onItemLongClickListener = OnItemLongClickListener { _, _, position, _ -> onExplorerItemLongClick(position); true }
        refresher.setOnRefreshListener {
            setCurrentPath(currentDirectoryPath)
            refresher.isRefreshing = false
        }
    }

    class ExplorerViewModel: ViewModel() {
        var currentDirectoryPath = ""
    }

    protected open fun init() {
        setContentView(R.layout.activity_explorer_base)
    }

    private fun startFileViewer(cls: Class<*>, filePath: String, sortOrder: String = ""){
        val intent = Intent(this, cls)
        intent.putExtra("path", filePath)
        intent.putExtra("sessionID", gocryptfsVolume.sessionID)
        if (sortOrder.isNotEmpty()){
            intent.putExtra("sortOrder", sortOrder)
        }
        isStartingActivity = true
        startActivity(intent)
    }

    private fun openWithExternalApp(fullPath: String){
        isStartingActivity = true
        ExternalProvider.open(this, gocryptfsVolume, fullPath)
    }

    protected open fun onExplorerItemClick(position: Int) {
        val wasSelecting = explorerAdapter.selectedItems.isNotEmpty()
        explorerAdapter.onItemClick(position)
        if (explorerAdapter.selectedItems.isEmpty()) {
            if (!wasSelecting) {
                val fullPath = explorerElements[position].fullPath
                when {
                    explorerElements[position].isDirectory -> {
                        setCurrentPath(fullPath)
                    }
                    explorerElements[position].isParentFolder -> {
                        setCurrentPath(PathUtils.getParentPath(currentDirectoryPath))
                    }
                    isImage(fullPath) -> {
                        startFileViewer(ImageViewer::class.java, fullPath, sortOrderValues[currentSortOrderIndex])
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
                        val adapter = OpenAsDialogAdapter(this, usf_open)
                        ColoredAlertDialogBuilder(this)
                            .setSingleChoiceItems(adapter, -1){ dialog, which ->
                                when (adapter.getItem(which)){
                                    "image" -> startFileViewer(ImageViewer::class.java, fullPath)
                                    "video" -> startFileViewer(VideoPlayer::class.java, fullPath)
                                    "audio" -> startFileViewer(AudioPlayer::class.java, fullPath)
                                    "text" -> startFileViewer(TextEditor::class.java, fullPath)
                                    "external" -> if (usf_open){
                                        openWithExternalApp(fullPath)
                                    }
                                }
                                dialog.dismiss()
                            }
                            .setTitle(getString(R.string.open_as))
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                    }
                }
            }
        }
        invalidateOptionsMenu()
    }

    protected open fun onExplorerItemLongClick(position: Int) {
        explorerAdapter.onItemLongClick(position)
        invalidateOptionsMenu()
    }

    protected fun unselectAll(){
        explorerAdapter.unSelectAll()
        invalidateOptionsMenu()
    }

    private fun sortExplorerElements() {
        ExplorerElement.sortBy(sortOrderValues[currentSortOrderIndex], explorerElements)
        val sharedPrefsEditor = sharedPrefs.edit()
        sharedPrefsEditor.putString(ConstValues.sort_order_key, sortOrderValues[currentSortOrderIndex])
        sharedPrefsEditor.apply()
    }

    protected fun setCurrentPath(path: String) {
        explorerElements = gocryptfsVolume.listDir(path)
        text_dir_empty.visibility = if (explorerElements.size == 0) View.VISIBLE else View.INVISIBLE
        sortExplorerElements()
        if (path.isNotEmpty()) { //not root
            explorerElements.add(0, ExplorerElement("..", (-1).toShort(), -1, -1, currentDirectoryPath))
        }
        explorerAdapter.setExplorerElements(explorerElements)
        currentDirectoryPath = path
        current_path_text.text = getString(R.string.location, currentDirectoryPath)
        Thread{
            var totalSize: Long = 0
            for (element in explorerElements){
                if (element.isDirectory){
                    var dirSize: Long = 0
                    for (subFile in gocryptfsVolume.recursiveMapFiles(element.fullPath)){
                        if (subFile.isRegularFile){
                            dirSize += subFile.size
                        }
                    }
                    element.size = dirSize
                    totalSize += dirSize
                } else if (element.isRegularFile) {
                    totalSize += element.size
                }
            }
            runOnUiThread {
                total_size_text.text = getString(R.string.total_size, PathUtils.formatSize(totalSize))
                explorerAdapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun askCloseVolume() {
        ColoredAlertDialogBuilder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.ask_close_volume)
                .setPositiveButton(R.string.ok) { _, _ -> closeVolumeOnUserExit() }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun onBackPressed() {
        if (explorerAdapter.selectedItems.isEmpty()) {
            val parentPath = PathUtils.getParentPath(currentDirectoryPath)
            if (parentPath == currentDirectoryPath) {
                askCloseVolume()
            } else {
                setCurrentPath(PathUtils.getParentPath(currentDirectoryPath))
            }
        } else {
            unselectAll()
        }
    }

    private fun createFolder(folder_name: String){
        if (folder_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.mkdir(PathUtils.pathJoin(currentDirectoryPath, folder_name))) {
                ColoredAlertDialogBuilder(this)
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

    protected fun openDialogCreateFolder() {
        val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
        val dialog = ColoredAlertDialogBuilder(this)
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

    protected fun checkPathOverwrite(path: String, isDirectory: Boolean): String? {
        var outputPath: String? = null
        if (gocryptfsVolume.pathExists(path)){
            val fileName = File(path).name
            val handler = Handler{ msg ->
                outputPath = msg.obj as String?
                throw RuntimeException()
            }
            runOnUiThread {
                val dialog = ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.warning)
                    .setMessage(getString(if (isDirectory){R.string.dir_overwrite_question} else {R.string.file_overwrite_question}, path))
                    .setNegativeButton(R.string.no) { _, _ ->
                        val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
                        val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
                        dialogEditText.setText(fileName)
                        dialogEditText.selectAll()
                        val dialog = ColoredAlertDialogBuilder(this)
                            .setView(dialogEditTextView)
                            .setTitle(R.string.enter_new_name)
                            .setPositiveButton(R.string.ok) { _, _ ->
                                handler.sendMessage(Message().apply { obj = checkPathOverwrite(PathUtils.pathJoin(PathUtils.getParentPath(path), dialogEditText.text.toString()), isDirectory) })
                            }
                            .setNegativeButton(R.string.cancel) { _, _ -> handler.sendMessage(Message().apply { obj = null }) }
                            .create()
                        dialogEditText.setOnEditorActionListener { _, _, _ ->
                            dialog.dismiss()
                            handler.sendMessage(Message().apply { obj = checkPathOverwrite(PathUtils.pathJoin(PathUtils.getParentPath(path), dialogEditText.text.toString()), isDirectory) })
                            true
                        }
                        dialog.setOnCancelListener { handler.sendMessage(Message().apply { obj = null }) }
                        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                        dialog.show()
                    }
                    .setPositiveButton(R.string.yes) { _, _ -> handler.sendMessage(Message().apply { obj = path }) }
                    .create()
                dialog.setOnCancelListener { handler.sendMessage(Message().apply { obj = null }) }
                dialog.show()
            }
            try { Looper.loop() }
            catch (e: RuntimeException) {}
        } else {
            outputPath = path
        }
        return outputPath
    }

    protected fun importFilesFromUris(uris: List<Uri>, task: LoadingTask, callback: (DialogInterface.OnClickListener)? = null): Boolean {
        var success = false
        for (uri in uris) {
            val fileName = PathUtils.getFilenameFromURI(task.activity, uri)
            if (fileName == null){
                task.stopTask {
                    ColoredAlertDialogBuilder(task.activity)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.error_retrieving_filename, uri))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                }
                success = false
                break
            } else {
                val dstPath = checkPathOverwrite(PathUtils.pathJoin(currentDirectoryPath, fileName), false)
                if (dstPath == null){
                    break
                } else {
                    var message: String? = null
                    try {
                        success = gocryptfsVolume.importFile(task.activity, uri, dstPath)
                    } catch (e: FileNotFoundException){
                        message = if (e.message != null){
                            e.message!!+"\n"
                        } else {
                            ""
                        }
                    }
                    if (!success || message != null) {
                        task.stopTask {
                            ColoredAlertDialogBuilder(task.activity)
                                .setTitle(R.string.error)
                                .setMessage((message ?: "")+getString(R.string.import_failed, uri))
                                .setCancelable(callback == null)
                                .setPositiveButton(R.string.ok, callback)
                                .show()
                        }
                        break
                    }
                }
            }
        }
        return success
    }

    protected fun rename(old_name: String, new_name: String){
        if (new_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.rename(PathUtils.pathJoin(currentDirectoryPath, old_name), PathUtils.pathJoin(currentDirectoryPath, new_name))) {
                ColoredAlertDialogBuilder(this)
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

    protected fun handleMenuItems(menu: Menu){
        menu.findItem(R.id.rename).isVisible = false
        if (usf_open){
            menu.findItem(R.id.external_open)?.isVisible = false
        }
        val noItemSelected = explorerAdapter.selectedItems.isEmpty()
        menu.findItem(R.id.sort).isVisible = noItemSelected
        menu.findItem(R.id.close).isVisible = noItemSelected
        if (noItemSelected){
            toolbar.navigationIcon = null
        } else {
            toolbar.setNavigationIcon(R.drawable.icon_arrow_back)
            if (explorerAdapter.selectedItems.size == 1) {
                menu.findItem(R.id.rename).isVisible = true
                if (usf_open && explorerElements[explorerAdapter.selectedItems[0]].isRegularFile) {
                    menu.findItem(R.id.external_open)?.isVisible = true
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                unselectAll()
                true
            }
            R.id.sort -> {
                ColoredAlertDialogBuilder(this)
                        .setTitle(R.string.sort_order)
                        .setSingleChoiceItems(DialogSingleChoiceAdapter(this, sortOrderEntries), currentSortOrderIndex) { dialog, which ->
                            currentSortOrderIndex = which
                            setCurrentPath(currentDirectoryPath)
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .show()
                true
            }
            R.id.rename -> {
                val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
                val oldName = explorerElements[explorerAdapter.selectedItems[0]].name
                val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
                dialogEditText.setText(oldName)
                dialogEditText.selectAll()
                val dialog = ColoredAlertDialogBuilder(this)
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
            R.id.external_open -> {
                if (usf_open){
                    openWithExternalApp(PathUtils.pathJoin(currentDirectoryPath, explorerElements[explorerAdapter.selectedItems[0]].name))
                    unselectAll()
                }
                true
            }
            R.id.close -> {
                askCloseVolume()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    protected open fun closeVolumeOnUserExit() {
        finish()
    }

    protected open fun closeVolumeOnDestroy() {
        if (!gocryptfsVolume.isClosed()){
            gocryptfsVolume.close()
        }
        RestrictedFileProvider.wipeAll(this) //additional security
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) { //activity won't be recreated
            closeVolumeOnDestroy()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations){
            if (isStartingActivity){
                isStartingActivity = false
            } else if (!usf_keep_open){
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isCreating){
            isCreating = false
        } else {
            if (gocryptfsVolume.isClosed()){
                finish()
            } else {
                isStartingActivity = false
                ExternalProvider.removeFiles(this)
            }
        }
    }
}
