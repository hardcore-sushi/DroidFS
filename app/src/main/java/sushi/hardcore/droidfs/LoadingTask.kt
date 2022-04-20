package sushi.hardcore.droidfs

import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.databinding.DialogLoadingBinding
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder

abstract class LoadingTask<T>(val activity: AppCompatActivity, themeValue: String, loadingMessageResId: Int) {
    private val dialogLoading = CustomAlertDialogBuilder(activity, themeValue)
        .setView(
            DialogLoadingBinding.inflate(activity.layoutInflater).apply {
                textMessage.text = activity.getString(loadingMessageResId)
            }.root
        )
        .setTitle(R.string.loading)
        .setCancelable(false)
        .create()

    abstract suspend fun doTask(): T

    fun startTask(scope: CoroutineScope, onDone: (T) -> Unit) {
        dialogLoading.show()
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                doTask()
            }
            dialogLoading.dismiss()
            onDone(result)
        }
    }
}