package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_create.*
import kotlinx.android.synthetic.main.activity_create.checkbox_remember_path
import kotlinx.android.synthetic.main.activity_create.checkbox_save_password
import kotlinx.android.synthetic.main.activity_create.edit_password
import kotlinx.android.synthetic.main.activity_create.edit_volume_path
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.fingerprint_stuff.FingerprintPasswordHashSaver
import sushi.hardcore.droidfs.util.*
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class CreateActivity : BaseActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
    }
    private lateinit var fingerprintPasswordHashSaver: FingerprintPasswordHashSaver
    private lateinit var rootCipherDir: String
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
                    val path = PathUtils.getFullPathFromTreeUri(data.data, this)
                    edit_volume_path.setText(path)
                }
            }
        }
    }

    fun onClickCreate(view: View?) {
        object: LoadingTask(this, R.string.loading_msg_create){
            override fun doTask(activity: AppCompatActivity) {
                val password = edit_password.text.toString().toCharArray()
                val passwordConfirm = edit_password_confirm.text.toString().toCharArray()
                if (!password.contentEquals(passwordConfirm)) {
                    stopTaskWithToast(R.string.passwords_mismatch)
                } else {
                    rootCipherDir = edit_volume_path.text.toString()
                    val volumePathFile = File(rootCipherDir)
                    var goodDirectory = false
                    if (!volumePathFile.isDirectory) {
                        if (volumePathFile.mkdirs()) {
                            goodDirectory = true
                        } else {
                            stopTaskWithToast(R.string.error_mkdir)
                        }
                    } else {
                        val dirContent = volumePathFile.list()
                        if (dirContent != null){
                            if (dirContent.isEmpty()) {
                                goodDirectory = true
                            } else {
                                stopTaskWithToast(R.string.dir_not_empty)
                            }
                        } else {
                            stopTaskWithToast(R.string.listdir_null_error_msg)
                        }
                    }
                    if (goodDirectory) {
                        if (GocryptfsVolume.create_volume(rootCipherDir, password, GocryptfsVolume.ScryptDefaultLogN, ConstValues.creator)) {
                            var returnedHash: ByteArray? = null
                            if (usf_fingerprint && checkbox_save_password.isChecked){
                                returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                            }
                            sessionID = GocryptfsVolume.init(rootCipherDir, password, null, returnedHash)
                            if (sessionID != -1) {
                                var startExplorerImmediately = true
                                if (checkbox_remember_path.isChecked) {
                                    val oldSavedVolumesPaths = sharedPrefs.getStringSet(ConstValues.saved_volumes_key, HashSet()) as Set<String>
                                    val editor = sharedPrefs.edit()
                                    val newSavedVolumesPaths = oldSavedVolumesPaths.toMutableList()
                                    if (oldSavedVolumesPaths.contains(rootCipherDir)) {
                                        if (sharedPrefs.getString(rootCipherDir, null) != null){
                                            editor.remove(rootCipherDir)
                                        }
                                    } else {
                                        newSavedVolumesPaths.add(rootCipherDir)
                                        editor.putStringSet(ConstValues.saved_volumes_key, newSavedVolumesPaths.toSet())
                                    }
                                    editor.apply()
                                    if (checkbox_save_password.isChecked && returnedHash != null){
                                        fingerprintPasswordHashSaver.encryptAndSave(returnedHash, rootCipherDir){ _ ->
                                            stopTask { startExplorer() }
                                        }
                                        startExplorerImmediately = false
                                    }
                                }
                                if (startExplorerImmediately){
                                    stopTask { startExplorer() }
                                }
                            } else {
                                stopTaskWithToast(R.string.open_volume_failed)
                            }
                        } else {
                            stopTask {
                                ColoredAlertDialogBuilder(activity)
                                    .setTitle(R.string.error)
                                    .setMessage(R.string.create_volume_failed)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    }
                }
                Arrays.fill(password, 0.toChar())
                Arrays.fill(passwordConfirm, 0.toChar())
            }
        }
    }

    private fun startExplorer(){
        ColoredAlertDialogBuilder(this)
                .setTitle(R.string.success_volume_create)
                .setMessage(R.string.success_volume_create_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val intent = Intent(applicationContext, ExplorerActivity::class.java)
                    intent.putExtra("sessionID", sessionID)
                    intent.putExtra("volume_name", File(rootCipherDir).name)
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
