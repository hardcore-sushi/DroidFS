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
import java.util.UUID

class VolumeDatabase(private val context: Context): SQLiteOpenHelper(context, Constants.VOLUME_DATABASE_NAME, null, 6) {
    companion object {
        private const val TAG = "VolumeDatabase"
        private const val TABLE_NAME = "Volumes"
        private const val COLUMN_UUID = "uuid"
        private const val COLUMN_NAME = "name"
        private const val COLUMN_HIDDEN = "hidden"
        private const val COLUMN_TYPE = "type"
        private const val COLUMN_HASH = "hash"
        private const val COLUMN_IV = "iv"
    }

    private fun createTable(db: SQLiteDatabase) =
        db.execSQL(
        "CREATE TABLE IF NOT EXISTS $TABLE_NAME (" +
                "$COLUMN_UUID TEXT PRIMARY KEY," +
                "$COLUMN_NAME TEXT," +
                "$COLUMN_HIDDEN SHORT," +
                "$COLUMN_TYPE BLOB," +
                "$COLUMN_HASH BLOB," +
                "$COLUMN_IV BLOB" +
            ");"
        )

    override fun onCreate(db: SQLiteDatabase) {
        createTable(db)
        File(context.filesDir, VolumeData.VOLUMES_DIRECTORY).mkdir()
    }

    private fun getNewVolumePath(volumeName: String): File {
        return File(
            VolumeData.getFullPath(volumeName, true, context.filesDir.path)
        ).canonicalFile
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 3) {
            // Adding type column and set it to GOCRYPTFS_VOLUME_TYPE for all existing volumes
            db.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_TYPE BLOB;")
            db.update(TABLE_NAME, ContentValues().apply {
                put(COLUMN_TYPE, byteArrayOf(EncryptedVolume.GOCRYPTFS_VOLUME_TYPE))
            }, null, null)

            // Moving registered hidden volumes to the "volumes" directory
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
                    val success = File(
                        PathUtils.pathJoin(
                            context.filesDir.path,
                            volumeName
                        )
                    ).renameTo(getNewVolumePath(volumeName))
                    if (!success) {
                        Log.e(TAG, "Failed to move $volumeName")
                    }
                }
                cursor.close()
            } else {
                Log.e(TAG, "Volumes directory creation failed while upgrading")
            }
        }
        // Moving unregistered hidden volumes to the "volumes" directory
        File(context.filesDir.path).listFiles()?.let {
            for (i in it) {
                if (i.isDirectory && i.name != Constants.CRYFS_LOCAL_STATE_DIR && i.name != VolumeData.VOLUMES_DIRECTORY) {
                    if (EncryptedVolume.getVolumeType(i.path) != (-1).toByte()) {
                        if (!i.renameTo(getNewVolumePath(i.name))) {
                            Log.e(TAG, "Failed to move "+i.name)
                        }
                    }
                }
            }
        }
        if (oldVersion < 6) {
            val volumeCount = db.rawQuery("SELECT COUNT(*) FROM $TABLE_NAME", null).let { cursor ->
                cursor.moveToNext()
                cursor.getInt(0).also {
                    cursor.close()
                }
            }
            db.execSQL("ALTER TABLE $TABLE_NAME RENAME TO OLD;")
            createTable(db)
            val uuids = (0 until volumeCount).joinToString(", ") { "('${VolumeData.newUuid()}')" }
            val baseColumns = "$COLUMN_NAME, $COLUMN_HIDDEN, $COLUMN_TYPE, $COLUMN_HASH, $COLUMN_IV"
            // add uuids to old data
            db.execSQL("INSERT INTO $TABLE_NAME " +
                    "SELECT uuid, $baseColumns FROM " +
                    "(SELECT $baseColumns, ROW_NUMBER() OVER () i FROM OLD) NATURAL JOIN " +
                    "(SELECT column1 uuid, ROW_NUMBER() OVER () i FROM (VALUES $uuids));")
            db.execSQL("DROP TABLE OLD;")
        }
    }

    private fun extractVolumeData(cursor: Cursor): VolumeData {
        return VolumeData(
            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_UUID)),
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
            return (writableDatabase.insert(TABLE_NAME, null, ContentValues().apply {
                put(COLUMN_UUID, volume.uuid)
                put(COLUMN_NAME, volume.name)
                put(COLUMN_HIDDEN, volume.isHidden)
                put(COLUMN_TYPE, byteArrayOf(volume.type))
                put(COLUMN_HASH, volume.encryptedHash)
                put(COLUMN_IV, volume.iv)
            }) >= 0.toLong())
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

    fun isHashSaved(volume: VolumeData): Boolean {
        val cursor = readableDatabase.rawQuery("SELECT $COLUMN_HASH FROM $TABLE_NAME WHERE $COLUMN_UUID=?", arrayOf(volume.uuid))
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
        return writableDatabase.update(TABLE_NAME, ContentValues().apply {
            put(COLUMN_HASH, volume.encryptedHash)
            put(COLUMN_IV, volume.iv)
        }, "$COLUMN_UUID=?", arrayOf(volume.uuid)) > 0
    }

    fun removeHash(volume: VolumeData): Boolean {
        return writableDatabase.update(
            TABLE_NAME,
            ContentValues().apply {
                put(COLUMN_HASH, null as ByteArray?)
                put(COLUMN_IV, null as ByteArray?)
            }, "$COLUMN_UUID=?", arrayOf(volume.uuid)
        ) > 0
    }

    fun renameVolume(volume: VolumeData, newName: String): Boolean {
        return writableDatabase.update(
            TABLE_NAME,
            ContentValues().apply {
                put(COLUMN_NAME, newName)
            },
            "$COLUMN_UUID=?", arrayOf(volume.uuid)
        ) > 0
    }

    fun removeVolume(volume: VolumeData): Boolean {
        return writableDatabase.delete(TABLE_NAME, "$COLUMN_UUID=?", arrayOf(volume.uuid)) > 0
    }
}