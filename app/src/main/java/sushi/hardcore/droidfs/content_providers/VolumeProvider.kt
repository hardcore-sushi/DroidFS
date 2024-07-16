package sushi.hardcore.droidfs.content_providers

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.BuildConfig
import sushi.hardcore.droidfs.EncryptedFileProvider
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeManager
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.Stat
import sushi.hardcore.droidfs.util.AndroidUtils
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File

class VolumeProvider: DocumentsProvider() {
    companion object {
        private const val TAG = "DocumentsProvider"
        private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".volume_provider"
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        )
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )

        fun notifyRootsChanged(context: Context) {
            context.contentResolver.notifyChange(DocumentsContract.buildRootsUri(AUTHORITY), null)
        }
    }

    private val usfExposeDelegate = AndroidUtils.LiveBooleanPreference("usf_expose", false)
    private val usfExpose by usfExposeDelegate
    private val usfSafWriteDelegate = AndroidUtils.LiveBooleanPreference("usf_saf_write", false)
    private val usfSafWrite by usfSafWriteDelegate
    private lateinit var volumeManager: VolumeManager
    private val volumes = HashMap<String, Pair<Int, VolumeData>>()
    private lateinit var encryptedFileProvider: EncryptedFileProvider

    override fun onCreate(): Boolean {
        val context = (context ?: return false)
        AndroidUtils.LiveBooleanPreference.init(context, usfExposeDelegate, usfSafWriteDelegate)
        volumeManager = (context.applicationContext as VolumeManagerApp).volumeManager
        encryptedFileProvider = EncryptedFileProvider(context)
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        if (!usfExpose) return cursor
        volumes.clear()
        for (volume in volumeManager.listVolumes()) {
            var flags = DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
            if (usfSafWrite && volume.second.canWrite(context!!.filesDir.path)) {
                flags = flags or DocumentsContract.Root.FLAG_SUPPORTS_CREATE
            }
            cursor.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, volume.second.name)
                add(DocumentsContract.Root.COLUMN_FLAGS, flags)
                add(DocumentsContract.Root.COLUMN_ICON, R.drawable.icon_document_provider)
                add(DocumentsContract.Root.COLUMN_TITLE, volume.second.name)
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, volume.second.uuid)
            }
            volumes[volume.second.uuid] = volume
        }
        return cursor
    }

    internal data class DocumentData(
        val rootId: String,
        val volumeId: Int,
        val volumeData: VolumeData,
        val encryptedVolume: EncryptedVolume,
        val path: String
    ) {
        fun child(childPath: String) = DocumentData(rootId, volumeId, volumeData, encryptedVolume, childPath)
    }

    private fun parseDocumentId(documentId: String): DocumentData? {
        val splits = documentId.split("/", limit = 2)
        if (splits.size > 2) {
            return null
        } else {
            volumes[splits[0]]?.let {
                val encryptedVolume = volumeManager.getVolume(it.first) ?: return null
                val path = "/"+if (splits.size == 2) {
                    splits[1]
                } else {
                    ""
                }
                return DocumentData(splits[0], it.first, it.second, encryptedVolume, path)
            }
        }
        return null
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (!usfExpose) return false
        val parent = parseDocumentId(parentDocumentId) ?: return false
        val child = parseDocumentId(documentId) ?: return false
        return parent.rootId == child.rootId && PathUtils.isChildOf(child.path, parent.path)
    }

    private fun addDocumentRow(cursor: MatrixCursor, volumeData: VolumeData, documentId: String, name: String, stat: Stat) {
        val isDirectory = stat.type == Stat.S_IFDIR
        var flags = 0
        if (usfSafWrite && volumeData.canWrite(context!!.filesDir.path)) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
            if (isDirectory) {
                flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            } else if (stat.type == Stat.S_IFREG) {
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            }
        }
        val mimeType = if (isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(name).extension)
                ?: "application/octet-stream"
        }
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            add(DocumentsContract.Document.COLUMN_SIZE, stat.size)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, stat.mTime)
        }
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (!usfExpose) return cursor
        val document = parseDocumentId(documentId) ?: return cursor
        document.encryptedVolume.getAttr(document.path)?.let { stat ->
            val name = if (document.path == "/") {
                document.volumeData.shortName
            } else {
                File(document.path).name
            }
            addDocumentRow(cursor, document.volumeData, documentId, name, stat)
        }
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (!usfExpose) return cursor
        val document = parseDocumentId(parentDocumentId) ?: return cursor
        document.encryptedVolume.readDir(document.path)?.let { content ->
            for (i in content) {
                if (i.isParentFolder) continue
                addDocumentRow(cursor, document.volumeData, document.rootId+i.fullPath, i.name, i.stat)
            }
        }
        return cursor
    }

    class LazyExportedFile(
        private val encryptedFileProvider: EncryptedFileProvider,
        private val encryptedVolume: EncryptedVolume,
        path: String,
    ) : EncryptedFileProvider.ExportedFile(path) {

        private val exportedFile: EncryptedFileProvider.ExportedFile by lazy {
            val size = encryptedVolume.getAttr(path)?.size ?: run {
                Log.e(TAG, "stat() failed")
                throw RuntimeException("stat() failed")
            }
            val exportedFile = encryptedFileProvider.createFile(path, size) ?: run {
                Log.e(TAG, "Can't create exported file")
                throw RuntimeException("Can't create exported file")
            }
            if (!encryptedFileProvider.exportFile(exportedFile, encryptedVolume)) {
                Log.e(TAG, "File export failed")
                throw RuntimeException("File export failed")
            }
            exportedFile
        }

        override fun open(mode: Int, furtive: Boolean) = exportedFile.open(mode, furtive)
        override fun free() = exportedFile.free()
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        if (!usfExpose) return null
        val document = parseDocumentId(documentId) ?: return null

        val lazyExportedFile = LazyExportedFile(encryptedFileProvider, document.encryptedVolume, document.path)

        val result = encryptedFileProvider.openFile(
            lazyExportedFile,
            mode,
            document.encryptedVolume,
            volumeManager.getCoroutineScope(document.volumeId),
            true,
            usfSafWrite,
        )
        when (result.second) {
            EncryptedFileProvider.Error.SUCCESS -> return result.first!!
            EncryptedFileProvider.Error.WRITE_ACCESS_DENIED -> Log.e(TAG, "Unauthorized write access requested from $callingPackage")
            else -> result.second.log()
        }
        return null
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String?,
        displayName: String
    ): String? {
        if (!usfExpose || !usfSafWrite) return null
        val document = parseDocumentId(parentDocumentId) ?: return null
        val path = PathUtils.pathJoin(document.path, displayName)
        var success = false
        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
            success = document.encryptedVolume.mkdir(path)
        } else {
            val f = document.encryptedVolume.openFileWriteMode(path)
            if (f != -1L) {
                document.encryptedVolume.closeFile(f)
                success = true
            }
        }
        return if (success) {
            document.rootId+path
        } else {
            null
        }
    }

    override fun deleteDocument(documentId: String) {
        if (!usfExpose || !usfSafWrite) return

        fun recursiveRemoveDirectory(document: DocumentData) {
            document.encryptedVolume.readDir(document.path)?.forEach { e ->
                val childPath = PathUtils.pathJoin(document.path, e.name)
                if (e.isDirectory) {
                    recursiveRemoveDirectory(document.child(childPath))
                } else  {
                    document.encryptedVolume.deleteFile(childPath)
                }
                revokeDocumentPermission(document.rootId+childPath)
            }
            document.encryptedVolume.rmdir(document.path)
        }

        val document = parseDocumentId(documentId) ?: return
        document.encryptedVolume.getAttr(document.path)?.let { stat ->
            if (stat.type == Stat.S_IFDIR) {
                recursiveRemoveDirectory(document)
            } else {
                document.encryptedVolume.deleteFile(document.path)
            }
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        if (!usfExpose || !usfSafWrite) return documentId
        val document = parseDocumentId(documentId) ?: return documentId
        val newPath = PathUtils.pathJoin(PathUtils.getParentPath(document.path), displayName)
        return if (document.encryptedVolume.rename(document.path, newPath)) {
            document.rootId+newPath
        } else {
            documentId
        }
    }
}