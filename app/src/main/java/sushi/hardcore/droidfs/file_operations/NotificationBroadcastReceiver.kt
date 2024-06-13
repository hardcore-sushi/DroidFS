package sushi.hardcore.droidfs.file_operations

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationBroadcastReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == FileOperationService.ACTION_CANCEL) {
            intent.getBundleExtra("bundle")?.let { bundle ->
                // TODO: use peekService instead?
                val binder = (bundle.getBinder("binder") as FileOperationService.LocalBinder?)
                binder?.getService()?.cancelOperation(bundle.getInt("taskId"))
            }
        }
    }
}