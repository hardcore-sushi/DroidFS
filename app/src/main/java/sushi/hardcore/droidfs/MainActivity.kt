package sushi.hardcore.droidfs

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import sushi.hardcore.droidfs.databinding.ActivityMainBinding
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar.toolbar)
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
        binding.buttonOpen.setOnClickListener {
            startActivity(OpenActivity::class.java)
        }
        binding.buttonCreate.setOnClickListener {
            startActivity(CreateActivity::class.java)
        }
        binding.buttonChangePassword.setOnClickListener {
            startActivity(ChangePasswordActivity::class.java)
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

    fun <T> startActivity(clazz: Class<T>) {
        val intent = Intent(this, clazz)
        startActivity(intent)
    }
}
