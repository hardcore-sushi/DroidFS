package sushi.hardcore.droidfs

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import sushi.hardcore.droidfs.content_providers.TemporaryFileProvider
import sushi.hardcore.droidfs.util.AndroidUtils

class VolumeManagerApp : Application(), DefaultLifecycleObserver {
    companion object {
        const val ACTION_CLOSE_ALL_VOLUMES = "close_all"
    }

    private val closingServiceIntent by lazy {
        Intent(this, ClosingService::class.java)
    }
    private val keepAliveServiceStartIntent by lazy {
        Intent(this, KeepAliveService::class.java).apply {
            action = KeepAliveService.ACTION_START
        }.putExtra(
            "notification", KeepAliveService.NotificationDetails(
                "KeepAlive",
                getString(R.string.keep_alive_notification_title),
                getString(R.string.keep_alive_notification_text),
                KeepAliveService.NotificationDetails.NotificationAction(
                    R.drawable.icon_lock,
                    getString(R.string.close_all),
                    ACTION_CLOSE_ALL_VOLUMES,
                )
            )
        )
    }
    private val usfBackgroundDelegate = AndroidUtils.LiveBooleanPreference("usf_background", false) { _ ->
        updateServicesStates()
    }
    private val usfBackground by usfBackgroundDelegate
    private val usfKeepOpenDelegate = AndroidUtils.LiveBooleanPreference("usf_keep_open", false) { _ ->
        updateServicesStates()
    }
    private val usfKeepOpen by usfKeepOpenDelegate
    var isExporting = false
    var isStartingExternalApp = false
    val volumeManager = VolumeManager(this).also {
        it.observe(object : VolumeManager.Observer {
            override fun onVolumeStateChanged(volume: VolumeData) {
                updateServicesStates()
            }

            override fun onAllVolumesClosed() {
                stopKeepAliveService()
                // closingService should not be running when this callback is triggered
            }
        })
    }

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        AndroidUtils.LiveBooleanPreference.init(this, usfBackgroundDelegate, usfKeepOpenDelegate)
    }

    fun updateServicesStates() {
        if (usfBackground && volumeManager.getVolumeCount() > 0) {
            if (usfKeepOpen) {
                stopService(closingServiceIntent)
                if (!AndroidUtils.isServiceRunning(this@VolumeManagerApp, KeepAliveService::class.java)) {
                    ContextCompat.startForegroundService(this, keepAliveServiceStartIntent)
                }
            } else {
                stopKeepAliveService()
                if (!AndroidUtils.isServiceRunning(this@VolumeManagerApp, ClosingService::class.java)) {
                    startService(closingServiceIntent)
                }
            }
        } else {
            stopService(closingServiceIntent)
            stopKeepAliveService()
        }
    }

    private fun stopKeepAliveService() {
        stopService(Intent(this, KeepAliveService::class.java))
    }

    override fun onResume(owner: LifecycleOwner) {
        isStartingExternalApp = false
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!isStartingExternalApp) {
            if (!usfBackground) {
                volumeManager.closeAll()
            }
            if (!usfBackground || !isExporting) {
                TemporaryFileProvider.instance.wipe()
            }
        }
    }
}