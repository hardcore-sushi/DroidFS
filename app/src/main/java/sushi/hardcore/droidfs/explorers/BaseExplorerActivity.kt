package sushi.hardcore.droidfs.explorers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.ConstValues.isAudio
import sushi.hardcore.droidfs.ConstValues.isImage
import sushi.hardcore.droidfs.ConstValues.isPDF
import sushi.hardcore.droidfs.ConstValues.isText
import sushi.hardcore.droidfs.ConstValues.isVideo
import sushi.hardcore.droidfs.GocryptfsVolume
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.adapters.ExplorerElementAdapter
import sushi.hardcore.droidfs.adapters.OpenAsDialogAdapter
import sushi.hardcore.droidfs.content_providers.ExternalProvider
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.file_operations.FileOperationService
import sushi.hardcore.droidfs.file_operations.OperationFile
import sushi.hardcore.droidfs.file_viewers.*
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog

open class BaseExplorerActivity : BaseActivity(), ExplorerElementAdapter.Listener {
    private lateinit var sortOrderEntries: Array<String>
    private lateinit var sortOrderValues: Array<String>
    private var foldersFirst = true
    private var mapFolders = true
    private var currentSortOrderIndex = 0
    protected lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var volumeName: String
    private lateinit var explorerViewModel: ExplorerViewModel
    protected var currentDirectoryPath: String = ""
        set(value) {
            field = value
            explorerViewModel.currentDirectoryPath = value
        }
    protected lateinit var fileOperationService: FileOperationService
    protected lateinit var explorerElements: MutableList<ExplorerElement>
    protected lateinit var explorerAdapter: ExplorerElementAdapter
    private var isCreating = true
    protected var isStartingActivity = false
    private var usf_open = false
    protected var usf_keep_open = false
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var isUsingListLayout = true
    private lateinit var layoutIcon: ImageButton
    private lateinit var titleText: TextView
    private lateinit var recycler_view_explorer: RecyclerView
    private lateinit var refresher: SwipeRefreshLayout
    private lateinit var textDirEmpty: TextView
    private lateinit var currentPathText: TextView
    private lateinit var numberOfFilesText: TextView
    private lateinit var numberOfFoldersText: TextView
    private lateinit var totalSizeText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usf_open = sharedPrefs.getBoolean("usf_open", false)
        usf_keep_open = sharedPrefs.getBoolean("usf_keep_open", false)
        volumeName = intent.getStringExtra("volume_name") ?: ""
        val sessionID = intent.getIntExtra("sessionID", -1)
        gocryptfsVolume = GocryptfsVolume(applicationContext, sessionID)
        sortOrderEntries = resources.getStringArray(R.array.sort_orders_entries)
        sortOrderValues = resources.getStringArray(R.array.sort_orders_values)
        foldersFirst = sharedPrefs.getBoolean("folders_first", true)
        mapFolders = sharedPrefs.getBoolean("map_folders", true)
        currentSortOrderIndex = resources.getStringArray(R.array.sort_orders_values).indexOf(sharedPrefs.getString(ConstValues.SORT_ORDER_KEY, "name"))
        init()
        recycler_view_explorer = findViewById(R.id.recycler_view_explorer)
        refresher = findViewById(R.id.refresher)
        textDirEmpty = findViewById(R.id.text_dir_empty)
        currentPathText = findViewById(R.id.current_path_text)
        numberOfFilesText = findViewById(R.id.number_of_files_text)
        numberOfFoldersText = findViewById(R.id.number_of_folders_text)
        totalSizeText = findViewById(R.id.total_size_text)
        supportActionBar?.apply {
            setDisplayShowCustomEnabled(true)
            setCustomView(R.layout.action_bar)
            titleText = customView.findViewById(R.id.title_text)
        }
        title = ""
        setVolumeNameTitle()
        explorerAdapter = ExplorerElementAdapter(
            this,
            if (sharedPrefs.getBoolean("thumbnails", true)) {
                gocryptfsVolume
            } else {
                null
            },
            this,
            sharedPrefs.getLong(ConstValues.THUMBNAIL_MAX_SIZE_KEY, ConstValues.DEFAULT_THUMBNAIL_MAX_SIZE)*1000,
        )
        explorerViewModel = ViewModelProvider(this).get(ExplorerViewModel::class.java)
        currentDirectoryPath = explorerViewModel.currentDirectoryPath
        setCurrentPath(currentDirectoryPath)
        linearLayoutManager = LinearLayoutManager(this@BaseExplorerActivity)
        recycler_view_explorer.adapter = explorerAdapter
        isUsingListLayout = sharedPrefs.getBoolean("useListLayout", true)
        layoutIcon = findViewById(R.id.layout_icon)
        setRecyclerViewLayout()
        layoutIcon.setOnClickListener {
            isUsingListLayout = !isUsingListLayout
            setRecyclerViewLayout()
            recycler_view_explorer.recycledViewPool.clear()
            with (sharedPrefs.edit()) {
                putBoolean("useListLayout", isUsingListLayout)
                apply()
            }
        }
        refresher.setOnRefreshListener {
            setCurrentPath(currentDirectoryPath)
            refresher.isRefreshing = false
        }
        bindFileOperationService()
    }

    class ExplorerViewModel: ViewModel() {
        var currentDirectoryPath = ""
    }

    private fun setRecyclerViewLayout() {
        layoutIcon.setImageResource(if (isUsingListLayout) {
            recycler_view_explorer.layoutManager = linearLayoutManager
            explorerAdapter.isUsingListLayout = true
            R.drawable.icon_view_grid
        } else {
            val displayMetrics = resources.displayMetrics
            val columnsNumber = (displayMetrics.widthPixels / displayMetrics.density / 200 + 0.5).toInt()
            recycler_view_explorer.layoutManager = GridLayoutManager(this, columnsNumber)
            explorerAdapter.isUsingListLayout = false
            R.drawable.icon_view_list
        })
    }

    protected open fun init() {
        setContentView(R.layout.activity_explorer_base)
    }

    protected open fun bindFileOperationService(){
        Intent(this, FileOperationService::class.java).also {
            bindService(it, object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    val binder = service as FileOperationService.LocalBinder
                    fileOperationService = binder.getService()
                    binder.setGocryptfsVolume(gocryptfsVolume)
                }
                override fun onServiceDisconnected(arg0: ComponentName) {

                }
            }, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startFileViewer(cls: Class<*>, filePath: String){
        val intent = Intent(this, cls).apply {
            putExtra("path", filePath)
            putExtra("sessionID", gocryptfsVolume.sessionID)
            putExtra("sortOrder", sortOrderValues[currentSortOrderIndex])
        }
        isStartingActivity = true
        startActivity(intent)
    }

    private fun openWithExternalApp(fullPath: String){
        isStartingActivity = true
        ExternalProvider.open(this, themeValue, gocryptfsVolume, fullPath)
    }

    private fun showOpenAsDialog(path: String) {
        val adapter = OpenAsDialogAdapter(this, usf_open)
        CustomAlertDialogBuilder(this, themeValue)
            .setSingleChoiceItems(adapter, -1) { dialog, which ->
                when (adapter.getItem(which)) {
                    "image" -> startFileViewer(ImageViewer::class.java, path)
                    "video" -> startFileViewer(VideoPlayer::class.java, path)
                    "audio" -> startFileViewer(AudioPlayer::class.java, path)
                    "pdf" -> startFileViewer(PdfViewer::class.java, path)
                    "text" -> startFileViewer(TextEditor::class.java, path)
                    "external" -> if (usf_open) {
                        openWithExternalApp(path)
                    }
                }
                dialog.dismiss()
            }
            .setTitle(getString(R.string.open_as) + ':')
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setVolumeNameTitle() {
        titleText.text = getString(R.string.volume, volumeName)
    }

    override fun onSelectionChanged(size: Int) {
        if (size == 0) {
            setVolumeNameTitle()
        } else {
            titleText.text = getString(R.string.elements_selected, size, explorerElements.count { !it.isParentFolder })
        }
    }

    override fun onExplorerElementClick(position: Int) {
        if (explorerAdapter.selectedItems.isEmpty()) {
            val fullPath = explorerElements[position].fullPath
            when {
                explorerElements[position].isDirectory -> {
                    setCurrentPath(fullPath)
                }
                explorerElements[position].isParentFolder -> {
                    setCurrentPath(PathUtils.getParentPath(currentDirectoryPath))
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
                isPDF(fullPath) -> {
                    startFileViewer(PdfViewer::class.java, fullPath)
                }
                isAudio(fullPath) -> {
                    startFileViewer(AudioPlayer::class.java, fullPath)
                }
                else -> showOpenAsDialog(fullPath)
            }
        }
        invalidateOptionsMenu()
    }

    override fun onExplorerElementLongClick(position: Int) {
        invalidateOptionsMenu()
    }

    protected fun unselectAll(notifyChange: Boolean = true) {
        explorerAdapter.unSelectAll(notifyChange)
        invalidateOptionsMenu()
    }

    private fun displayExplorerElements(totalSize: Long) {
        totalSizeText.text = getString(R.string.total_size, PathUtils.formatSize(totalSize))
        synchronized(this) {
            ExplorerElement.sortBy(sortOrderValues[currentSortOrderIndex], foldersFirst, explorerElements)
        }
        unselectAll(false)
        explorerAdapter.explorerElements = explorerElements
        val sharedPrefsEditor = sharedPrefs.edit()
        sharedPrefsEditor.putString(ConstValues.SORT_ORDER_KEY, sortOrderValues[currentSortOrderIndex])
        sharedPrefsEditor.apply()
    }

    private fun recursiveSetSize(directory: ExplorerElement) {
        for (child in gocryptfsVolume.listDir(directory.fullPath)) {
            if (child.isDirectory) {
                recursiveSetSize(child)
            }
            directory.size += child.size
        }
    }

    private fun displayNumberOfElements(textView: TextView, stringIdSingular: Int, stringIdPlural: Int, count: Int) {
        with(textView) {
            visibility = if (count == 0) {
                View.GONE
            } else {
                text = if (count == 1) {
                    getString(stringIdSingular)
                } else {
                    getString(stringIdPlural, count)
                }
                View.VISIBLE
            }
        }
    }

    protected fun setCurrentPath(path: String, onDisplayed: (() -> Unit)? = null) {
        synchronized(this) {
            explorerElements = gocryptfsVolume.listDir(path)
            if (path.isNotEmpty()) { //not root
                explorerElements.add(
                    0,
                    ExplorerElement("..", (-1).toShort(), parentPath = currentDirectoryPath)
                )
            }
        }
        textDirEmpty.visibility = if (explorerElements.size == 0) View.VISIBLE else View.GONE
        currentDirectoryPath = path
        currentPathText.text = getString(R.string.location, currentDirectoryPath)
        displayNumberOfElements(numberOfFilesText, R.string.one_file, R.string.multiple_files, explorerElements.count { it.isRegularFile })
        displayNumberOfElements(numberOfFoldersText, R.string.one_folder, R.string.multiple_folders, explorerElements.count { it.isDirectory })
        if (mapFolders) {
            Thread {
                var totalSize: Long = 0
                synchronized(this) {
                    for (element in explorerElements) {
                        if (element.isDirectory) {
                            recursiveSetSize(element)
                        }
                        totalSize += element.size
                    }
                }
                runOnUiThread {
                    displayExplorerElements(totalSize)
                    onDisplayed?.invoke()
                }
            }.start()
        } else {
            displayExplorerElements(explorerElements.filter { !it.isParentFolder }.sumOf { it.size })
            onDisplayed?.invoke()
        }
    }

    private fun askCloseVolume() {
        CustomAlertDialogBuilder(this, themeValue)
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

    private fun createFolder(folderName: String){
        if (folderName.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.mkdir(PathUtils.pathJoin(currentDirectoryPath, folderName))) {
                CustomAlertDialogBuilder(this, themeValue)
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
        EditTextDialog(this, R.string.enter_folder_name) {
            createFolder(it)
        }.show()
    }

    protected fun checkPathOverwrite(items: ArrayList<OperationFile>, dstDirectoryPath: String, callback: (ArrayList<OperationFile>?) -> Unit) {
        val srcDirectoryPath = items[0].explorerElement.parentPath
        var ready = true
        for (i in 0 until items.size) {
            val testDstPath: String
            if (items[i].dstPath == null){
                testDstPath = PathUtils.pathJoin(dstDirectoryPath, PathUtils.getRelativePath(srcDirectoryPath, items[i].explorerElement.fullPath))
                if (gocryptfsVolume.pathExists(testDstPath)){
                    ready = false
                } else {
                    items[i].dstPath = testDstPath
                }
            } else {
                testDstPath = items[i].dstPath!!
                if (gocryptfsVolume.pathExists(testDstPath) && !items[i].overwriteConfirmed){
                    ready = false
                }
            }
            if (!ready){
                CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.warning)
                    .setMessage(getString(if (items[i].explorerElement.isDirectory){R.string.dir_overwrite_question} else {R.string.file_overwrite_question}, testDstPath))
                    .setPositiveButton(R.string.yes) {_, _ ->
                        items[i].dstPath = testDstPath
                        items[i].overwriteConfirmed = true
                        checkPathOverwrite(items, dstDirectoryPath, callback)
                    }
                    .setNegativeButton(R.string.no) { _, _ ->
                        with(EditTextDialog(this, R.string.enter_new_name) {
                            items[i].dstPath = PathUtils.pathJoin(dstDirectoryPath, PathUtils.getRelativePath(srcDirectoryPath, items[i].explorerElement.parentPath), it)
                            if (items[i].explorerElement.isDirectory){
                                for (j in 0 until items.size){
                                    if (PathUtils.isChildOf(items[j].explorerElement.fullPath, items[i].explorerElement.fullPath)){
                                        items[j].dstPath = PathUtils.pathJoin(items[i].dstPath!!, PathUtils.getRelativePath(items[i].explorerElement.fullPath, items[j].explorerElement.fullPath))
                                    }
                                }
                            }
                            checkPathOverwrite(items, dstDirectoryPath, callback)
                        }) {
                            with (binding.dialogEditText) {
                                setText(items[i].explorerElement.name)
                                selectAll()
                            }
                            setOnCancelListener{
                                callback(null)
                            }
                            show()
                        }
                    }
                    .setOnCancelListener{
                        callback(null)
                    }
                    .show()
                break
            }
        }
        if (ready){
            callback(items)
        }
    }

    protected fun importFilesFromUris(uris: List<Uri>, callback: (String?) -> Unit) {
        val items = ArrayList<OperationFile>()
        for (uri in uris) {
            val fileName = PathUtils.getFilenameFromURI(this, uri)
            if (fileName == null) {
                CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.error_retrieving_filename, uri))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                items.clear()
                break
            } else {
                items.add(OperationFile.fromExplorerElement(ExplorerElement(fileName, 1, parentPath = currentDirectoryPath)))
            }
        }
        if (items.size > 0) {
            checkPathOverwrite(items, currentDirectoryPath) { checkedItems ->
                checkedItems?.let {
                    fileOperationService.importFilesFromUris(checkedItems.map { it.dstPath!! }, uris){ failedItem ->
                        runOnUiThread {
                            callback(failedItem)
                        }
                    }
                }
            }
        }
    }

    fun importDirectory(sourceUri: Uri, callback: (String?, List<Uri>, DocumentFile) -> Unit) {
        val tree = DocumentFile.fromTreeUri(this, sourceUri)!! //non-null after Lollipop
        val operation = OperationFile.fromExplorerElement(ExplorerElement(tree.name!!, 0, parentPath = currentDirectoryPath))
        checkPathOverwrite(arrayListOf(operation), currentDirectoryPath) { checkedOperation ->
            checkedOperation?.let {
                fileOperationService.importDirectory(checkedOperation[0].dstPath!!, tree) { failedItem, uris ->
                    runOnUiThread {
                        callback(failedItem, uris, tree)
                    }
                }
            }
        }
    }

    protected fun rename(old_name: String, new_name: String){
        if (new_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!gocryptfsVolume.rename(PathUtils.pathJoin(currentDirectoryPath, old_name), PathUtils.pathJoin(currentDirectoryPath, new_name))) {
                CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.rename_failed, old_name))
                        .setPositiveButton(R.string.ok, null)
                        .show()
            } else {
                setCurrentPath(currentDirectoryPath) {
                    invalidateOptionsMenu()
                }
            }
        }
    }

    private fun setMenuIconTint(menu: Menu, iconColor: Int, menuItemId: Int, drawableId: Int) {
        menu.findItem(menuItemId)?.let {
            it.icon = ContextCompat.getDrawable(this, drawableId)?.apply {
                setTint(iconColor)
            }
        }
    }

    protected fun handleMenuItems(menu: Menu){
        menu.findItem(R.id.rename).isVisible = false
        menu.findItem(R.id.open_as)?.isVisible = false
        if (usf_open){
            menu.findItem(R.id.external_open)?.isVisible = false
        }
        val noItemSelected = explorerAdapter.selectedItems.isEmpty()
        val iconColor = ContextCompat.getColor(this, R.color.neutralIconTint)
        setMenuIconTint(menu, iconColor, R.id.sort, R.drawable.icon_sort)
        setMenuIconTint(menu, iconColor, R.id.decrypt, R.drawable.icon_decrypt)
        setMenuIconTint(menu, iconColor, R.id.share, R.drawable.icon_share)
        menu.findItem(R.id.sort).isVisible = noItemSelected
        menu.findItem(R.id.close).isVisible = noItemSelected
        supportActionBar?.setDisplayHomeAsUpEnabled(!noItemSelected)
        if (!noItemSelected) {
            if (explorerAdapter.selectedItems.size == 1) {
                menu.findItem(R.id.rename).isVisible = true
                if (explorerElements[explorerAdapter.selectedItems.first()].isRegularFile) {
                    menu.findItem(R.id.open_as)?.isVisible = true
                    if (usf_open) {
                        menu.findItem(R.id.external_open)?.isVisible = true
                    }
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
                CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.sort_order)
                        .setSingleChoiceItems(sortOrderEntries, currentSortOrderIndex) { dialog, which ->
                            currentSortOrderIndex = which
                            setCurrentPath(currentDirectoryPath)
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                true
            }
            R.id.rename -> {
                val oldName = explorerElements[explorerAdapter.selectedItems.first()].name
                with(EditTextDialog(this, R.string.rename_title) {
                    rename(oldName, it)
                }) {
                    with(binding.dialogEditText) {
                        setText(oldName)
                        selectAll()
                    }
                    show()
                }
                true
            }
            R.id.open_as -> {
                showOpenAsDialog(
                    PathUtils.pathJoin(
                        currentDirectoryPath,
                        explorerElements[explorerAdapter.selectedItems.first()].name
                    )
                )
                true
            }
            R.id.external_open -> {
                if (usf_open){
                    openWithExternalApp(
                        PathUtils.pathJoin(
                            currentDirectoryPath,
                            explorerElements[explorerAdapter.selectedItems.first()].name
                        )
                    )
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
                setCurrentPath(currentDirectoryPath)
            }
        }
    }
}
