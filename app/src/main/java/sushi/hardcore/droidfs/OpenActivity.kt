package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_open.*
import kotlinx.android.synthetic.main.activity_open.checkbox_remember_path
import kotlinx.android.synthetic.main.activity_open.checkbox_save_password
import kotlinx.android.synthetic.main.activity_open.edit_password
import kotlinx.android.synthetic.main.activity_open.edit_volume_path
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.explorers.ExplorerActivityDrop
import sushi.hardcore.droidfs.explorers.ExplorerActivityPick
import sushi.hardcore.droidfs.fingerprint_stuff.FingerprintPasswordHashSaver
import sushi.hardcore.droidfs.util.FilesUtils
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.io.File
import java.util.*

class OpenActivity : ColoredActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
    }
    private lateinit var savedVolumesAdapter: SavedVolumesAdapter
    private lateinit var fingerprintPasswordHashSaver: FingerprintPasswordHashSaver
    private lateinit var root_cipher_dir: String
    private var sessionID = -1
    private var usf_fingerprint = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open)
        setSupportActionBar(toolbar)
        //val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        usf_fingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fingerprintPasswordHashSaver = FingerprintPasswordHashSaver(this, sharedPrefs)
            if (!usf_fingerprint){
                WidgetUtil.hide(checkbox_save_password)
            }
        } else {
            WidgetUtil.hide(checkbox_save_password)
        }
        savedVolumesAdapter = SavedVolumesAdapter(this, sharedPrefs)
        if (savedVolumesAdapter.count > 0){
            saved_path_listview.adapter = savedVolumesAdapter
            saved_path_listview.onItemClickListener = OnItemClickListener { _, _, position, _ ->
                root_cipher_dir = savedVolumesAdapter.getItem(position)
                edit_volume_path.setText(root_cipher_dir)
                val cipherText = sharedPrefs.getString(root_cipher_dir, null)
                if (cipherText != null){ //password hash saved
                    fingerprintPasswordHashSaver.decrypt(cipherText, root_cipher_dir, ::openUsingPasswordHash)
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

    fun pick_directory(view: View?) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(i, PICK_DIRECTORY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == PICK_DIRECTORY_REQUEST_CODE) {
                if (data != null) {
                    val path = FilesUtils.getFullPathFromTreeUri(data.data, this)
                    edit_volume_path.setText(path)
                }
            }
        }
    }

    fun onClickOpen(view: View?) {
        root_cipher_dir = edit_volume_path.text.toString() //fresh get in case of manual rewrite
        if (root_cipher_dir.isEmpty()) {
            Toast.makeText(this, R.string.enter_volume_path, Toast.LENGTH_SHORT).show()
        } else {
            val password = edit_password.text.toString().toCharArray()
            var returnedHash: ByteArray? = null
            if (usf_fingerprint && checkbox_save_password.isChecked){
                returnedHash = ByteArray(GocryptfsVolume.KeyLen)
            }
            sessionID = GocryptfsVolume.init(root_cipher_dir, password, null, returnedHash)
            if (sessionID != -1) {
                var startExplorerImmediately = true
                if (checkbox_remember_path.isChecked) {
                    savedVolumesAdapter.addVolumePath(root_cipher_dir)
                    if (checkbox_save_password.isChecked && returnedHash != null){
                        fingerprintPasswordHashSaver.encryptAndSave(returnedHash, root_cipher_dir) {success ->
                            if (success){
                                startExplorer()
                            }
                        }
                        startExplorerImmediately = false
                    }
                }
                if (startExplorerImmediately){
                    startExplorer()
                }
            } else {
                ColoredAlertDialog(this)
                        .setTitle(R.string.open_volume_failed)
                        .setMessage(R.string.open_volume_failed_msg)
                        .setPositiveButton(R.string.ok, null)
                        .show()
            }
            Arrays.fill(password, 0.toChar())
        }
    }

    private fun openUsingPasswordHash(passwordHash: ByteArray){
        sessionID = GocryptfsVolume.init(root_cipher_dir, null, passwordHash, null)
        if (sessionID != -1){
            startExplorer()
        } else {
            ColoredAlertDialog(this)
                    .setTitle(R.string.open_volume_failed)
                    .setMessage(getString(R.string.open_failed_hash_msg))
                    .setPositiveButton(R.string.ok, null)
                    .show()
        }
        Arrays.fill(passwordHash, 0)
    }

    private fun startExplorer() {
        var explorer_intent: Intent? = null
        val current_intent_action = intent.action
        if (current_intent_action != null) {
            if ((current_intent_action == Intent.ACTION_SEND || current_intent_action == Intent.ACTION_SEND_MULTIPLE) && intent.extras != null) { //import via android share menu
                explorer_intent = Intent(this, ExplorerActivityDrop::class.java)
                explorer_intent.action = current_intent_action //forward action
                explorer_intent.putExtras(intent.extras!!) //forward extras
            } else if (current_intent_action == "pick") { //pick items to import
                explorer_intent = Intent(this, ExplorerActivityPick::class.java)
                explorer_intent.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
            }
        }
        if (explorer_intent == null) {
            explorer_intent = Intent(this, ExplorerActivity::class.java) //default opening
        }
        explorer_intent.putExtra("sessionID", sessionID)
        explorer_intent.putExtra("volume_name", File(root_cipher_dir).name)
        startActivity(explorer_intent)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && fingerprintPasswordHashSaver.isListening){
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