package sushi.hardcore.droidfs

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_change_password.*
import kotlinx.android.synthetic.main.checkboxes_section.*
import kotlinx.android.synthetic.main.volume_path_section.*
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class ChangePasswordActivity : VolumeActionActivity() {
    private lateinit var savedVolumesAdapter: SavedVolumesAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)
        setupActionBar()
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
                onClickSwitchHiddenVolume(switch_hidden_volume)
            }
        } else {
            WidgetUtil.hideWithPadding(saved_path_listview)
        }
        val textWatcher = object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (volumeDatabase.isVolumeSaved(s.toString())){
                    checkbox_remember_path.isEnabled = false
                    checkbox_remember_path.isChecked = false
                    if (volumeDatabase.isHashSaved(s.toString())){
                        edit_old_password.text = null
                        edit_old_password.hint = getString(R.string.hash_saved_hint)
                        edit_old_password.isEnabled = false
                    } else {
                        edit_old_password.hint = null
                        edit_old_password.isEnabled = true
                    }
                } else {
                    checkbox_remember_path.isEnabled = true
                    edit_old_password.hint = null
                    edit_old_password.isEnabled = true
                }
            }
        }
        edit_volume_path.addTextChangedListener(textWatcher)
        edit_volume_name.addTextChangedListener(textWatcher)
        edit_new_password_confirm.setOnEditorActionListener { v, _, _ ->
            onClickChangePassword(v)
            true
        }
    }

    fun pickDirectory(view: View?) {
        safePickDirectory()
    }

    override fun onDirectoryPicked(uri: Uri) {
        if (PathUtils.isTreeUriOnPrimaryStorage(uri)){
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
        } else {
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.change_pwd_on_sdcard_error_msg)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    fun onClickChangePassword(view: View?) {
        loadVolumePath {
            val volumeFile = File(currentVolumePath)
            if (!GocryptfsVolume.isGocryptfsVolume(volumeFile)){
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.error_not_a_volume)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else if (!volumeFile.canWrite()){
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.change_pwd_cant_write_error_msg)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else {
                changePassword()
            }
        }
    }

    private fun changePassword(givenHash: ByteArray? = null){
        val newPassword = edit_new_password.text.toString().toCharArray()
        val newPasswordConfirm = edit_new_password_confirm.text.toString().toCharArray()
        if (!newPassword.contentEquals(newPasswordConfirm)) {
            Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
        } else {
            object : LoadingTask(this, R.string.loading_msg_change_password) {
                override fun doTask(activity: AppCompatActivity) {
                    val oldPassword = edit_old_password.text.toString().toCharArray()
                    var returnedHash: ByteArray? = null
                    if (checkbox_save_password.isChecked) {
                        returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                    }
                    var changePasswordImmediately = true
                    if (givenHash == null) {
                        var volume: Volume? = null
                        volumeDatabase.getVolumes().forEach { testVolume ->
                            if (testVolume.name == currentVolumeName){
                                volume = testVolume
                            }
                        }
                        volume?.let {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                it.hash?.let { hash ->
                                    it.iv?.let { iv ->
                                        currentVolumePath = if (it.isHidden){
                                            PathUtils.pathJoin(filesDir.path, it.name)
                                        } else {
                                            it.name
                                        }
                                        stopTask {
                                            loadPasswordHash(hash, iv, ::changePassword)
                                        }
                                        changePasswordImmediately = false
                                    }
                                }
                            }
                        }
                    }
                    if (changePasswordImmediately) {
                        if (GocryptfsVolume.changePassword(currentVolumePath, oldPassword, givenHash, newPassword, returnedHash)) {
                            val volume = Volume(currentVolumeName, switch_hidden_volume.isChecked)
                            if (volumeDatabase.isHashSaved(currentVolumeName)) {
                                volumeDatabase.removeHash(volume)
                            }
                            if (checkbox_remember_path.isChecked) {
                                volumeDatabase.saveVolume(volume)
                            }
                            if (checkbox_save_password.isChecked && returnedHash != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                stopTask {
                                    savePasswordHash(returnedHash) {
                                        onPasswordChanged()
                                    }
                                }
                            } else {
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

    fun onClickRememberPath(view: View) {
        if (!checkbox_remember_path.isChecked){
            checkbox_save_password.isChecked = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Wiper.wipeEditText(edit_old_password)
        Wiper.wipeEditText(edit_new_password)
        Wiper.wipeEditText(edit_new_password_confirm)
    }
}