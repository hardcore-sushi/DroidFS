package sushi.hardcore.droidfs

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VolumeDatabase(context: Context): SQLiteOpenHelper(context,
    ConstValues.volumeDatabaseName, null, 3) {
    companion object {
        const val TABLE_NAME = "Volumes"
        const val COLUMN_NAME = "name"
        const val COLUMN_HIDDEN = "hidden"
        const val COLUMN_HASH = "hash"
        const val COLUMN_IV = "iv"

        private fun contentValuesFromVolume(volume: Volume): ContentValues {
            val contentValues = ContentValues()
            contentValues.put(COLUMN_NAME, volume.name)
            contentValues.put(COLUMN_HIDDEN, volume.isHidden)
            contentValues.put(COLUMN_HASH, volume.hash)
            contentValues.put(COLUMN_IV, volume.iv)
            return contentValues
        }
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_NAME ($COLUMN_NAME TEXT PRIMARY KEY, $COLUMN_HIDDEN SHORT, $COLUMN_HASH BLOB, $COLUMN_IV BLOB);"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun isVolumeSaved(volumeName: String): Boolean {
        val cursor = readableDatabase.query(TABLE_NAME, arrayOf(COLUMN_NAME), "$COLUMN_NAME=?", arrayOf(volumeName), null, null, null)
        val result = cursor.count > 0
        cursor.close()
        return result
    }

    fun saveVolume(volume: Volume): Boolean {
        if (!isVolumeSaved(volume.name)){
            return (writableDatabase.insert(TABLE_NAME, null, contentValuesFromVolume(volume)) == 0.toLong())
        }
        return false
    }

    fun getVolumes(): List<Volume> {
        val list: MutableList<Volume> = ArrayList()
        val cursor = readableDatabase.rawQuery("SELECT * FROM $TABLE_NAME", null)
        while (cursor.moveToNext()){
            list.add(
                Volume(
                    cursor.getString(cursor.getColumnIndex(COLUMN_NAME)),
                    cursor.getShort(cursor.getColumnIndex(COLUMN_HIDDEN)) == 1.toShort(),
                    cursor.getBlob(cursor.getColumnIndex(COLUMN_HASH)),
                    cursor.getBlob(cursor.getColumnIndex(COLUMN_IV))
                )
            )
        }
        cursor.close()
        return list
    }

    fun isHashSaved(volumeName: String): Boolean {
        val cursor = readableDatabase.query(TABLE_NAME, arrayOf(COLUMN_NAME, COLUMN_HASH), "$COLUMN_NAME=?", arrayOf(volumeName), null, null, null)
        var isHashSaved = false
        if (cursor.moveToNext()){
            if (cursor.getBlob(cursor.getColumnIndex(COLUMN_HASH)) != null){
                isHashSaved = true
            }
        }
        cursor.close()
        return isHashSaved
    }

    fun addHash(volume: Volume): Boolean {
        return writableDatabase.update(TABLE_NAME, contentValuesFromVolume(volume), "$COLUMN_NAME=?", arrayOf(volume.name)) > 0
    }

    fun removeHash(volume: Volume): Boolean {
        return writableDatabase.update(
            TABLE_NAME, contentValuesFromVolume(
            Volume(
                volume.name,
                volume.isHidden,
                null,
                null
            )
        ), "$COLUMN_NAME=?", arrayOf(volume.name)) > 0
    }

    fun removeVolume(volume: Volume): Boolean {
        return writableDatabase.delete(TABLE_NAME, "$COLUMN_NAME=?", arrayOf(volume.name)) > 0
    }
}