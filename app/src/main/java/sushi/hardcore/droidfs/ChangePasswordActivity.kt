package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_change_password.*
import kotlinx.android.synthetic.main.activity_change_password.checkbox_remember_path
import kotlinx.android.synthetic.main.activity_change_password.checkbox_save_password
import kotlinx.android.synthetic.main.activity_change_password.edit_volume_path
import kotlinx.android.synthetic.main.activity_change_password.saved_path_listview
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.fingerprint_stuff.FingerprintPasswordHashSaver
import sushi.hardcore.droidfs.util.*
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class ChangePasswordActivity : BaseActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
    }
    private lateinit var fingerprintPasswordHashSaver: FingerprintPasswordHashSaver
    private lateinit var rootCipherDir: String
    private var usf_fingerprint = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        usf_fingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && usf_fingerprint) {
            fingerprintPasswordHashSaver = FingerprintPasswordHashSaver(this, sharedPrefs)
        } else {
            WidgetUtil.hide(checkbox_save_password)
        }
        val savedVolumesAdapter = SavedVolumesAdapter(this, sharedPrefs)
        if (savedVolumesAdapter.count > 0){
            saved_path_listview.adapter = savedVolumesAdapter
            saved_path_listview.onItemClickListener = OnItemClickListener { _, _, position, _ ->
                edit_volume_path.setText(savedVolumesAdapter.getItem(position))
            }
        } else {
            WidgetUtil.hide(saved_path_listview)
        }
        edit_volume_path.addTextChangedListener(object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (sharedPrefs.getString(s.toString(), null) == null) {
                    edit_old_password.hint = null
                    edit_old_password.isEnabled = true
                } else {
                    edit_old_password.text = null
                    edit_old_password.hint = getString(R.string.hash_saved_hint)
                    edit_old_password.isEnabled = false
                }
            }
        })
        edit_new_password_confirm.setOnEditorActionListener { v, _, _ ->
            onClickChangePassword(v)
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
                            .setMessage(R.string.change_pwd_on_sdcard_error_msg)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
            }
        }
    }

    fun onClickChangePassword(view: View?) {
        rootCipherDir = edit_volume_path.text.toString()
        if (rootCipherDir.isEmpty()) {
            Toast.makeText(this, R.string.enter_volume_path, Toast.LENGTH_SHORT).show()
        } else {
            if (!File(rootCipherDir).canWrite()){
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.change_pwd_cant_write_error_msg)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                changePassword(null)
            }
        }
    }

    private fun changePassword(givenHash: ByteArray?){
        val newPassword = edit_new_password.text.toString().toCharArray()
        val newPasswordConfirm = edit_new_password_confirm.text.toString().toCharArray()
        if (!newPassword.contentEquals(newPasswordConfirm)) {
            Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
        } else {
            object : LoadingTask(this, R.string.loading_msg_change_password) {
                override fun doTask(activity: AppCompatActivity) {
                    val oldPassword = edit_old_password.text.toString().toCharArray()
                    var returnedHash: ByteArray? = null
                    if (usf_fingerprint && checkbox_save_password.isChecked) {
                        returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                    }
                    var changePasswordImmediately = true
                    if (givenHash == null) {
                        val cipherText = sharedPrefs.getString(rootCipherDir, null)
                        if (cipherText != null) { //password hash saved
                            stopTask {
                                fingerprintPasswordHashSaver.decrypt(cipherText, rootCipherDir, ::changePassword)
                            }
                            changePasswordImmediately = false
                        }
                    }
                    if (changePasswordImmediately) {
                        if (GocryptfsVolume.changePassword(
                                rootCipherDir,
                                oldPassword,
                                givenHash,
                                newPassword,
                                returnedHash
                            )
                        ) {
                            val editor = sharedPrefs.edit()
                            if (sharedPrefs.getString(rootCipherDir, null) != null) {
                                editor.remove(rootCipherDir)
                                editor.apply()
                            }
                            var continueImmediately = true
                            if (checkbox_remember_path.isChecked) {
                                val oldSavedVolumesPaths = sharedPrefs.getStringSet(ConstValues.saved_volumes_key, HashSet()) as Set<String>
                                val newSavedVolumesPaths = oldSavedVolumesPaths.toMutableList()
                                if (!oldSavedVolumesPaths.contains(rootCipherDir)) {
                                    newSavedVolumesPaths.add(rootCipherDir)
                                    editor.putStringSet(
                                        ConstValues.saved_volumes_key,
                                        newSavedVolumesPaths.toSet()
                                    )
                                    editor.apply()
                                }
                                if (checkbox_save_password.isChecked && returnedHash != null) {
                                    fingerprintPasswordHashSaver.encryptAndSave(returnedHash, rootCipherDir) { _ ->
                                        stopTask { onPasswordChanged() }
                                    }
                                    continueImmediately = false
                                }
                            }
                            if (continueImmediately) {
                                stopTask { onPasswordChanged() }
                            }
                        } else {
                            stopTask {
                                ColoredAlertDialogBuilder(activity)
                                    .setTitle(R.string.error)
                                    .setMessage(R.string.change_password_failed)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        }
                    }
                    Arrays.fill(oldPassword, 0.toChar())
                }
                override fun doFinally(activity: AppCompatActivity) {
                    Arrays.fill(newPassword, 0.toChar())
                    Arrays.fill(newPasswordConfirm, 0.toChar())
                }
            }
        }
    }

    private fun onPasswordChanged(){
        ColoredAlertDialogBuilder(this)
                .setTitle(R.string.success_change_password)
                .setMessage(R.string.success_change_password_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> finish() }
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
        Wiper.wipeEditText(edit_old_password)
        Wiper.wipeEditText(edit_new_password)
        Wiper.wipeEditText(edit_new_password_confirm)
    }
}