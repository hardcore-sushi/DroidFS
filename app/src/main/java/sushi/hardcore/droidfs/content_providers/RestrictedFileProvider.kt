package sushi.hardcore.droidfs.content_providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import sushi.hardcore.droidfs.BuildConfig
import sushi.hardcore.droidfs.util.SQLUtil.appendSelectionArgs
import sushi.hardcore.droidfs.util.SQLUtil.concatenateWhere
import sushi.hardcore.droidfs.util.Wiper
import java.io.File
import java.util.*
import java.util.regex.Pattern

class RestrictedFileProvider: ContentProvider() {
    companion object {
        private const val DB_NAME = "temporary_files.db"
        private const val TABLE_FILES = "files"
        private const val DB_VERSION = 3
        private var dbHelper: RestrictedDatabaseHelper? = null
        private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".temporary_provider"
        private val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY")
        const val TEMPORARY_FILES_DIR_NAME = "temp"
        private val UUID_PATTERN = Pattern.compile("[a-fA-F0-9-]+")

        private lateinit var tempFilesDir: File

        internal class TemporaryFileColumns {
            companion object {
                const val COLUMN_UUID = "uuid"
                const val COLUMN_NAME = "name"
            }
        }

        internal class RestrictedDatabaseHelper(context: Context?): SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS " + TABLE_FILES + " (" +
                            TemporaryFileColumns.COLUMN_UUID + " TEXT PRIMARY KEY, " +
                            TemporaryFileColumns.COLUMN_NAME + " TEXT" +
                            ");"
                )
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion == 1) {
                    db.execSQL("DROP TABLE IF EXISTS files")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS " + TABLE_FILES + " (" +
                                TemporaryFileColumns.COLUMN_UUID + " TEXT PRIMARY KEY, " +
                                TemporaryFileColumns.COLUMN_NAME + " TEXT" +
                                ");"
                    )
                }
            }
        }

        fun newFile(fileName: String): Uri? {
            val uuid = UUID.randomUUID().toString()
            val file = File(tempFilesDir, uuid)
            return if (file.createNewFile()){
                val contentValues = ContentValues()
                contentValues.put(TemporaryFileColumns.COLUMN_UUID, uuid)
                contentValues.put(TemporaryFileColumns.COLUMN_NAME, fileName)
                if (dbHelper?.writableDatabase?.insert(TABLE_FILES, null, contentValues)?.toInt() != -1){
                    Uri.withAppendedPath(CONTENT_URI, uuid)
                } else {
                    null
                }
            } else {
                null
            }
        }

        fun wipeAll(context: Context) {
            tempFilesDir.listFiles()?.let{
                for (file in it) {
                    Wiper.wipe(file)
                }
            }
            dbHelper?.close()
            context.deleteDatabase(DB_NAME)
        }

        private fun isValidUUID(uuid: String): Boolean {
            return UUID_PATTERN.matcher(uuid).matches()
        }

        private fun getUuidFromUri(uri: Uri): String? {
            val uuid = uri.lastPathSegment
            if (uuid != null) {
                if (isValidUUID(uuid)) {
                    return uuid
                }
            }
            return null
        }

        private fun getFileFromUUID(uuid: String): File? {
            if (isValidUUID(uuid)){
                return File(tempFilesDir, uuid)
            }
            return null
        }

        private fun getFileFromUri(uri: Uri): File? {
            getUuidFromUri(uri)?.let {
                return getFileFromUUID(it)
            }
            return null
        }
    }

    override fun onCreate(): Boolean {
        context?.let {
            dbHelper = RestrictedDatabaseHelper(it)
            tempFilesDir = File(it.cacheDir, TEMPORARY_FILES_DIR_NAME)
            return tempFilesDir.mkdirs()
        }
        return false
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw RuntimeException("Operation not supported")
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        throw RuntimeException("Operation not supported")
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        var resultCursor: MatrixCursor? = null
        val temporaryFile = getFileFromUri(uri)
        temporaryFile?.let{
            val fileName = dbHelper?.readableDatabase?.query(TABLE_FILES, arrayOf(TemporaryFileColumns.COLUMN_NAME), TemporaryFileColumns.COLUMN_UUID + "=?", arrayOf(uri.lastPathSegment), null, null, null)
            fileName?.let{
                if (fileName.moveToNext()) {
                    resultCursor = MatrixCursor(
                        arrayOf(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.SIZE
                        )
                    )
                    resultCursor!!.newRow()
                        .add(fileName.getString(0))
                        .add(temporaryFile.length())
                }
                fileName.close()
            }
        }
        return resultCursor
    }

    override fun delete(uri: Uri, givenSelection: String?, givenSelectionArgs: Array<String>?): Int {
        val uuid = getUuidFromUri(uri)
        uuid?.let{
            val selection = concatenateWhere(givenSelection ?: "" , TemporaryFileColumns.COLUMN_UUID + "=?")
            val selectionArgs = appendSelectionArgs(givenSelectionArgs, arrayOf(it))

            val files = dbHelper?.readableDatabase?.query(TABLE_FILES, arrayOf(TemporaryFileColumns.COLUMN_UUID), selection, selectionArgs, null, null, null)
            if (files != null) {
                while (files.moveToNext()) {
                    getFileFromUUID(files.getString(0))?.let { file ->
                        Wiper.wipe(file)
                    }
                }
                files.close()
                return dbHelper?.writableDatabase?.delete(TABLE_FILES, selection, selectionArgs) ?: 0
            }
        }
        return 0
    }

    override fun getType(uri: Uri): String {
        return "application/octet-stream"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (("w" in mode && callingPackage == BuildConfig.APPLICATION_ID) || "w" !in mode) {
            getFileFromUri(uri)?.let{
                return ParcelFileDescriptor.open(it, ParcelFileDescriptor.parseMode(mode))
            }
        } else {
            throw SecurityException("Read-only access")
        }
        return null
    }
}