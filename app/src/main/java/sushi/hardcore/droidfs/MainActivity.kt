package sushi.hardcore.droidfs

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.view.*
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

class MainActivity : BaseActivity() {
    companion object {
        private const val STORAGE_PERMISSIONS_REQUEST = 1
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) +
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSIONS_REQUEST)
            } else {
                if (checkStorageAvailability()){
                    checkFirstOpening()
                }
            }
        }
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        image_logo.layoutParams.height = (metrics.heightPixels/2.2).toInt()
        Glide.with(this).load(R.drawable.logo).into(image_logo)
    }

    private fun checkStorageAvailability(): Boolean {
        val state = Environment.getExternalStorageState()
        val storageAvailable = Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
        return if (!storageAvailable) {
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.storage_unavailable)
                .setMessage(R.string.storage_unavailable_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> finish() }.show()
            false
        } else {
            true
        }
    }

    private fun checkFirstOpening() {
        if (sharedPrefs.getBoolean("applicationFirstOpening", true)){
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.usf_home_warning_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.see_unsafe_features){ _, _ ->
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra("screen", "UnsafeFeaturesSettingsFragment")
                    startActivity(intent)
                }
                .setNegativeButton(R.string.ok, null)
                .setOnDismissListener { sharedPrefs.edit().putBoolean("applicationFirstOpening", false).apply() }
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            STORAGE_PERMISSIONS_REQUEST -> if (grantResults.size == 2) {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    ColoredAlertDialogBuilder(this)
                         .setTitle(R.string.storage_perm_denied)
                         .setMessage(R.string.storage_perm_denied_msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }.show()
                } else {
                    checkStorageAvailability()
                    checkFirstOpening()
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        return true
    }

    fun onClickCreate(v: View?) {
        val intent = Intent(this, CreateActivity::class.java)
        startActivity(intent)
    }

    fun onClickOpen(v: View?) {
        val intent = Intent(this, OpenActivity::class.java)
        startActivity(intent)
    }

    fun onClickChangePassword(v: View?) {
        val intent = Intent(this, ChangePasswordActivity::class.java)
        startActivity(intent)
    }
}
