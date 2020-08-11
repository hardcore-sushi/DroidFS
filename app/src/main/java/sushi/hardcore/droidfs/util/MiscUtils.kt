package sushi.hardcore.droidfs.util

object MiscUtils {
    fun incrementIndex(index: Int, list: List<Any>): Int {
        var i = index+1
        if (i >= list.size){
            i = 0
        }
        return i
    }
    fun decrementIndex(index: Int, list: List<Any>): Int {
        var i = index-1
        if (i < 0){
            i = list.size-1
        }
        return i
    }
}