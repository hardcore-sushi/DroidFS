package sushi.hardcore.droidfs

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.databinding.ActivityChangePasswordBinding
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.util.*

class ChangePasswordActivity: BaseActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private lateinit var volume: Volume
    private lateinit var volumeDatabase: VolumeDatabase
    private var fingerprintProtector: FingerprintProtector? = null
    private var usfFingerprint: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volume = intent.getParcelableExtra("volume")!!
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = getString(R.string.change_password)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.textVolumeName.text = volume.name
        volumeDatabase = VolumeDatabase(this)
        usfFingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fingerprintProtector = FingerprintProtector.new(this, themeValue, volumeDatabase)
            if (fingerprintProtector != null && volume.encryptedHash != null) {
                binding.textCurrentPasswordLabel.visibility = View.GONE
                binding.editCurrentPassword.visibility = View.GONE
            }
        }
        if (!usfFingerprint || fingerprintProtector == null) {
            binding.checkboxSavePassword.visibility = View.GONE
        }
        if (sharedPrefs.getBoolean(ConstValues.PIN_PASSWORDS_KEY, false)) {
            arrayOf(binding.editCurrentPassword, binding.editNewPassword, binding.editPasswordConfirm).forEach {
                it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
        }
        binding.editPasswordConfirm.setOnEditorActionListener { _, _, _ ->
            changeVolumePassword()
            true
        }
        binding.button.setOnClickListener { changeVolumePassword() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun showCurrentPasswordInput() {
        binding.textCurrentPasswordLabel.visibility = View.VISIBLE
        binding.editCurrentPassword.visibility = View.VISIBLE
    }

    private fun changeVolumePassword() {
        val newPassword = CharArray(binding.editNewPassword.text.length)
        binding.editNewPassword.text.getChars(0, newPassword.size, newPassword, 0)
        val newPasswordConfirm = CharArray(binding.editPasswordConfirm.text.length)
        binding.editPasswordConfirm.text.getChars(0, newPasswordConfirm.size, newPasswordConfirm, 0)
        @SuppressLint("NewApi")
        if (!newPassword.contentEquals(newPasswordConfirm)) {
            Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
            Arrays.fill(newPassword, 0.toChar())
        } else {
            var changeWithCurrentPassword = true
            volume.encryptedHash?.let { encryptedHash ->
                volume.iv?.let { iv ->
                    fingerprintProtector?.let {
                        changeWithCurrentPassword = false
                        it.listener = object : FingerprintProtector.Listener {
                            override fun onHashStorageReset() {
                                showCurrentPasswordInput()
                                volume.encryptedHash = null
                                volume.iv = null
                            }
                            override fun onPasswordHashDecrypted(hash: ByteArray) {
                                changeVolumePassword(newPassword, hash)
                            }
                            override fun onPasswordHashSaved() {}
                            override fun onFailed(pending: Boolean) {
                                Arrays.fill(newPassword, 0.toChar())
                            }
                        }
                        it.loadPasswordHash(volume.name, encryptedHash, iv)
                    }
                }
            }
            if (changeWithCurrentPassword) {
                changeVolumePassword(newPassword)
            }
        }
        Arrays.fill(newPasswordConfirm, 0.toChar())
    }

    private fun changeVolumePassword(newPassword: CharArray, givenHash: ByteArray? = null) {
        object : LoadingTask(this, themeValue, R.string.loading_msg_change_password) {
            override fun doTask(activity: AppCompatActivity) {
                var returnedHash: ByteArray? = null
                if (binding.checkboxSavePassword.isChecked) {
                    returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                }
                var currentPassword: CharArray? = null
                if (givenHash == null) {
                    currentPassword = CharArray(binding.editCurrentPassword.text.length)
                    binding.editCurrentPassword.text.getChars(0, currentPassword.size, currentPassword, 0)
                }
                if (GocryptfsVolume.changePassword(volume.getFullPath(filesDir.path), currentPassword, givenHash, newPassword, returnedHash)) {
                    if (volumeDatabase.isHashSaved(volume.name)) {
                        volumeDatabase.removeHash(volume)
                    }
                    stopTask {
                        @SuppressLint("NewApi") // if fingerprintProtector is null checkboxSavePassword is hidden
                        if (binding.checkboxSavePassword.isChecked && returnedHash != null) {
                            fingerprintProtector!!.let {
                                it.listener = object : FingerprintProtector.Listener {
                                    override fun onHashStorageReset() {
                                        // retry
                                        it.savePasswordHash(volume, returnedHash)
                                    }
                                    override fun onPasswordHashDecrypted(hash: ByteArray) {}
                                    override fun onPasswordHashSaved() {
                                        Arrays.fill(returnedHash, 0)
                                        finish()
                                    }
                                    override fun onFailed(pending: Boolean) {
                                        if (!pending) {
                                            Arrays.fill(returnedHash, 0)
                                            finish()
                                        }
                                    }
                                }
                                it.savePasswordHash(volume, returnedHash)
                            }
                        } else {
                            finish()
                        }
                    }
                } else {
                    stopTask {
                        CustomAlertDialogBuilder(activity, themeValue)
                            .setTitle(R.string.error)
                            .setMessage(R.string.change_password_failed)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    }
                }
                if (currentPassword != null)
                    Arrays.fill(currentPassword, 0.toChar())
                Arrays.fill(newPassword, 0.toChar())
                if (givenHash != null)
                    Arrays.fill(givenHash, 0)
            }
        }
    }
}