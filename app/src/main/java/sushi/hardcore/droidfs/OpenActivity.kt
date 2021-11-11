package sushi.hardcore.droidfs

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.databinding.ActivityOpenBinding
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerActivityDrop
import sushi.hardcore.droidfs.explorers.ExplorerActivityPick
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File
import java.util.*

class OpenActivity : VolumeActionActivity() {
    private lateinit var savedVolumesAdapter: SavedVolumesAdapter
    private var sessionID = -1
    private var isStartingActivity = false
    private var isFinishingIntentionally = false
    private lateinit var binding: ActivityOpenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOpenBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupLayout()
        setupFingerprintStuff()
        savedVolumesAdapter = SavedVolumesAdapter(this, themeValue, volumeDatabase)
        if (savedVolumesAdapter.count > 0){
            binding.savedPathListview.adapter = savedVolumesAdapter
            binding.savedPathListview.onItemClickListener = OnItemClickListener { _, _, position, _ ->
                val volume = savedVolumesAdapter.getItem(position)
                currentVolumeName = volume.name
                if (volume.isHidden){
                    switchHiddenVolume.isChecked = true
                    editVolumeName.setText(currentVolumeName)
                } else {
                    switchHiddenVolume.isChecked = false
                    editVolumePath.setText(currentVolumeName)
                }
                onClickSwitchHiddenVolume()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                    volume.hash?.let { hash ->
                        volume.iv?.let { iv ->
                            currentVolumePath = if (volume.isHidden){
                                PathUtils.pathJoin(filesDir.path, volume.name)
                            } else {
                                volume.name
                            }
                            loadPasswordHash(hash, iv, ::openUsingPasswordHash)
                        }
                    }
                }
            }
        } else {
            WidgetUtil.hideWithPadding(binding.savedPathListview)
        }
        val textWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (volumeDatabase.isVolumeSaved(s.toString())){
                    checkboxRememberPath.isEnabled = false
                    checkboxRememberPath.isChecked = true
                    if (volumeDatabase.isHashSaved(s.toString())){
                        checkboxSavePassword.isEnabled = false
                        checkboxSavePassword.isChecked = true
                    } else {
                        checkboxSavePassword.isEnabled = true
                    }
                } else {
                    checkboxRememberPath.isEnabled = true
                    checkboxSavePassword.isEnabled = true
                }
            }
        }
        editVolumePath.addTextChangedListener(textWatcher)
        editVolumeName.addTextChangedListener(textWatcher)
        binding.editPassword.setOnEditorActionListener { _, _, _ ->
            checkVolumePathThenOpen()
            true
        }
        binding.buttonOpen.setOnClickListener {
            checkVolumePathThenOpen()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            android.R.id.home -> {
                isFinishingIntentionally = true
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onPickingDirectory() {
        isStartingActivity = true
    }

    fun checkVolumePathThenOpen() {
        loadVolumePath {
            val volumeFile = File(currentVolumePath)
            if (!GocryptfsVolume.isGocryptfsVolume(volumeFile)){
                CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.error)
                    .setMessage(R.string.error_not_a_volume)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else if (!volumeFile.canWrite()) {
                if ((intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) && intent.extras != null) { //import via android share menu
                    CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.error)
                        .setMessage(R.string.open_cant_write_error_msg)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else {
                    val dialog = CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.warning)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> openVolume() }
                    if (PathUtils.isPathOnExternalStorage(currentVolumeName, this)){
                        dialog.setView(
                            layoutInflater.inflate(R.layout.dialog_sdcard_error, null).apply {
                                findViewById<TextView>(R.id.path).text = PathUtils.getPackageDataFolder(this@OpenActivity)
                                findViewById<TextView>(R.id.footer).text = getString(R.string.open_read_only)
                            }
                        )
                    } else {
                        dialog.setMessage(R.string.open_cant_write_warning)
                    }
                    dialog.show()
                }
            } else {
                openVolume()
            }
        }
    }

    private fun openVolume(){
        object : LoadingTask(this, themeValue, R.string.loading_msg_open) {
            override fun doTask(activity: AppCompatActivity) {
                val password = binding.editPassword.text.toString().toCharArray()
                var returnedHash: ByteArray? = null
                if (checkboxSavePassword.isChecked && usf_fingerprint) {
                    returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                }
                sessionID = GocryptfsVolume.init(currentVolumePath, password, null, returnedHash)
                if (sessionID != -1) {
                    if (checkboxRememberPath.isChecked) {
                        volumeDatabase.saveVolume(Volume(currentVolumeName, switchHiddenVolume.isChecked))
                    }
                    if (checkboxSavePassword.isChecked && returnedHash != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                            stopTask {
                                savePasswordHash(returnedHash) { success ->
                                    if (success){
                                        startExplorer()
                                    } else {
                                        GocryptfsVolume(applicationContext, sessionID).close()
                                    }
                                }
                            }
                    } else {
                        stopTask { startExplorer() }
                    }
                } else {
                    stopTask {
                        CustomAlertDialogBuilder(activity, themeValue)
                            .setTitle(R.string.open_volume_failed)
                            .setMessage(R.string.open_volume_failed_msg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
                Arrays.fill(password, 0.toChar())
            }
        }
    }

    private fun openUsingPasswordHash(passwordHash: ByteArray){
        object : LoadingTask(this, themeValue, R.string.loading_msg_open) {
            override fun doTask(activity: AppCompatActivity) {
                sessionID = GocryptfsVolume.init(currentVolumePath, null, passwordHash, null)
                if (sessionID != -1){
                    stopTask { startExplorer() }
                } else {
                    stopTask {
                        CustomAlertDialogBuilder(activity, themeValue)
                            .setTitle(R.string.open_volume_failed)
                            .setMessage(R.string.open_failed_hash_msg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
                Arrays.fill(passwordHash, 0)
            }
        }
    }

    private fun startExplorer() {
        var explorerIntent: Intent? = null
        val currentIntentAction = intent.action
        if (currentIntentAction != null) {
            if ((currentIntentAction == Intent.ACTION_SEND || currentIntentAction == Intent.ACTION_SEND_MULTIPLE) && intent.extras != null) { //import via android share menu
                explorerIntent = Intent(this, ExplorerActivityDrop::class.java)
                explorerIntent.action = currentIntentAction //forward action
                explorerIntent.putExtras(intent.extras!!) //forward extras
            } else if (currentIntentAction == "pick") { //pick items to import
                explorerIntent = Intent(this, ExplorerActivityPick::class.java)
                explorerIntent.putExtra("originalSessionID", intent.getIntExtra("sessionID", -1))
                explorerIntent.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            }
        }
        if (explorerIntent == null) {
            explorerIntent = Intent(this, ExplorerActivity::class.java) //default opening
        }
        explorerIntent.putExtra("sessionID", sessionID)
        explorerIntent.putExtra("volume_name", File(currentVolumeName).name)
        startActivity(explorerIntent)
        isFinishingIntentionally = true
        finish()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isFinishingIntentionally = true
    }

    override fun onStop() {
        super.onStop()
        if (intent.action == "pick"){
            if (isStartingActivity) {
                isStartingActivity = false
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Wiper.wipeEditText(binding.editPassword)
        if (intent.action == "pick" && !isFinishingIntentionally){
            val sessionID = intent.getIntExtra("sessionID", -1)
            if (sessionID != -1){
                GocryptfsVolume(applicationContext, sessionID).close()
                RestrictedFileProvider.wipeAll(this)
            }
        }
    }
}