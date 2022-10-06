package sushi.hardcore.droidfs.util

import android.content.Intent
import android.os.Build
import android.os.Parcelable

object IntentUtils {
    inline fun <reified T: Parcelable> getParcelableExtra(intent: Intent, name: String): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("Deprecation")
            intent.getParcelableExtra(name)
        }
    }

    fun forwardIntent(sourceIntent: Intent, targetIntent: Intent) {
        targetIntent.action = sourceIntent.action
        sourceIntent.extras?.let { targetIntent.putExtras(it) }
    }
}