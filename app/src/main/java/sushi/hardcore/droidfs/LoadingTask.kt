package sushi.hardcore.droidfs

import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder

abstract class LoadingTask(val activity: AppCompatActivity, themeValue: String, loadingMessageResId: Int) {
    private val dialogLoadingView = activity.layoutInflater.inflate(R.layout.dialog_loading, null)
    private val dialogLoading: AlertDialog = CustomAlertDialogBuilder(activity, themeValue)
        .setView(dialogLoadingView)
        .setTitle(R.string.loading)
        .setCancelable(false)
        .create()
    private var isStopped = false
    init {
        dialogLoadingView.findViewById<TextView>(R.id.text_message).text = activity.getString(loadingMessageResId)
        startTask()
    }
    abstract fun doTask(activity: AppCompatActivity)
    open fun doFinally(activity: AppCompatActivity){}
    private fun startTask() {
        dialogLoading.show()
        Thread {
            doTask(activity)
            if (!isStopped){
                dialogLoading.dismiss()
            }
            activity.runOnUiThread { doFinally(activity) }
        }.start()
    }
    fun stopTask(onUiThread: (() -> Unit)?){
        isStopped = true
        dialogLoading.dismiss()
        onUiThread?.let {
            activity.runOnUiThread {
                onUiThread()
            }
        }
    }
    protected fun stopTaskWithToast(stringId: Int){
        stopTask { Toast.makeText(activity, stringId, Toast.LENGTH_SHORT).show() }
    }
}