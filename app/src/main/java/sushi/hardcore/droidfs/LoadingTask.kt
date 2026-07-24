package sushi.hardcore.droidfs

import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.databinding.DialogLoadingBinding

abstract class LoadingTask<T>(val activity: FragmentActivity, loadingMessageResId: Int) {
    private val dialogLoading = MaterialAlertDialogBuilder(activity)
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