package sushi.hardcore.droidfs

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.UnrecoverableKeyException
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
class FingerprintProtector private constructor(
    private val activity: FragmentActivity,
    private val themeValue: String,
    private val volumeDatabase: VolumeDatabase,
) {

    interface Listener {
        fun onHashStorageReset()
        fun onPasswordHashDecrypted(hash: ByteArray)
        fun onPasswordHashSaved()
        fun onFailed(pending: Boolean)
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "Hash Key"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LEN = 128

        fun canAuthenticate(context: Context): Int {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            return if (!keyguardManager.isKeyguardSecure)
                1
            else when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
                BiometricManager.BIOMETRIC_SUCCESS -> 0
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> 2
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> 3
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> 4
                else -> -1
            }
        }

        fun new(
            activity: FragmentActivity,
            themeValue: String,
            volumeDatabase: VolumeDatabase,
        ): FingerprintProtector? {
            return if (canAuthenticate(activity) == 0)
                FingerprintProtector(activity, themeValue, volumeDatabase)
            else
                null
        }
    }

    lateinit var listener: Listener
    private val biometricPrompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object: BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (
                errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                errorCode != BiometricPrompt.ERROR_TIMEOUT
            ) {
                Toast.makeText(activity, activity.getString(R.string.biometric_error, errString), Toast.LENGTH_SHORT).show()
            }
            listener.onFailed(false)
        }
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            val cipherObject = result.cryptoObject?.cipher
            if (cipherObject != null) {
                try {
                    when (cipherActionMode) {
                        Cipher.ENCRYPT_MODE -> {
                            val cipherText = cipherObject.doFinal(dataToProcess)
                            volume.encryptedHash = cipherText
                            volume.iv = cipherObject.iv
                            if (volumeDatabase.addHash(volume))
                                listener.onPasswordHashSaved()
                            else
                                listener.onFailed(false)
                        }
                        Cipher.DECRYPT_MODE -> {
                            try {
                                val plainText = cipherObject.doFinal(dataToProcess)
                                listener.onPasswordHashDecrypted(plainText)
                            } catch (e: AEADBadTagException) {
                                listener.onFailed(true)
                                CustomAlertDialogBuilder(activity, themeValue)
                                    .setTitle(R.string.error)
                                    .setMessage(R.string.MAC_verification_failed)
                                    .setPositiveButton(R.string.reset_hash_storage) { _, _ ->
                                        resetHashStorage()
                                    }
                                    .setNegativeButton(R.string.cancel) { _, _ -> listener.onFailed(false) }
                                    .setOnCancelListener { listener.onFailed(false) }
                                    .show()
                            }
                        }
                    }
                } catch (e: IllegalBlockSizeException) {
                    listener.onFailed(true)
                    CustomAlertDialogBuilder(activity, themeValue)
                        .setTitle(R.string.illegal_block_size_exception)
                        .setMessage(R.string.illegal_block_size_exception_msg)
                        .setPositiveButton(R.string.reset_hash_storage) { _, _ ->
                            resetHashStorage()
                        }
                        .setNegativeButton(R.string.cancel) { _, _ -> listener.onFailed(false) }
                        .setOnCancelListener { listener.onFailed(false) }
                        .show()
                }
            } else {
                Toast.makeText(activity, R.string.error_cipher_null, Toast.LENGTH_SHORT).show()
                listener.onFailed(false)
            }
        }
    })
    private lateinit var keyStore: KeyStore
    private lateinit var key: SecretKey
    private lateinit var cipher: Cipher
    private var isCipherReady = false
    private var cipherActionMode: Int? = null
    private lateinit var volume: Volume
    private lateinit var dataToProcess: ByteArray

    private fun resetHashStorage() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: KeyStoreException) {
            e.printStackTrace()
        }
        volumeDatabase.getVolumes().forEach { volume ->
            volumeDatabase.removeHash(volume)
        }
        isCipherReady = false
        Toast.makeText(activity, R.string.hash_storage_reset, Toast.LENGTH_SHORT).show()
        listener.onHashStorageReset()
    }

    private fun prepareCipher(): Boolean {
        if (!isCipherReady) {
            keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
            keyStore.load(null)
            key = if (keyStore.containsAlias(KEY_ALIAS)) {
                try {
                    keyStore.getKey(KEY_ALIAS, null) as SecretKey
                } catch (e: UnrecoverableKeyException) {
                    listener.onFailed(true)
                    CustomAlertDialogBuilder(activity, themeValue)
                        .setTitle(activity.getString(R.string.unrecoverable_key_exception))
                        .setMessage(activity.getString(R.string.unrecoverable_key_exception_msg, e.localizedMessage))
                        .setPositiveButton(R.string.reset_hash_storage) { _, _ ->
                            resetHashStorage()
                        }
                        .setNegativeButton(R.string.cancel) { _, _ -> listener.onFailed(false) }
                        .setOnCancelListener { listener.onFailed(false) }
                        .show()
                    return false
                }
            } else {
                val builder = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                builder.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                builder.setKeySize(KEY_SIZE)
                builder.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                builder.setUserAuthenticationRequired(true)
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEY_STORE
                )
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            }
            cipher = Cipher.getInstance(
                KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE
            )
            isCipherReady = true
        }
        return true
    }

    private fun alertKeyPermanentlyInvalidatedException() {
        listener.onFailed(true)
        CustomAlertDialogBuilder(activity, themeValue)
            .setTitle(R.string.key_permanently_invalidated_exception)
            .setMessage(R.string.key_permanently_invalidated_exception_msg)
            .setPositiveButton(R.string.reset_hash_storage) { _, _ ->
                resetHashStorage()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> listener.onFailed(false) }
            .setOnCancelListener { listener.onFailed(false) }
            .show()
    }

    fun savePasswordHash(volume: Volume, plainText: ByteArray) {
        this.volume = volume
        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.encrypt_action_description))
            .setSubtitle(volume.shortName)
            .setDescription(activity.getString(R.string.fingerprint_instruction))
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        cipherActionMode = Cipher.ENCRYPT_MODE
        if (prepareCipher()) {
            try {
                cipher.init(Cipher.ENCRYPT_MODE, key)
                dataToProcess = plainText
                biometricPrompt.authenticate(
                    biometricPromptInfo,
                    BiometricPrompt.CryptoObject(cipher)
                )
            } catch (e: KeyPermanentlyInvalidatedException) {
                alertKeyPermanentlyInvalidatedException()
            }
        }
    }

    fun loadPasswordHash(volumeName: String, cipherText: ByteArray, iv: ByteArray) {
        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.decrypt_action_description))
            .setSubtitle(volumeName)
            .setDescription(activity.getString(R.string.fingerprint_instruction))
            .setNegativeButtonText(activity.getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        cipherActionMode = Cipher.DECRYPT_MODE
        if (prepareCipher()) {
            dataToProcess = cipherText
            val gcmSpec = GCMParameterSpec(GCM_TAG_LEN, iv)
            try {
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
                biometricPrompt.authenticate(
                    biometricPromptInfo,
                    BiometricPrompt.CryptoObject(cipher)
                )
            } catch (e: KeyPermanentlyInvalidatedException) {
                alertKeyPermanentlyInvalidatedException()
            }
        }
    }
}