package sushi.hardcore.droidfs

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WiperService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        (application as VolumeManagerApp).volumeManager.closeAll()
        stopSelf()
    }
}