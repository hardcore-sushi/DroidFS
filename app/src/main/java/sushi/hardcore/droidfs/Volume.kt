package sushi.hardcore.droidfs

class Volume(val name: String, val isHidden: Boolean = false, var hash: ByteArray? = null, var iv: ByteArray? = null)