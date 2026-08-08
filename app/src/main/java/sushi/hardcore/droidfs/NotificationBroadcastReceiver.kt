package sushi.hardcore.droidfs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            VolumeManagerApp.ACTION_CLOSE_ALL_VOLUMES -> {
                (context.applicationContext as VolumeManagerApp).volumeManager.closeAll()
            }
        }
    }
}