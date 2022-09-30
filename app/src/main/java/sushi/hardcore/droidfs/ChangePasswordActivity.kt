package sushi.hardcore.droidfs

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import sushi.hardcore.droidfs.databinding.ActivityChangePasswordBinding
import sushi.hardcore.droidfs.filesystems.CryfsVolume
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.GocryptfsVolume
import sushi.hardcore.droidfs.util.IntentUtils
import sushi.hardcore.droidfs.util.ObjRef
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.util.*

class ChangePasswordActivity: BaseActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private lateinit var volume: VolumeData
    private lateinit var volumeDatabase: VolumeDatabase
    private var fingerprintProtector: FingerprintProtector? = null
    private var usfFingerprint: Boolean = false
    private val inputMethodManager by lazy {
        getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volume = IntentUtils.getParcelableExtra(intent, "volume")!!
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
                binding.fingerprintSwitchContainer.visibility = View.VISIBLE
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
        binding.fingerprintSwitchContainer.setOnClickListener {
            binding.switchUseFingerprint.toggle()
        }
        binding.switchUseFingerprint.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && binding.editCurrentPassword.hasFocus()) {
                binding.editCurrentPassword.clearFocus()
                inputMethodManager.hideSoftInputFromWindow(binding.editCurrentPassword.windowToken, 0)
            }
        }
        binding.editCurrentPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.switchUseFingerprint.isChecked = false
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
        val newPassword = WidgetUtil.encodeEditTextContent(binding.editNewPassword)
        val newPasswordConfirm = WidgetUtil.encodeEditTextContent(binding.editPasswordConfirm)
        @SuppressLint("NewApi")
        if (!newPassword.contentEquals(newPasswordConfirm)) {
            Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
            Arrays.fill(newPassword, 0)
        } else {
            var changeWithCurrentPassword = true
            volume.encryptedHash?.let { encryptedHash ->
                volume.iv?.let { iv ->
                    fingerprintProtector?.let {
                        if (binding.switchUseFingerprint.isChecked) {
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
                                    Arrays.fill(newPassword, 0)
                                }
                            }
                            it.loadPasswordHash(volume.name, encryptedHash, iv)
                        }
                    }
                }
            }
            if (changeWithCurrentPassword) {
                changeVolumePassword(newPassword)
            }
        }
        Arrays.fill(newPasswordConfirm, 0)
    }

    private fun changeVolumePassword(newPassword: ByteArray, givenHash: ByteArray? = null) {
        val returnedHash: ObjRef<ByteArray?>? = if (binding.checkboxSavePassword.isChecked) {
            ObjRef(null)
        } else {
            null
        }
        val currentPassword = if (givenHash == null) {
            WidgetUtil.encodeEditTextContent(binding.editCurrentPassword)
        } else {
            null
        }
        object : LoadingTask<Boolean>(this, themeValue, R.string.loading_msg_change_password) {
            override suspend fun doTask(): Boolean {
                val success = if (volume.type == EncryptedVolume.GOCRYPTFS_VOLUME_TYPE) {
                    GocryptfsVolume.changePassword(
                        volume.getFullPath(filesDir.path),
                        currentPassword,
                        givenHash,
                        newPassword,
                        returnedHash?.apply { value = ByteArray(GocryptfsVolume.KeyLen) }?.value
                    )
                } else {
                    CryfsVolume.changePassword(
                        volume.getFullPath(filesDir.path),
                        filesDir.path,
                        currentPassword,
                        givenHash,
                        newPassword,
                        returnedHash
                    )
                }
                if (success) {
                    if (volumeDatabase.isHashSaved(volume.name)) {
                        volumeDatabase.removeHash(volume)
                    }
                }
                if (currentPassword != null)
                    Arrays.fill(currentPassword, 0)
                Arrays.fill(newPassword, 0)
                if (givenHash != null)
                    Arrays.fill(givenHash, 0)
                return success
            }
        }.startTask(lifecycleScope) { success ->
            if (success) {
                @SuppressLint("NewApi") // if fingerprintProtector is null checkboxSavePassword is hidden
                if (binding.checkboxSavePassword.isChecked && returnedHash != null) {
                    fingerprintProtector!!.let {
                        it.listener = object : FingerprintProtector.Listener {
                            override fun onHashStorageReset() {
                                // retry
                                it.savePasswordHash(volume, returnedHash.value!!)
                            }
                            override fun onPasswordHashDecrypted(hash: ByteArray) {}
                            override fun onPasswordHashSaved() {
                                Arrays.fill(returnedHash.value!!, 0)
                                finish()
                            }
                            override fun onFailed(pending: Boolean) {
                                if (!pending) {
                                    Arrays.fill(returnedHash.value!!, 0)
                                    finish()
                                }
                            }
                        }
                        it.savePasswordHash(volume, returnedHash.value!!)
                    }
                } else {
                    finish()
                }
            } else {
                CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.error)
                    .setMessage(R.string.change_password_failed)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        }
    }
}