package sushi.hardcore.droidfs

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider

class VolumeManagerApp : Application(), DefaultLifecycleObserver {
    companion object {
        private const val USF_KEEP_OPEN_KEY = "usf_keep_open"
    }

    lateinit var sharedPreferences: SharedPreferences
    private val sharedPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == USF_KEEP_OPEN_KEY) {
            reloadUsfKeepOpen()
        }
    }
    private var usfKeepOpen = false
    var isStartingExternalApp = false
    val volumeManager = VolumeManager()

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this).apply {
            registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        }
        reloadUsfKeepOpen()
    }

    private fun reloadUsfKeepOpen() {
        usfKeepOpen = sharedPreferences.getBoolean(USF_KEEP_OPEN_KEY, false)
    }

    override fun onResume(owner: LifecycleOwner) {
        isStartingExternalApp = false
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!isStartingExternalApp && !usfKeepOpen) {
            volumeManager.closeAll()
            RestrictedFileProvider.wipeAll(applicationContext)
        }
    }
}