package sushi.hardcore.droidfs

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.databinding.ActivityLogcatBinding
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.InterruptedIOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogcatActivity: BaseActivity() {
    private lateinit var binding: ActivityLogcatBinding
    private var process: Process? = null
    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", Locale.getDefault())
    }
    private val saveAs = registerForActivityResult(ActivityResultContracts.CreateDocument("text/*")) { uri ->
        uri?.let {
            saveTo(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogcatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.logcat_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                BufferedReader(InputStreamReader(ProcessBuilder("logcat", "-T", "500").start().also {
                    process = it
                }.inputStream)).lineSequence().forEach {
                    binding.content.append(it)
                }
            } catch (_: InterruptedIOException) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        process?.destroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.logcat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.save -> {
                saveAs.launch("DroidFS_${dateFormat.format(Date())}.log")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveTo(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            contentResolver.openOutputStream(uri)?.use { output ->
                Runtime.getRuntime().exec("logcat -d").inputStream.use { input ->
                    input.copyTo(output)
                }
                launch(Dispatchers.Main) {
                    Toast.makeText(this@LogcatActivity, R.string.logcat_saved, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}