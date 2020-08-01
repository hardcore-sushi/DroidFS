package sushi.hardcore.droidfs.util

object SQLUtil {
    @JvmStatic
    fun concatenateWhere(a: String, b: String): String {
        if (a.isEmpty()) {
            return b
        }
        return if (b.isEmpty()) {
            a
        } else "($a) AND ($b)"
    }

    @JvmStatic
    fun appendSelectionArgs(originalValues: Array<String>?, newValues: Array<String>): Array<String> {
        if (originalValues == null || originalValues.isEmpty()) {
            return newValues
        }
        val result = Array(originalValues.size + newValues.size){ "it = $it" }
        System.arraycopy(originalValues, 0, result, 0, originalValues.size)
        System.arraycopy(newValues, 0, result, originalValues.size, newValues.size)
        return result
    }
}