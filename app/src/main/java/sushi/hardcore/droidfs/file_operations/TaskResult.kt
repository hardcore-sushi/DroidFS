package sushi.hardcore.droidfs.file_operations

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.Theme

class TaskResult<T> private constructor(val state: State, val failedItem: T?, val errorMessage: String?) {
    enum class State {
        SUCCESS,
        /**
         * Task completed but failed
         */
        FAILED,
        /**
         * Task thrown an exception
         */
        ERROR,
        CANCELLED,
    }

    fun showErrorAlertDialog(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.error)
            .setMessage(context.getString(R.string.task_failed, errorMessage))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    companion object {
        fun <T> completed(failedItem: T?): TaskResult<T> {
            return if (failedItem == null) {
                TaskResult(State.SUCCESS, null, null)
            } else {
                TaskResult(State.FAILED, failedItem, null)
            }
        }

        fun <T> error(errorMessage: String?): TaskResult<T> {
            return TaskResult(State.ERROR, null, errorMessage)
        }

        fun <T> cancelled(): TaskResult<T> {
            return TaskResult(State.CANCELLED, null, null)
        }
    }
}