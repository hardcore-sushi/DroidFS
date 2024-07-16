package sushi.hardcore.droidfs

import android.app.Service
import android.content.Intent

/**
 * Dummy background service listening for application task removal in order to
 * close all volumes still open on quit.
 *
 * Should only be running when usfBackground is enabled AND usfKeepOpen is disabled.
 */
class ClosingService : Service() {
    override fun onBind(intent: Intent) = null

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        (application as VolumeManagerApp).volumeManager.closeAll()
        stopSelf()
    }
}