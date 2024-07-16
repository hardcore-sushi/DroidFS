package sushi.hardcore.droidfs.util

import android.app.Activity
import sushi.hardcore.droidfs.filesystems.EncryptedVolume

abstract class Observable<T> {
    protected val observers = mutableListOf<T>()

    fun observe(observer: T) {
        observers.add(observer)
    }
}

fun Activity.finishOnClose(encryptedVolume: EncryptedVolume) {
    encryptedVolume.observe(object : EncryptedVolume.Observer {
        override fun onClose() {
            finish()
            // no need to remove observer as the EncryptedVolume will be destroyed
        }
    })
}