/*
 * Code borrowed from the awesome Material Files app (https://github.com/zhanghai/MaterialFiles)
 *
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package sushi.hardcore.droidfs.collation

import kotlin.math.min

class ByteString internal constructor(
    private val bytes: ByteArray
) : Comparable<ByteString> {

    fun borrowBytes(): ByteArray = bytes

    private var stringCache: String? = null

    override fun toString(): String {
        // We are okay with the potential race condition here.
        var string = stringCache
        if (string == null) {
            // String() uses replacement char instead of throwing exception.
            string = String(bytes)
            stringCache = string
        }
        return string
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }
        other as ByteString
        return bytes contentEquals other.bytes
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun compareTo(other: ByteString): Int = bytes.compareTo(other.bytes)

    private fun ByteArray.compareTo(other: ByteArray): Int {
        val size = size
        val otherSize = other.size
        for (index in 0 until min(size, otherSize)) {
            val byte = this[index]
            val otherByte = other[index]
            val result = byte - otherByte
            if (result != 0) {
                return result
            }
        }
        return size - otherSize
    }

    companion object {
        fun fromBytes(bytes: ByteArray, start: Int = 0, end: Int = bytes.size): ByteString =
            ByteString(bytes.copyOfRange(start, end))

        fun fromString(string: String): ByteString =
            ByteString(string.toByteArray()).apply { stringCache = string }
    }
}

fun ByteArray.toByteString(start: Int = 0, end: Int = size): ByteString =
    ByteString.fromBytes(this, start, end)

fun String.toByteString(): ByteString = ByteString.fromString(this)
