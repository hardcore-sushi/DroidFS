package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_change_password.*
import kotlinx.android.synthetic.main.activity_create.*
import kotlinx.android.synthetic.main.activity_open.*
import kotlinx.android.synthetic.main.activity_open.checkbox_remember_path
import kotlinx.android.synthetic.main.activity_open.checkbox_save_password
import kotlinx.android.synthetic.main.activity_open.edit_password
import kotlinx.android.synthetic.main.activity_open.edit_volume_path
import kotlinx.android.synthetic.main.activity_open.saved_path_listview
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerActivityDrop
import sushi.hardcore.droidfs.explorers.ExplorerActivityPick
import sushi.hardcore.droidfs.fingerprint_stuff.FingerprintPasswordHashSaver
import sushi.hardcore.droidfs.provider.RestrictedFileProvider
import sushi.hardcore.droidfs.util.*
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class OpenActivity : BaseActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
    }
    private lateinit var savedVolumesAdapter: SavedVolumesAdapter
    private lateinit var fingerprintPasswordHashSaver: FingerprintPasswordHashSaver
    private lateinit var rootCipherDir: String
    private var sessionID = -1
    private var isStartingActivity = false
    private var isFinishingIntentionally = false
    private var usf_fingerprint = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        usf_fingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && usf_fingerprint) {
            fingerprintPasswordHashSaver = FingerprintPasswordHashSaver(this, sharedPrefs)
        } else {
            WidgetUtil.hide(checkbox_save_password)
        }
        savedVolumesAdapter = SavedVolumesAdapter(this, sharedPrefs)
        if (savedVolumesAdapter.count > 0){
            saved_path_listview.adapter = savedVolumesAdapter
            saved_path_listview.onItemClickListener = OnItemClickListener { _, _, position, _ ->
                rootCipherDir = savedVolumesAdapter.getItem(position)
                edit_volume_path.setText(rootCipherDir)
                val cipherText = sharedPrefs.getString(rootCipherDir, null)
                if (cipherText != null){ //password hash saved
                    fingerprintPasswordHashSaver.decrypt(cipherText, rootCipherDir, ::openUsingPasswordHash)
                }
            }
        } else {
            WidgetUtil.hide(saved_path_listview)
        }
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
                    if (PathUtils.isTreeUriOnPrimaryStorage(data.data)){
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
                    } else {
                        ColoredAlertDialogBuilder(this)
                            .setTitle(R.string.warning)
                            .setMessage(R.string.open_on_sdcard_warning)
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
            if (!File(rootCipherDir).canWrite()){
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.open_cant_write_warning)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok) { _, _ -> openVolume() }
                    .show()
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
                if (usf_fingerprint && checkbox_save_password.isChecked){
                    returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                }
                sessionID = GocryptfsVolume.init(rootCipherDir, password, null, returnedHash)
                if (sessionID != -1) {
                    var startExplorerImmediately = true
                    if (checkbox_remember_path.isChecked) {
                        savedVolumesAdapter.addVolumePath(rootCipherDir)
                        if (checkbox_save_password.isChecked && returnedHash != null){
                            fingerprintPasswordHashSaver.encryptAndSave(returnedHash, rootCipherDir) { _ ->
                                stopTask { startExplorer() }
                            }
                            startExplorerImmediately = false
                        }
                    }
                    if (startExplorerImmediately){
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

    fun onClickSavePasswordHash(view: View) {
        if (checkbox_save_password.isChecked){
            if (!fingerprintPasswordHashSaver.canAuthenticate()){
                checkbox_save_password.isChecked = false
            } else {
                checkbox_remember_path.isChecked = true
            }
        }
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
        if (::fingerprintPasswordHashSaver.isInitialized && fingerprintPasswordHashSaver.isListening){
            fingerprintPasswordHashSaver.stopListening()
            if (fingerprintPasswordHashSaver.fingerprintFragment.isAdded){
                fingerprintPasswordHashSaver.fingerprintFragment.dismiss()
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