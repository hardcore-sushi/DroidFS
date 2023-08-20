package sushi.hardcore.droidfs.util

import java.lang.Integer.max

class Version(inputVersion: String) : Comparable<Version> {
    private var version: String

    init {
        val regex = "[0-9]+(\\.[0-9]+)*".toRegex()
        val match = regex.find(inputVersion) ?: throw IllegalArgumentException("Invalid version format")
        version = match.value
    }

    fun split() = version.split(".").toTypedArray()

    override fun compareTo(other: Version) =
        (split() to other.split()).let { (split, otherSplit) ->
            val length = max(split.size, otherSplit.size)
            for (i in 0 until length) {
                val part = if (i < split.size) split[i].toInt() else 0
                val otherPart = if (i < otherSplit.size) otherSplit[i].toInt() else 0
                if (part < otherPart) return -1
                if (part > otherPart) return 1
            }
            0
        }
}