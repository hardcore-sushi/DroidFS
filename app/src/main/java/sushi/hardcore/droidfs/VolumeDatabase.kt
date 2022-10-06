package sushi.hardcore.droidfs

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.PathUtils
import java.io.File

class VolumeDatabase(private val context: Context): SQLiteOpenHelper(context, ConstValues.VOLUME_DATABASE_NAME, null, 4) {
    companion object {
        const val TABLE_NAME = "Volumes"
        const val COLUMN_NAME = "name"
        const val COLUMN_HIDDEN = "hidden"
        const val COLUMN_TYPE = "type"
        const val COLUMN_HASH = "hash"
        const val COLUMN_IV = "iv"

        private fun contentValuesFromVolume(volume: VolumeData): ContentValues {
            val contentValues = ContentValues()
            contentValues.put(COLUMN_NAME, volume.name)
            contentValues.put(COLUMN_HIDDEN, volume.isHidden)
            contentValues.put(COLUMN_TYPE, byteArrayOf(volume.type))
            contentValues.put(COLUMN_HASH, volume.encryptedHash)
            contentValues.put(COLUMN_IV, volume.iv)
            return contentValues
        }
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NAME (" +
                    "$COLUMN_NAME TEXT PRIMARY KEY," +
                    "$COLUMN_HIDDEN SHORT," +
                    "$COLUMN_TYPE BLOB," +
                    "$COLUMN_HASH BLOB," +
                    "$COLUMN_IV BLOB" +
                ");"
        )
        File(context.filesDir, VolumeData.VOLUMES_DIRECTORY).mkdir()
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Adding type column and set it to GOCRYPTFS_VOLUME_TYPE for all existing volumes
        db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_TYPE BLOB;")
        db.update(TABLE_NAME, ContentValues().apply {
            put(COLUMN_TYPE, byteArrayOf(EncryptedVolume.GOCRYPTFS_VOLUME_TYPE))
        }, null, null)

        // Moving hidden volumes to the "volumes" directory
        if (File(context.filesDir, VolumeData.VOLUMES_DIRECTORY).mkdir()) {
            val cursor = db.query(
                TABLE_NAME,
                arrayOf(COLUMN_NAME),
                "$COLUMN_HIDDEN=?",
                arrayOf("1"),
                null,
                null,
                null
            )
            while (cursor.moveToNext()) {
                val volumeName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME))
                File(
                    PathUtils.pathJoin(
                        context.filesDir.path,
                        volumeName
                    )
                ).renameTo(
                    File(
                        VolumeData(
                            volumeName,
                            true,
                            EncryptedVolume.GOCRYPTFS_VOLUME_TYPE
                        ).getFullPath(context.filesDir.path)
                    ).canonicalFile
                )
            }
            cursor.close()
        } else {
            Log.e("VolumeDatabase", "Volumes directory creation failed while upgrading")
        }
    }

    private fun extractVolumeData(cursor: Cursor): VolumeData {
        return VolumeData(
            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)),
            cursor.getShort(cursor.getColumnIndexOrThrow(COLUMN_HIDDEN)) == 1.toShort(),
            cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_TYPE))[0],
            cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_HASH)),
            cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_IV))
        )
    }

    private fun getVolumeCursor(volumeName: String, isHidden: Boolean): Cursor {
        return readableDatabase.query(
            TABLE_NAME, null,
            "$COLUMN_NAME=? AND $COLUMN_HIDDEN=?",
            arrayOf(volumeName, (if (isHidden) 1 else 0).toString()),
            null, null, null
        )
    }

    fun getVolume(volumeName: String, isHidden: Boolean): VolumeData? {
        val cursor = getVolumeCursor(volumeName, isHidden)
        val volumeData = if (cursor.moveToNext()) {
            extractVolumeData(cursor)
        } else {
            null
        }
        cursor.close()
        return volumeData
    }

    fun isVolumeSaved(volumeName: String, isHidden: Boolean): Boolean {
        val cursor = getVolumeCursor(volumeName, isHidden)
        val result = cursor.count > 0
        cursor.close()
        return result
    }

    fun saveVolume(volume: VolumeData): Boolean {
        if (!isVolumeSaved(volume.name, volume.isHidden)) {
            return (writableDatabase.insert(TABLE_NAME, null, contentValuesFromVolume(volume)) >= 0.toLong())
        }
        return false
    }

    fun getVolumes(): List<VolumeData> {
        val list: MutableList<VolumeData> = ArrayList()
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_NAME", null)
        while (cursor.moveToNext()){
            list.add(extractVolumeData(cursor))
        }
        cursor.close()
        return list
    }

    fun isHashSaved(volumeName: String): Boolean {
        val cursor = readableDatabase.query(TABLE_NAME, arrayOf(COLUMN_NAME, COLUMN_HASH), "$COLUMN_NAME=?", arrayOf(volumeName), null, null, null)
        var isHashSaved = false
        if (cursor.moveToNext()) {
            if (cursor.getBlob(cursor.getColumnIndexOrThrow(COLUMN_HASH)) != null) {
                isHashSaved = true
            }
        }
        cursor.close()
        return isHashSaved
    }

    fun addHash(volume: VolumeData): Boolean {
        return writableDatabase.update(TABLE_NAME, contentValuesFromVolume(volume), "$COLUMN_NAME=?", arrayOf(volume.name)) > 0
    }

    fun removeHash(volume: VolumeData): Boolean {
        return writableDatabase.update(
            TABLE_NAME, contentValuesFromVolume(
            VolumeData(
                volume.name,
                volume.isHidden,
                volume.type,
                null,
                null
            )
        ), "$COLUMN_NAME=?", arrayOf(volume.name)) > 0
    }

    fun renameVolume(oldName: String, newName: String): Boolean {
        return writableDatabase.update(TABLE_NAME,
            ContentValues().apply {
                put(COLUMN_NAME, newName)
            },
            "$COLUMN_NAME=?",arrayOf(oldName)
        ) > 0
    }

    fun removeVolume(volumeName: String): Boolean {
        return writableDatabase.delete(TABLE_NAME, "$COLUMN_NAME=?", arrayOf(volumeName)) > 0
    }
}