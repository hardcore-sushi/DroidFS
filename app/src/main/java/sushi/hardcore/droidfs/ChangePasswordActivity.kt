package sushi.hardcore.droidfs

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_change_password.*
import kotlinx.android.synthetic.main.activity_change_password.checkbox_remember_path
import kotlinx.android.synthetic.main.activity_change_password.checkbox_save_password
import kotlinx.android.synthetic.main.activity_change_password.edit_volume_path
import kotlinx.android.synthetic.main.activity_change_password.saved_path_listview
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.fingerprint_stuff.FingerprintPasswordHashSaver
import sushi.hardcore.droidfs.util.FilesUtils
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.util.*

class ChangePasswordActivity : ColoredActivity() {
    companion object {
        private const val PICK_DIRECTORY_REQUEST_CODE = 1
    }
    private lateinit var fingerprintPasswordHashSaver: FingerprintPasswordHashSaver
    private lateinit var root_cipher_dir: String
    private var usf_fingerprint = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!sharedPrefs.getBoolean("usf_screenshot", false)){
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContentView(R.layout.activity_change_password)
        setSupportActionBar(toolbar)
        usf_fingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fingerprintPasswordHashSaver = FingerprintPasswordHashSaver(this, sharedPrefs)
            if (!usf_fingerprint){
                WidgetUtil.hide(checkbox_save_password)
            }
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
                } else {
                    edit_old_password.hint = getString(R.string.hash_saved_hint)
                }
            }
        })
        edit_new_password_confirm.setOnEditorActionListener { v, _, _ ->
            onClickChangePassword(v)
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

    fun onClickChangePassword(view: View?) {
        root_cipher_dir = edit_volume_path.text.toString()
        if (root_cipher_dir.isEmpty()) {
            Toast.makeText(this, R.string.enter_volume_path, Toast.LENGTH_SHORT).show()
        } else {
            changePassword(null)
        }
    }

    fun changePassword(givenHash: ByteArray?){
        val new_password = edit_new_password.text.toString().toCharArray()
        val new_password_confirm = edit_new_password_confirm.text.toString().toCharArray()
        if (!new_password.contentEquals(new_password_confirm)) {
            Toast.makeText(applicationContext, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
        } else {
            val old_password = edit_old_password.text.toString().toCharArray()
            var returnedHash: ByteArray? = null
            if (usf_fingerprint && checkbox_save_password.isChecked){
                returnedHash = ByteArray(GocryptfsVolume.KeyLen)
            }
            var changePasswordImmediately = true
            if (givenHash == null){
                val cipherText = sharedPrefs.getString(root_cipher_dir, null)
                if (cipherText != null){ //password hash saved
                    fingerprintPasswordHashSaver.decrypt(cipherText, root_cipher_dir, ::changePassword)
                    changePasswordImmediately = false
                }
            }
            if (changePasswordImmediately){
                if (GocryptfsVolume.change_password(root_cipher_dir, old_password, givenHash, new_password, returnedHash)) {
                    val editor = sharedPrefs.edit()
                    if (sharedPrefs.getString(root_cipher_dir, null) != null){
                        editor.remove(root_cipher_dir)
                        editor.apply()
                    }
                    var continueImmediately = true
                    if (checkbox_remember_path.isChecked) {
                        val old_saved_volumes_paths = sharedPrefs.getStringSet(ConstValues.saved_volumes_key, HashSet()) as Set<String>
                        val new_saved_volumes_paths = old_saved_volumes_paths.toMutableList()
                        if (!old_saved_volumes_paths.contains(root_cipher_dir)) {
                            new_saved_volumes_paths.add(root_cipher_dir)
                            editor.putStringSet(ConstValues.saved_volumes_key, new_saved_volumes_paths.toSet())
                            editor.apply()
                        }
                        if (checkbox_save_password.isChecked && returnedHash != null){
                            fingerprintPasswordHashSaver.encryptAndSave(returnedHash, root_cipher_dir){ _ ->
                                onPasswordChanged()
                            }
                            continueImmediately = false
                        }
                    }
                    if (continueImmediately){
                        onPasswordChanged()
                    }
                } else {
                    ColoredAlertDialog(this)
                            .setTitle(R.string.error)
                            .setMessage(R.string.change_password_failed)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                }
            }
            Arrays.fill(old_password, 0.toChar())
        }
        Arrays.fill(new_password, 0.toChar())
        Arrays.fill(new_password_confirm, 0.toChar())
    }

    fun onPasswordChanged(){
        ColoredAlertDialog(this)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && fingerprintPasswordHashSaver.isListening){
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