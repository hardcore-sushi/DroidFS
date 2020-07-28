package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private var usf_fingerprint = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open)
        setSupportActionBar(toolbar)
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

    fun pickDirectory(view: View?) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(i, PICK_DIRECTORY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_DIRECTORY_REQUEST_CODE) {
                if (data != null) {
                    val path = PathUtils.getFullPathFromTreeUri(data.data, this)
                    edit_volume_path.setText(path)
                }
            }
        }
    }

    fun onClickOpen(view: View?) {
        object : LoadingTask(this, R.string.loading_msg_open){
            override fun doTask(activity: AppCompatActivity) {
                rootCipherDir = edit_volume_path.text.toString() //fresh get in case of manual rewrite
                if (rootCipherDir.isEmpty()) {
                    stopTaskWithToast(R.string.enter_volume_path)
                } else {
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
                explorerIntent.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            }
        }
        if (explorerIntent == null) {
            explorerIntent = Intent(this, ExplorerActivity::class.java) //default opening
        }
        explorerIntent.putExtra("sessionID", sessionID)
        explorerIntent.putExtra("volume_name", File(rootCipherDir).name)
        startActivity(explorerIntent)
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

    override fun onPause() {
        super.onPause()
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
    }
}