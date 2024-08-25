package sushi.hardcore.droidfs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import sushi.hardcore.droidfs.file_operations.FileOperationService

class NotificationBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FileOperationService.ACTION_CANCEL -> {
                intent.getBundleExtra("bundle")?.let { bundle ->
                    // TODO: use peekService instead?
                    val binder = (bundle.getBinder("binder") as FileOperationService.LocalBinder?)
                    binder?.getService()?.cancelOperation(bundle.getInt("taskId"))
                }
            }
            VolumeManagerApp.ACTION_CLOSE_ALL_VOLUMES -> {
                (context.applicationContext as VolumeManagerApp).volumeManager.closeAll()
            }
        }
    }
}