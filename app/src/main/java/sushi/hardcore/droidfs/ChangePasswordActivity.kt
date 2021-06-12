package sushi.hardcore.droidfs

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.adapters.SavedVolumesAdapter
import sushi.hardcore.droidfs.databinding.ActivityChangePasswordBinding
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class ChangePasswordActivity : VolumeActionActivity() {
    private lateinit var savedVolumesAdapter: SavedVolumesAdapter
    private lateinit var binding: ActivityChangePasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupLayout()
        setupFingerprintStuff()
        savedVolumesAdapter = SavedVolumesAdapter(this, volumeDatabase)
        if (savedVolumesAdapter.count > 0){
            binding.savedPathListview.adapter = savedVolumesAdapter
            binding.savedPathListview.onItemClickListener = OnItemClickListener { _, _, position, _ ->
                val volume = savedVolumesAdapter.getItem(position)
                currentVolumeName = volume.name
                if (volume.isHidden){
                    switchHiddenVolume.isChecked = true
                    editVolumeName.setText(currentVolumeName)
                } else {
                    switchHiddenVolume.isChecked = false
                    editVolumePath.setText(currentVolumeName)
                }
                onClickSwitchHiddenVolume()
            }
        } else {
            WidgetUtil.hideWithPadding(binding.savedPathListview)
        }
        val textWatcher = object: TextWatcher{
            override fun afterTextChanged(s: Editable?) {
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (volumeDatabase.isVolumeSaved(s.toString())){
                    checkboxRememberPath.isEnabled = false
                    checkboxRememberPath.isChecked = false
                    binding.editOldPassword.apply {
                        if (volumeDatabase.isHashSaved(s.toString())){
                            text = null
                            hint = getString(R.string.hash_saved_hint)
                            isEnabled = false
                        } else {
                            hint = null
                            isEnabled = true
                        }
                    }
                } else {
                    checkboxRememberPath.isEnabled = true
                    binding.editOldPassword.apply {
                        hint = null
                        isEnabled = true
                    }
                }
            }
        }
        editVolumePath.addTextChangedListener(textWatcher)
        editVolumeName.addTextChangedListener(textWatcher)
        binding.editNewPasswordConfirm.setOnEditorActionListener { _, _, _ ->
            checkVolumePathThenChangePassword()
            true
        }
        binding.buttonChangePassword.setOnClickListener {
            checkVolumePathThenChangePassword()
        }
    }

    override fun onDirectoryPicked(uri: Uri) {
        if (PathUtils.isTreeUriOnPrimaryStorage(uri)){
            val path = PathUtils.getFullPathFromTreeUri(uri, this)
            if (path != null){
                editVolumePath.setText(path)
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

    fun checkVolumePathThenChangePassword() {
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
        val newPassword = binding.editNewPassword.text.toString().toCharArray()
        val newPasswordConfirm = binding.editNewPasswordConfirm.text.toString().toCharArray()
        if (!newPassword.contentEquals(newPasswordConfirm)) {
            Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
        } else {
            object : LoadingTask(this, R.string.loading_msg_change_password) {
                override fun doTask(activity: AppCompatActivity) {
                    val oldPassword = binding.editOldPassword.text.toString().toCharArray()
                    var returnedHash: ByteArray? = null
                    if (checkboxSavePassword.isChecked) {
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
                            val volume = Volume(currentVolumeName, switchHiddenVolume.isChecked)
                            if (volumeDatabase.isHashSaved(currentVolumeName)) {
                                volumeDatabase.removeHash(volume)
                            }
                            if (checkboxRememberPath.isChecked) {
                                volumeDatabase.saveVolume(volume)
                            }
                            if (checkboxSavePassword.isChecked && returnedHash != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
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

    override fun onDestroy() {
        super.onDestroy()
        Wiper.wipeEditText(binding.editOldPassword)
        Wiper.wipeEditText(binding.editNewPassword)
        Wiper.wipeEditText(binding.editNewPasswordConfirm)
    }
}