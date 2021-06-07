package sushi.hardcore.droidfs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_open.*
import kotlinx.android.synthetic.main.checkboxes_section.*
import kotlinx.android.synthetic.main.volume_path_section.*
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerActivityDrop
import sushi.hardcore.droidfs.explorers.ExplorerActivityPick
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class OpenActivity : VolumeActionActivity() {
    private lateinit var savedVolumesAdapter: SavedVolumesAdapter
    private var sessionID = -1
    private var isStartingActivity = false
    private var isFinishingIntentionally = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open)
        setupLayout()
        setupFingerprintStuff()
        savedVolumesAdapter = SavedVolumesAdapter(this, volumeDatabase)
        if (savedVolumesAdapter.count > 0){
            saved_path_listview.adapter = savedVolumesAdapter
            saved_path_listview.onItemClickListener = OnItemClickListener { _, _, position, _ ->
                val volume = savedVolumesAdapter.getItem(position)
                currentVolumeName = volume.name
                if (volume.isHidden){
                    switch_hidden_volume.isChecked = true
                    edit_volume_name.setText(currentVolumeName)
                } else {
                    switch_hidden_volume.isChecked = false
                    edit_volume_path.setText(currentVolumeName)
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
            WidgetUtil.hideWithPadding(saved_path_listview)
        }
        val textWatcher = object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (volumeDatabase.isVolumeSaved(s.toString())){
                    checkbox_remember_path.isEnabled = false
                    checkbox_remember_path.isChecked = false
                    if (volumeDatabase.isHashSaved(s.toString())){
                        checkbox_save_password.isEnabled = false
                        checkbox_save_password.isChecked = false
                    } else {
                        checkbox_save_password.isEnabled = true
                    }
                } else {
                    checkbox_remember_path.isEnabled = true
                    checkbox_save_password.isEnabled = true
                }
            }
        }
        edit_volume_path.addTextChangedListener(textWatcher)
        edit_volume_name.addTextChangedListener(textWatcher)
        edit_password.setOnEditorActionListener { _, _, _ ->
            checkVolumePathThenOpen()
            true
        }
        button_open.setOnClickListener {
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

    override fun onDirectoryPicked(uri: Uri) {
        val path = PathUtils.getFullPathFromTreeUri(uri, this)
        if (path != null){
            edit_volume_path.setText(path)
        } else {
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.path_from_uri_null_error_msg)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    fun checkVolumePathThenOpen() {
        loadVolumePath {
            val volumeFile = File(currentVolumePath)
            if (!GocryptfsVolume.isGocryptfsVolume(volumeFile)){
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.error_not_a_volume)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else if (!volumeFile.canWrite()) {
                if ((intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) && intent.extras != null) { //import via android share menu
                    ColoredAlertDialogBuilder(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.open_cant_write_error_msg)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else {
                    val dialog = ColoredAlertDialogBuilder(this)
                        .setTitle(R.string.warning)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> openVolume() }
                    if (PathUtils.isPathOnExternalStorage(currentVolumeName, this)){
                        dialog.setMessage(R.string.open_on_sdcard_warning)
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
        object : LoadingTask(this, R.string.loading_msg_open){
            override fun doTask(activity: AppCompatActivity) {
                val password = edit_password.text.toString().toCharArray()
                var returnedHash: ByteArray? = null
                if (checkbox_save_password.isChecked){
                    returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                }
                sessionID = GocryptfsVolume.init(currentVolumePath, password, null, returnedHash)
                if (sessionID != -1) {
                    if (checkbox_remember_path.isChecked) {
                        volumeDatabase.saveVolume(Volume(currentVolumeName, switch_hidden_volume.isChecked))
                    }
                    if (checkbox_save_password.isChecked && returnedHash != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                            stopTask {
                                savePasswordHash(returnedHash) { success ->
                                    if (success){
                                        startExplorer()
                                    } else {
                                        GocryptfsVolume(sessionID).close()
                                    }
                                }
                            }
                    } else {
                        stopTask { startExplorer() }
                    }
                } else {
                    stopTask {
                        ColoredAlertDialogBuilder(activity)
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
        object : LoadingTask(this, R.string.loading_msg_open){
            override fun doTask(activity: AppCompatActivity) {
                sessionID = GocryptfsVolume.init(currentVolumePath, null, passwordHash, null)
                if (sessionID != -1){
                    stopTask { startExplorer() }
                } else {
                    stopTask {
                        ColoredAlertDialogBuilder(activity)
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
        Wiper.wipeEditText(edit_password)
        if (intent.action == "pick" && !isFinishingIntentionally){
            val sessionID = intent.getIntExtra("sessionID", -1)
            if (sessionID != -1){
                GocryptfsVolume(sessionID).close()
                RestrictedFileProvider.wipeAll(this)
            }
        }
    }
}