package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_open.*
import kotlinx.android.synthetic.main.checkboxes_section.*
import kotlinx.android.synthetic.main.volume_path_section.*
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerActivityDrop
import sushi.hardcore.droidfs.explorers.ExplorerActivityPick
import sushi.hardcore.droidfs.provider.RestrictedFileProvider
import sushi.hardcore.droidfs.util.*
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class OpenActivity : VolumeActionActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
    }
    private lateinit var savedVolumesAdapter: SavedVolumesAdapter
    private var sessionID = -1
    private var isStartingActivity = false
    private var isFinishingIntentionally = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open)
        setupActionBar()
        setupFingerprintStuff()
        savedVolumesAdapter = SavedVolumesAdapter(this, sharedPrefs)
        if (savedVolumesAdapter.count > 0){
            saved_path_listview.adapter = savedVolumesAdapter
            saved_path_listview.onItemClickListener = OnItemClickListener { _, _, position, _ ->
                rootCipherDir = savedVolumesAdapter.getItem(position)
                edit_volume_path.setText(rootCipherDir)
                val cipherText = sharedPrefs.getString(rootCipherDir, null)
                if (cipherText != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){ //password hash saved
                    loadPasswordHash(cipherText, ::openUsingPasswordHash)
                }
            }
        } else {
            WidgetUtil.hide(saved_path_listview)
        }
        edit_volume_path.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (savedVolumesAdapter.isPathSaved(s.toString())){
                    checkbox_remember_path.isEnabled = false
                    checkbox_remember_path.isChecked = false
                    if (sharedPrefs.getString(s.toString(), null) != null){
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
        })
        edit_password.setOnEditorActionListener { v, _, _ ->
            onClickOpen(v)
            true
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

    fun pickDirectory(view: View?) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        isStartingActivity = true
        startActivityForResult(i, PICK_DIRECTORY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_DIRECTORY_REQUEST_CODE) {
                if (data?.data != null) {
                    val path = PathUtils.getFullPathFromTreeUri(data.data, this)
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
            }
        }
    }

    fun onClickOpen(view: View?) {
        rootCipherDir = edit_volume_path.text.toString()
        if (rootCipherDir.isEmpty()) {
            Toast.makeText(this, R.string.enter_volume_path, Toast.LENGTH_SHORT).show()
        } else {
            val rootCipherDirFile = File(rootCipherDir)
            if (!rootCipherDirFile.canRead()) {
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.open_cant_read_error)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else if (!GocryptfsVolume.isGocryptfsVolume(rootCipherDirFile)){
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.error_not_a_volume)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else if (!rootCipherDirFile.canWrite()) {
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
                    if (PathUtils.isPathOnExternalStorage(rootCipherDir, this)){
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
                sessionID = GocryptfsVolume.init(rootCipherDir, password, null, returnedHash)
                if (sessionID != -1) {
                    if (checkbox_remember_path.isChecked) {
                        savedVolumesAdapter.addVolumePath(rootCipherDir)
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
                sessionID = GocryptfsVolume.init(rootCipherDir, null, passwordHash, null)
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
        explorerIntent.putExtra("volume_name", File(rootCipherDir).name)
        startActivity(explorerIntent)
        isFinishingIntentionally = true
        finish()
    }

    fun onClickRememberPath(view: View) {
        if (!checkbox_remember_path.isChecked){
            checkbox_save_password.isChecked = false
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isFinishingIntentionally = true
    }

    override fun onPause() {
        super.onPause()
        if (intent.action == "pick"){
            if (isStartingActivity){
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