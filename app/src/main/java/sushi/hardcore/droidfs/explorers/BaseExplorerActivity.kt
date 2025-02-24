package sushi.hardcore.droidfs.explorers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import sushi.hardcore.droidfs.BaseActivity
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.EncryptedFileProvider
import sushi.hardcore.droidfs.FileShare
import sushi.hardcore.droidfs.FileTypes
import sushi.hardcore.droidfs.LoadingTask
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.adapters.ExplorerElementAdapter
import sushi.hardcore.droidfs.adapters.OpenAsDialogAdapter
import sushi.hardcore.droidfs.content_providers.TemporaryFileProvider
import sushi.hardcore.droidfs.file_operations.FileOperationService
import sushi.hardcore.droidfs.file_operations.OperationFile
import sushi.hardcore.droidfs.file_operations.TaskResult
import sushi.hardcore.droidfs.file_viewers.AudioPlayer
import sushi.hardcore.droidfs.file_viewers.ImageViewer
import sushi.hardcore.droidfs.file_viewers.PdfViewer
import sushi.hardcore.droidfs.file_viewers.TextEditor
import sushi.hardcore.droidfs.file_viewers.VideoPlayer
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.UIUtils
import sushi.hardcore.droidfs.util.finishOnClose
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog

open class BaseExplorerActivity : BaseActivity(), ExplorerElementAdapter.Listener {
    private lateinit var sortOrderEntries: Array<String>
    private lateinit var sortOrderValues: Array<String>
    private var foldersFirst = true
    private var mapFolders = true
    private var currentSortOrderIndex = 0
    protected var volumeId = -1
    protected lateinit var encryptedVolume: EncryptedVolume
    private lateinit var volumeName: String
    private lateinit var explorerViewModel: ExplorerViewModel
    protected var currentDirectoryPath: String = ""
        set(value) {
            field = value
            explorerViewModel.currentDirectoryPath = value
        }
    protected lateinit var fileOperationService: FileOperationService
    protected val activityScope = MainScope()
    private var directoryLoadingTask: Job? = null
    protected lateinit var explorerElements: MutableList<ExplorerElement>
    protected lateinit var explorerAdapter: ExplorerElementAdapter
    protected lateinit var app: VolumeManagerApp
    private var usf_open = false
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var isUsingListLayout = true
    private lateinit var layoutIcon: ImageButton
    private lateinit var titleText: TextView
    private lateinit var recycler_view_explorer: RecyclerView
    private lateinit var refresher: SwipeRefreshLayout
    private lateinit var loader: ProgressBar
    private lateinit var textDirEmpty: TextView
    private lateinit var currentPathText: TextView
    private lateinit var numberOfFilesText: TextView
    private lateinit var numberOfFoldersText: TextView
    private lateinit var totalSizeText: TextView
    private var gridColumnCount = Constants.DEFAULT_GRID_COLUMN_COUNT
    protected val fileShare by lazy { FileShare(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as VolumeManagerApp
        usf_open = sharedPrefs.getBoolean("usf_open", false)
        volumeName = intent.getStringExtra("volumeName") ?: ""
        volumeId = intent.getIntExtra("volumeId", -1)
        encryptedVolume = app.volumeManager.getVolume(volumeId)!!.also { finishOnClose(it) }
        sortOrderEntries = resources.getStringArray(R.array.sort_orders_entries)
        sortOrderValues = resources.getStringArray(R.array.sort_orders_values)
        foldersFirst = sharedPrefs.getBoolean("folders_first", true)
        mapFolders = sharedPrefs.getBoolean("map_folders", true)
        currentSortOrderIndex = resources.getStringArray(R.array.sort_orders_values).indexOf(sharedPrefs.getString(Constants.SORT_ORDER_KEY, "name"))
        init()
        recycler_view_explorer = findViewById(R.id.recycler_view_explorer)
        refresher = findViewById(R.id.refresher)
        loader = findViewById(R.id.loader)
        textDirEmpty = findViewById(R.id.text_dir_empty)
        currentPathText = findViewById(R.id.current_path_text)
        numberOfFilesText = findViewById(R.id.number_of_files_text)
        numberOfFoldersText = findViewById(R.id.number_of_folders_text)
        totalSizeText = findViewById(R.id.total_size_text)
        gridColumnCount = sharedPrefs.getInt(Constants.GRID_COLUMN_COUNT_KEY, Constants.DEFAULT_GRID_COLUMN_COUNT)

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
                encryptedVolume
            } else {
                null
            },
            this,
            sharedPrefs.getLong(Constants.THUMBNAIL_MAX_SIZE_KEY, Constants.DEFAULT_THUMBNAIL_MAX_SIZE)*1000,
        )
        explorerViewModel = ViewModelProvider(this).get(ExplorerViewModel::class.java)
        currentDirectoryPath = explorerViewModel.currentDirectoryPath
        linearLayoutManager = LinearLayoutManager(this@BaseExplorerActivity)
        recycler_view_explorer.adapter = explorerAdapter
        isUsingListLayout = sharedPrefs.getBoolean("useListLayout", true)
        layoutIcon = findViewById(R.id.layout_icon)
        setRecyclerViewLayout()
        onBackPressedDispatcher.addCallback(this) {
            if (explorerAdapter.selectedItems.isEmpty()) {
                val parentPath = PathUtils.getParentPath(currentDirectoryPath)
                if (parentPath == currentDirectoryPath) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    setCurrentPath(PathUtils.getParentPath(currentDirectoryPath))
                }
            } else {
                unselectAll()
            }
        }
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
        var currentDirectoryPath = "/"
    }

    private fun setRecyclerViewLayout() {
        layoutIcon.setImageResource(if (isUsingListLayout) {
            recycler_view_explorer.layoutManager = linearLayoutManager
            explorerAdapter.isUsingListLayout = true
            R.drawable.icon_view_grid
        } else {
            recycler_view_explorer.layoutManager = GridLayoutManager(this, gridColumnCount)
            explorerAdapter.isUsingListLayout = false
            R.drawable.icon_view_list
        })
    }

    protected open fun init() {
        setContentView(R.layout.activity_explorer)
    }

    protected open fun bindFileOperationService() {
        FileOperationService.bind(this) {
            fileOperationService = it
        }
    }

    private fun startFileViewer(cls: Class<*>, filePath: String) {
        val intent = Intent(this, cls).apply {
            putExtra("path", filePath)
            putExtra("volumeId", volumeId)
            putExtra("sortOrder", sortOrderValues[currentSortOrderIndex])
        }
        startActivity(intent)
    }

    protected fun onExportFailed(errorResId: Int) {
        CustomAlertDialogBuilder(this, theme)
            .setTitle(R.string.error)
            .setMessage(getString(R.string.tmp_export_failed, getString(errorResId)))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun openWithExternalApp(path: String, size: Long) {
        app.isExporting = true
        val exportedFile = TemporaryFileProvider.instance.encryptedFileProvider.createFile(path, size)
        if (exportedFile == null) {
            onExportFailed(R.string.export_failed_create)
            return
        }
        val msg = when (exportedFile) {
            is EncryptedFileProvider.ExportedMemFile -> R.string.export_mem
            is EncryptedFileProvider.ExportedDiskFile -> R.string.export_disk
            else -> R.string.loading_msg_export
        }
        object : LoadingTask<Pair<Intent?, Int?>>(this, theme, msg) {
            override suspend fun doTask(): Pair<Intent?, Int?> {
                return fileShare.openWith(exportedFile, size, volumeId)
            }
        }.startTask(lifecycleScope) { (intent, error) ->
            if (intent == null) {
                onExportFailed(error!!)
            } else {
                app.isStartingExternalApp = true
                startActivity(intent)
            }
            app.isExporting = false
        }
    }

    private fun showOpenAsDialog(explorerElement: ExplorerElement) {
        val path = explorerElement.fullPath
        val adapter = OpenAsDialogAdapter(this, usf_open)
        CustomAlertDialogBuilder(this, theme)
            .setSingleChoiceItems(adapter, -1) { dialog, which ->
                when (adapter.getItem(which)) {
                    "image" -> startFileViewer(ImageViewer::class.java, path)
                    "video" -> startFileViewer(VideoPlayer::class.java, path)
                    "audio" -> startFileViewer(AudioPlayer::class.java, path)
                    "pdf" -> startFileViewer(PdfViewer::class.java, path)
                    "text" -> startFileViewer(TextEditor::class.java, path)
                    "external" -> if (usf_open) {
                        openWithExternalApp(path, explorerElement.stat.size)
                    }
                }
                dialog.dismiss()
            }
            .setTitle(getString(R.string.open_as) + ':')
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    protected fun createNewFile(callback: (Long) -> Unit) {
        EditTextDialog(this, R.string.enter_file_name) {
            if (it.isEmpty()) {
                Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
                createNewFile(callback)
            } else {
                val filePath = PathUtils.pathJoin(currentDirectoryPath, it)
                val handleID = encryptedVolume.openFileWriteMode(filePath)
                if (handleID == -1L) {
                    CustomAlertDialogBuilder(this, theme)
                        .setTitle(R.string.error)
                        .setMessage(R.string.file_creation_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else {
                    callback(handleID)
                }
            }
        }.show()
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
                FileTypes.isImage(fullPath) -> {
                    startFileViewer(ImageViewer::class.java, fullPath)
                }
                FileTypes.isVideo(fullPath) -> {
                    startFileViewer(VideoPlayer::class.java, fullPath)
                }
                FileTypes.isText(fullPath) -> {
                    startFileViewer(TextEditor::class.java, fullPath)
                }
                FileTypes.isPDF(fullPath) -> {
                    startFileViewer(PdfViewer::class.java, fullPath)
                }
                FileTypes.isAudio(fullPath) -> {
                    startFileViewer(AudioPlayer::class.java, fullPath)
                }
                else -> showOpenAsDialog(explorerElements[position])
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

    private fun displayExplorerElements() {
        ExplorerElement.sortBy(sortOrderValues[currentSortOrderIndex], foldersFirst, explorerElements)
        unselectAll(false)
        loader.isVisible = false
        recycler_view_explorer.isVisible = true
        explorerAdapter.explorerElements = explorerElements
    }

    private suspend fun recursiveSetSize(directory: ExplorerElement) {
        yield()
        for (child in encryptedVolume.readDir(directory.fullPath) ?: return) {
            if (child.isDirectory) {
                recursiveSetSize(child)
            }
            directory.stat.size += child.stat.size
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

    protected fun setCurrentPath(path: String, onDisplayed: (() -> Unit)? = null) = lifecycleScope.launch {
        directoryLoadingTask?.cancelAndJoin()
        recycler_view_explorer.isVisible = false
        loader.isVisible = true
        explorerElements = encryptedVolume.readDir(path) ?: return@launch
        if (path != "/") {
            explorerElements.add(
                0,
                ExplorerElement("..", Stat.parentFolderStat(), parentPath = currentDirectoryPath)
            )
        }
        textDirEmpty.visibility = if (explorerElements.size == 0) View.VISIBLE else View.GONE
        currentDirectoryPath = path
        currentPathText.text = getString(R.string.location, currentDirectoryPath)
        displayNumberOfElements(numberOfFilesText, R.string.one_file, R.string.multiple_files, explorerElements.count { it.isRegularFile })
        displayNumberOfElements(numberOfFoldersText, R.string.one_folder, R.string.multiple_folders, explorerElements.count { it.isDirectory })
        if (mapFolders) {
            var totalSize: Long = 0
            directoryLoadingTask = launch(Dispatchers.IO) {
                for (element in explorerElements) {
                    if (element.isDirectory) {
                        recursiveSetSize(element)
                    }
                    totalSize += element.stat.size
                }
            }
            directoryLoadingTask!!.join()
            displayExplorerElements()
            totalSizeText.text = getString(R.string.total_size, PathUtils.formatSize(totalSize))
            onDisplayed?.invoke()
        } else {
            displayExplorerElements()
            totalSizeText.text = getString(
                R.string.total_size,
                PathUtils.formatSize(explorerElements.filter { !it.isParentFolder }.sumOf { it.stat.size })
            )
            onDisplayed?.invoke()
        }
    }

    private fun askLockVolume() {
        CustomAlertDialogBuilder(this, theme)
                .setTitle(R.string.warning)
                .setMessage(R.string.ask_lock_volume)
                .setPositiveButton(R.string.ok) { _, _ ->
                    app.volumeManager.closeVolume(volumeId)
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    private fun createFolder(folderName: String){
        if (folderName.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!encryptedVolume.mkdir(PathUtils.pathJoin(currentDirectoryPath, folderName))) {
                CustomAlertDialogBuilder(this, theme)
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

    protected fun checkPathOverwrite(items: List<OperationFile>, dstDirectoryPath: String, callback: (List<OperationFile>?) -> Unit) {
        val srcDirectoryPath = items[0].parentPath
        var ready = true
        for (i in items.indices) {
            val testDstPath: String
            if (items[i].dstPath == null){
                testDstPath = PathUtils.pathJoin(dstDirectoryPath, PathUtils.getRelativePath(srcDirectoryPath, items[i].srcPath))
                if (encryptedVolume.pathExists(testDstPath)) {
                    ready = false
                } else {
                    items[i].dstPath = testDstPath
                }
            } else {
                testDstPath = items[i].dstPath!!
                if (encryptedVolume.pathExists(testDstPath) && !items[i].overwriteConfirmed) {
                    ready = false
                }
            }
            if (!ready){
                CustomAlertDialogBuilder(this, theme)
                    .setTitle(R.string.warning)
                    .setMessage(getString(
                        if (items[i].isDirectory) {
                            R.string.dir_overwrite_question
                        } else {
                            R.string.file_overwrite_question
                        }, testDstPath
                    ))
                    .setPositiveButton(R.string.yes) {_, _ ->
                        items[i].dstPath = testDstPath
                        items[i].overwriteConfirmed = true
                        checkPathOverwrite(items, dstDirectoryPath, callback)
                    }
                    .setNegativeButton(R.string.no) { _, _ ->
                        with(EditTextDialog(this, R.string.enter_new_name) {
                            items[i].dstPath = PathUtils.pathJoin(dstDirectoryPath, PathUtils.getRelativePath(srcDirectoryPath, items[i].parentPath), it)
                            if (items[i].isDirectory) {
                                for (j in items.indices) {
                                    if (PathUtils.isChildOf(items[j].srcPath, items[i].srcPath)) {
                                        items[j].dstPath = PathUtils.pathJoin(items[i].dstPath!!, PathUtils.getRelativePath(items[i].srcPath, items[j].srcPath))
                                    }
                                }
                            }
                            checkPathOverwrite(items, dstDirectoryPath, callback)
                        }) {
                            setSelectedText(items[i].name)
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

    protected fun onTaskResult(
        result: TaskResult<out String?>,
        failedErrorMessage: Int,
        successMessage: Int = -1,
        onSuccess: (() -> Unit)? = null,
    ) {
        when (result.state) {
            TaskResult.State.SUCCESS -> {
                if (onSuccess == null) {
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                } else {
                    onSuccess()
                }
            }
            TaskResult.State.FAILED -> {
                CustomAlertDialogBuilder(this, theme)
                    .setTitle(R.string.error)
                    .setMessage(getString(failedErrorMessage, result.failedItem))
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
            TaskResult.State.ERROR -> result.showErrorAlertDialog(this, theme)
            TaskResult.State.CANCELLED -> {}
        }
    }

    protected fun importFilesFromUris(uris: List<Uri>, callback: () -> Unit) {
        val items = ArrayList<OperationFile>()
        for (uri in uris) {
            val fileName = PathUtils.getFilenameFromURI(this, uri)
            if (fileName == null) {
                CustomAlertDialogBuilder(this, theme)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.error_retrieving_filename, uri))
                        .setPositiveButton(R.string.ok, null)
                        .show()
                items.clear()
                break
            } else {
                items.add(OperationFile(PathUtils.pathJoin(currentDirectoryPath, fileName), Stat.S_IFREG))
            }
        }
        if (items.size > 0) {
            checkPathOverwrite(items, currentDirectoryPath) { checkedItems ->
                checkedItems?.let {
                    activityScope.launch {
                        val result = fileOperationService.importFilesFromUris(volumeId, checkedItems.map { it.dstPath!! }, uris)
                        onTaskResult(result, R.string.import_failed, onSuccess = callback)
                        setCurrentPath(currentDirectoryPath)
                    }
                }
            }
        }
    }

    protected fun rename(old_name: String, new_name: String){
        if (new_name.isEmpty()) {
            Toast.makeText(this, R.string.error_filename_empty, Toast.LENGTH_SHORT).show()
        } else {
            if (!encryptedVolume.rename(PathUtils.pathJoin(currentDirectoryPath, old_name), PathUtils.pathJoin(currentDirectoryPath, new_name))) {
                CustomAlertDialogBuilder(this, theme)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.rename).isVisible = false
        menu.findItem(R.id.open_as)?.isVisible = false
        if (usf_open){
            menu.findItem(R.id.external_open)?.isVisible = false
        }
        val noItemSelected = explorerAdapter.selectedItems.isEmpty()
        with(UIUtils.getMenuIconNeutralTint(this, menu)) {
            applyTo(R.id.sort, R.drawable.icon_sort)
            applyTo(R.id.share, R.drawable.icon_share)
        }
        menu.findItem(R.id.sort).isVisible = noItemSelected
        menu.findItem(R.id.lock).isVisible = noItemSelected
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
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                unselectAll()
                true
            }
            R.id.sort -> {
                CustomAlertDialogBuilder(this, theme)
                        .setTitle(R.string.sort_order)
                        .setSingleChoiceItems(sortOrderEntries, currentSortOrderIndex) { dialog, which ->
                            currentSortOrderIndex = which
                            // displayExplorerElements must not be called if directoryLoadingTask is active
                            if (directoryLoadingTask?.isActive != true) {
                                displayExplorerElements()
                            }
                            val sharedPrefsEditor = sharedPrefs.edit()
                            sharedPrefsEditor.putString(Constants.SORT_ORDER_KEY, sortOrderValues[currentSortOrderIndex])
                            sharedPrefsEditor.apply()
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
                    setSelectedText(oldName)
                    show()
                }
                true
            }
            R.id.open_as -> {
                showOpenAsDialog(explorerElements[explorerAdapter.selectedItems.first()])
                true
            }
            R.id.external_open -> {
                if (usf_open){
                    val explorerElement = explorerElements[explorerAdapter.selectedItems.first()]
                    openWithExternalApp(explorerElement.fullPath, explorerElement.stat.size)
                    unselectAll()
                }
                true
            }
            R.id.close -> {
                finish()
                true
            }
            R.id.lock -> {
                askLockVolume()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) { //activity won't be recreated
            activityScope.cancel()
        }
    }

    override fun onResume() {
        super.onResume()
        if (app.isStartingExternalApp) {
            TemporaryFileProvider.instance.wipe()
        }
        setCurrentPath(currentDirectoryPath)
    }
}
