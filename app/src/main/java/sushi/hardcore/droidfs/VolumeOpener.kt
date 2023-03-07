package sushi.hardcore.droidfs

import android.annotation.SuppressLint
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.Constants.DEFAULT_VOLUME_KEY
import sushi.hardcore.droidfs.databinding.DialogOpenVolumeBinding
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.ObjRef
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.util.*

class VolumeOpener(
    private val activity: FragmentActivity,
) {
    interface VolumeOpenerCallbacks {
        fun onHashStorageReset() {}
        fun onVolumeOpened(id: Int)
    }

    private val volumeDatabase = VolumeDatabase(activity)
    private var fingerprintProtector: FingerprintProtector? = null
    private val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
    var themeValue = sharedPrefs.getString(Constants.THEME_VALUE_KEY, Constants.DEFAULT_THEME_VALUE)!!
    var defaultVolumeName: String? = sharedPrefs.getString(DEFAULT_VOLUME_KEY, null)
    private var dialogBinding: DialogOpenVolumeBinding? = null
    private val volumeManager = (activity.application as VolumeManagerApp).volumeManager

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fingerprintProtector = FingerprintProtector.new(activity, themeValue, volumeDatabase)
        }
    }

    @SuppressLint("NewApi") // fingerprintProtector is non-null only when SDK_INT >= 23
    fun openVolume(volume: VolumeData, isVolumeSaved: Boolean, callbacks: VolumeOpenerCallbacks) {
        val volumeId = volumeManager.getVolumeId(volume)
        if (volumeId == null) {
            if (volume.type == EncryptedVolume.GOCRYPTFS_VOLUME_TYPE && BuildConfig.GOCRYPTFS_DISABLED) {
                Toast.makeText(activity, R.string.gocryptfs_disabled, Toast.LENGTH_SHORT).show()
                return
            } else if (volume.type == EncryptedVolume.CRYFS_VOLUME_TYPE && BuildConfig.CRYFS_DISABLED) {
                Toast.makeText(activity, R.string.cryfs_disabled, Toast.LENGTH_SHORT).show()
                return
            }
            var askForPassword = true
            fingerprintProtector?.let { fingerprintProtector ->
                volume.encryptedHash?.let { encryptedHash ->
                    volume.iv?.let { iv ->
                        askForPassword = false
                        fingerprintProtector.listener = object : FingerprintProtector.Listener {
                            override fun onHashStorageReset() {
                                callbacks.onHashStorageReset()
                            }
                            override fun onPasswordHashDecrypted(hash: ByteArray) {
                                object : LoadingTask<EncryptedVolume?>(activity, themeValue, R.string.loading_msg_open) {
                                    override suspend fun doTask(): EncryptedVolume? {
                                        val encryptedVolume = EncryptedVolume.init(volume, activity.filesDir.path, null, hash, null)
                                        Arrays.fill(hash, 0)
                                        return encryptedVolume
                                    }
                                }.startTask(activity.lifecycleScope) { encryptedVolume ->
                                    if (encryptedVolume == null) {
                                        CustomAlertDialogBuilder(activity, themeValue)
                                            .setTitle(R.string.open_volume_failed)
                                            .setMessage(R.string.open_failed_hash_msg)
                                            .setPositiveButton(R.string.ok, null)
                                            .show()
                                    } else {
                                        callbacks.onVolumeOpened(volumeManager.insert(encryptedVolume, volume))
                                    }
                                }
                            }
                            override fun onPasswordHashSaved() {}
                            override fun onFailed(pending: Boolean) {
                                if (!pending) {
                                    askForPassword(volume, isVolumeSaved, callbacks)
                                }
                            }
                        }
                        fingerprintProtector.loadPasswordHash(volume.shortName, encryptedHash, iv)
                    }
                }
            }
            if (askForPassword) {
                askForPassword(volume, isVolumeSaved, callbacks)
            }
        } else {
            callbacks.onVolumeOpened(volumeId)
        }
    }

    fun wipeSensitive() {
        dialogBinding?.editPassword?.text?.clear()
    }

    private fun onPasswordSubmitted(volume: VolumeData, isVolumeSaved: Boolean, callbacks: VolumeOpenerCallbacks) {
        if (dialogBinding!!.checkboxDefaultOpen.isChecked xor (defaultVolumeName == volume.name)) {
            with (sharedPrefs.edit()) {
                defaultVolumeName = if (dialogBinding!!.checkboxDefaultOpen.isChecked) {
                    putString(DEFAULT_VOLUME_KEY, volume.name)
                    volume.name
                } else {
                    remove(DEFAULT_VOLUME_KEY)
                    null
                }
                apply()
            }
        }
        val password = WidgetUtil.encodeEditTextContent(dialogBinding!!.editPassword)
        val savePasswordHash = dialogBinding!!.checkboxSavePassword.isChecked
        dialogBinding = null
        // openVolumeWithPassword is responsible for wiping the password
        openVolumeWithPassword(
            volume,
            password,
            isVolumeSaved,
            savePasswordHash,
            callbacks,
        )
    }

    private fun askForPassword(volume: VolumeData, isVolumeSaved: Boolean, callbacks: VolumeOpenerCallbacks, savePasswordHash: Boolean = false) {
        dialogBinding = DialogOpenVolumeBinding.inflate(activity.layoutInflater)
        if (isVolumeSaved) {
            if (!sharedPrefs.getBoolean("usf_fingerprint", false) || fingerprintProtector == null || volume.encryptedHash != null) {
                dialogBinding!!.checkboxSavePassword.visibility = View.GONE
            } else {
                dialogBinding!!.checkboxSavePassword.isChecked = savePasswordHash
            }
            dialogBinding!!.checkboxDefaultOpen.isChecked = defaultVolumeName == volume.name
        } else {
            dialogBinding!!.checkboxSavePassword.visibility = View.GONE
            dialogBinding!!.checkboxDefaultOpen.visibility = View.GONE
        }
        val dialog = CustomAlertDialogBuilder(activity, themeValue)
            .setTitle(activity.getString(R.string.open_dialog_title, volume.shortName))
            .setView(dialogBinding!!.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open) { _, _ ->
                onPasswordSubmitted(volume, isVolumeSaved, callbacks)
            }
            .create()
        dialogBinding!!.editPassword.apply {
            setOnEditorActionListener { _, _, _ ->
                dialog.dismiss()
                onPasswordSubmitted(volume, isVolumeSaved, callbacks)
                true
            }
            if (sharedPrefs.getBoolean(Constants.PIN_PASSWORDS_KEY, false)) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    private fun openVolumeWithPassword(volume: VolumeData, password: ByteArray, isVolumeSaved: Boolean, savePasswordHash: Boolean, callbacks: VolumeOpenerCallbacks) {
        val returnedHash: ObjRef<ByteArray?>? = if (savePasswordHash) {
            ObjRef(null)
        } else {
            null
        }
        object : LoadingTask<EncryptedVolume?>(activity, themeValue, R.string.loading_msg_open) {
            override suspend fun doTask(): EncryptedVolume? {
                val encryptedVolume = EncryptedVolume.init(volume, activity.filesDir.path, password, null, returnedHash)
                Arrays.fill(password, 0)
                return encryptedVolume
            }
        }.startTask(activity.lifecycleScope) { encryptedVolume ->
            if (encryptedVolume == null) {
                CustomAlertDialogBuilder(activity, themeValue)
                    .setTitle(R.string.open_volume_failed)
                    .setMessage(R.string.open_volume_failed_msg)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        askForPassword(volume, isVolumeSaved, callbacks, savePasswordHash)
                    }
                    .show()
            } else {
                val fingerprintProtector = fingerprintProtector
                @SuppressLint("NewApi") // fingerprintProtector is non-null only when SDK_INT >= 23
                if (savePasswordHash && returnedHash != null && fingerprintProtector != null) {
                    fingerprintProtector.listener = object : FingerprintProtector.Listener {
                        override fun onHashStorageReset() {
                            callbacks.onHashStorageReset()
                        }
                        override fun onPasswordHashDecrypted(hash: ByteArray) {}
                        override fun onPasswordHashSaved() {
                            Arrays.fill(returnedHash.value!!, 0)
                            callbacks.onVolumeOpened(volumeManager.insert(encryptedVolume, volume))
                        }
                        private var isClosed = false
                        override fun onFailed(pending: Boolean) {
                            if (!isClosed) {
                                encryptedVolume.close()
                                isClosed = true
                            }
                            Arrays.fill(returnedHash.value!!, 0)
                        }
                    }
                    fingerprintProtector.savePasswordHash(volume, returnedHash.value!!)
                } else {
                    callbacks.onVolumeOpened(volumeManager.insert(encryptedVolume, volume))
                }
            }
        }
    }
}