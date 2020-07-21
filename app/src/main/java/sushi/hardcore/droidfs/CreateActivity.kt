package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_create.*
import kotlinx.android.synthetic.main.activity_create.checkbox_remember_path
import kotlinx.android.synthetic.main.activity_create.checkbox_save_password
import kotlinx.android.synthetic.main.activity_create.edit_password
import kotlinx.android.synthetic.main.activity_create.edit_volume_path
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.fingerprint_stuff.FingerprintPasswordHashSaver
import sushi.hardcore.droidfs.util.FilesUtils
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.io.File
import java.util.*

class CreateActivity : BaseActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
    }
    private lateinit var fingerprintPasswordHashSaver: FingerprintPasswordHashSaver
    private lateinit var root_cipher_dir: String
    private var sessionID = -1
    private var usf_fingerprint = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)
        setSupportActionBar(toolbar)
        usf_fingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && usf_fingerprint) {
            fingerprintPasswordHashSaver = FingerprintPasswordHashSaver(this, sharedPrefs)
        } else {
            WidgetUtil.hide(checkbox_save_password)
        }
        edit_password_confirm.setOnEditorActionListener { v, _, _ ->
            onClickCreate(v)
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
                    val path = FilesUtils.getFullPathFromTreeUri(data.data, this)
                    edit_volume_path.setText(path)
                }
            }
        }
    }

    fun onClickCreate(view: View?) {
        val password = edit_password.text.toString().toCharArray()
        val password_confirm = edit_password_confirm.text.toString().toCharArray()
        if (!password.contentEquals(password_confirm)) {
            Toast.makeText(applicationContext, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
        } else {
            root_cipher_dir = edit_volume_path.text.toString()
            val volume_path_file = File(root_cipher_dir)
            var good_directory = false
            if (!volume_path_file.isDirectory) {
                if (volume_path_file.mkdirs()) {
                    good_directory = true
                } else {
                    Toast.makeText(applicationContext, R.string.error_mkdir, Toast.LENGTH_SHORT).show()
                }
            } else {
                val dir_content = volume_path_file.list()
                if (dir_content != null){
                    if (dir_content.isEmpty()) {
                        good_directory = true
                    } else {
                        Toast.makeText(applicationContext, R.string.dir_not_empty, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(applicationContext, getString(R.string.listdir_null_error_msg), Toast.LENGTH_SHORT).show()
                }
            }
            if (good_directory) {
                if (GocryptfsVolume.create_volume(root_cipher_dir, password, GocryptfsVolume.ScryptDefaultLogN, ConstValues.creator)) {
                    var returnedHash: ByteArray? = null
                    if (usf_fingerprint && checkbox_save_password.isChecked){
                        returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                    }
                    sessionID = GocryptfsVolume.init(root_cipher_dir, password, null, returnedHash)
                    if (sessionID != -1) {
                        var startExplorerImmediately = true
                        if (checkbox_remember_path.isChecked) {
                            val old_saved_volumes_paths = sharedPrefs.getStringSet(ConstValues.saved_volumes_key, HashSet()) as Set<String>
                            val editor = sharedPrefs.edit()
                            val new_saved_volumes_paths = old_saved_volumes_paths.toMutableList()
                            if (old_saved_volumes_paths.contains(root_cipher_dir)) {
                                if (sharedPrefs.getString(root_cipher_dir, null) != null){
                                    editor.remove(root_cipher_dir)
                                }
                            } else {
                                new_saved_volumes_paths.add(root_cipher_dir)
                                editor.putStringSet(ConstValues.saved_volumes_key, new_saved_volumes_paths.toSet())
                            }
                            editor.apply()
                            if (checkbox_save_password.isChecked && returnedHash != null){
                                fingerprintPasswordHashSaver.encryptAndSave(returnedHash, root_cipher_dir){ _ ->
                                    startExplorer()
                                }
                                startExplorerImmediately = false
                            }
                        }
                        if (startExplorerImmediately){
                            startExplorer()
                        }
                    } else {
                        Toast.makeText(this, R.string.open_volume_failed, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.error)
                            .setMessage(R.string.create_volume_failed)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
            }
        }
        Arrays.fill(password, 0.toChar())
        Arrays.fill(password_confirm, 0.toChar())
    }

    fun startExplorer(){
        ColoredAlertDialog(this)
                .setTitle(R.string.success_volume_create)
                .setMessage(R.string.success_volume_create_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val intent = Intent(applicationContext, ExplorerActivity::class.java)
                    intent.putExtra("sessionID", sessionID)
                    intent.putExtra("volume_name", File(root_cipher_dir).name)
                    startActivity(intent)
                    finish()
                }
                .show()
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
        Wiper.wipeEditText(edit_password_confirm)
    }
}
