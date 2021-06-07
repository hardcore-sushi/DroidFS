package sushi.hardcore.droidfs

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        if (!isRecreating) {
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
