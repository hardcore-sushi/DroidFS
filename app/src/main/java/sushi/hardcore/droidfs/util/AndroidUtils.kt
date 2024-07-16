package sushi.hardcore.droidfs.util

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlin.reflect.KProperty

object AndroidUtils {
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * A [Manifest.permission.POST_NOTIFICATIONS] permission helper.
     *
     * Must be initialized before [Activity.onCreate].
     */
    class NotificationPermissionHelper<A: AppCompatActivity>(val activity: A) {
        private var listener: ((Boolean) -> Unit)? = null
        private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            listener?.invoke(granted)
            listener = null
        }

        /**
         * Ask for notification permission if required and run the provided callback.
         *
         * The callback is run as soon as the user dismisses the permission dialog,
         * no matter if the permission has been granted or not.
         *
         * If this function is called again before the user answered the dialog from the
         * previous call, the previous callback won't be triggered.
         *
         * @param onDialogDismiss argument set to `true` if the permission is granted or
         * not required, `false` otherwise
         */
        fun askAndRun(onDialogDismiss: (Boolean) -> Unit) {
            assert(listener == null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    listener = onDialogDismiss
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            onDialogDismiss(true)
        }
    }

    /**
     * Property delegate mirroring the state of a boolean value in shared preferences.
     *
     * [init] **must** be called before accessing the delegated property.
     */
    class LiveBooleanPreference(
        private val key: String,
        private val defaultValue: Boolean = false,
        private val onChange: ((value: Boolean) -> Unit)? = null
    ) {
        private lateinit var sharedPreferences: SharedPreferences
        private var value = defaultValue
        private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == this.key) {
                reload()
                onChange?.invoke(value)
            }
        }

        fun init(context: Context) = init(PreferenceManager.getDefaultSharedPreferences(context))

        fun init(sharedPreferences: SharedPreferences) {
            this.sharedPreferences = sharedPreferences
            reload()
            sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        }

        private fun reload() {
            value = sharedPreferences.getBoolean(key, defaultValue)
        }

        operator fun getValue(thisRef: Any, property: KProperty<*>) = value

        companion object {
            fun init(context: Context, vararg liveBooleanPreferences: LiveBooleanPreference) {
                init(PreferenceManager.getDefaultSharedPreferences(context), *liveBooleanPreferences)
            }

            fun init(sharedPreferences: SharedPreferences, vararg liveBooleanPreferences: LiveBooleanPreference) {
                for (i in liveBooleanPreferences) {
                    i.init(sharedPreferences)
                }
            }
        }
    }
}