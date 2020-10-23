package sushi.hardcore.droidfs

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.checkboxes_section.*
import kotlinx.android.synthetic.main.toolbar.*
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

open class VolumeActionActivity : BaseActivity() {
    protected lateinit var rootCipherDir: String
    private var usf_fingerprint = false
    private var biometricCanAuthenticateCode: Int = -1
    private lateinit var biometricManager: BiometricManager
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var keyStore: KeyStore
    private lateinit var key: SecretKey
    private lateinit var cipher: Cipher
    private var actionMode: Int? = null
    private lateinit var onAuthenticationResult: (success: Boolean) -> Unit
    private lateinit var onPasswordDecrypted: (password: ByteArray) -> Unit
    private lateinit var dataToProcess: ByteArray
    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "Hash Key"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LEN = 128
    }

    protected fun setupFingerprintStuff(){
        usf_fingerprint = sharedPrefs.getBoolean("usf_fingerprint", false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && usf_fingerprint) {
            biometricManager = BiometricManager.from(this)
            biometricCanAuthenticateCode = canAuthenticate()
            if (biometricCanAuthenticateCode == 0){
                val executor = ContextCompat.getMainExecutor(this)
                val activityContext = this
                val callback = object: BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        Toast.makeText(applicationContext, errString, Toast.LENGTH_SHORT).show()
                        if (actionMode == Cipher.ENCRYPT_MODE){
                            onAuthenticationResult(false)
                        }
                    }
                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(applicationContext, R.string.authentication_failed, Toast.LENGTH_SHORT).show()
                        if (actionMode == Cipher.ENCRYPT_MODE){
                            onAuthenticationResult(false)
                        }
                    }
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        var success = false
                        val cipherObject = result.cryptoObject?.cipher
                        if (cipherObject != null){
                            try {
                                when (actionMode) {
                                    Cipher.ENCRYPT_MODE -> {
                                        val cipherText = cipherObject.doFinal(dataToProcess)
                                        val encodedCipherText = Base64.encodeToString(cipherText, 0)
                                        val encodedIv = Base64.encodeToString(cipherObject.iv, 0)
                                        val sharedPrefsEditor = sharedPrefs.edit()
                                        sharedPrefsEditor.putString(rootCipherDir, "$encodedIv:$encodedCipherText")
                                        sharedPrefsEditor.apply()
                                        success = true
                                    }
                                    Cipher.DECRYPT_MODE -> {
                                        try {
                                            val plainText = cipherObject.doFinal(dataToProcess)
                                            onPasswordDecrypted(plainText)
                                        } catch (e: AEADBadTagException){
                                            ColoredAlertDialogBuilder(activityContext)
                                                .setTitle(R.string.error)
                                                .setMessage(R.string.MAC_verification_failed)
                                                .setPositiveButton(R.string.reset_hash_storage) { _, _ ->
                                                    resetHashStorage()
                                                }
                                                .setNegativeButton(R.string.cancel, null)
                                                .show()
                                        }
                                    }
                                }
                            } catch (e: IllegalBlockSizeException){
                                ColoredAlertDialogBuilder(activityContext)
                                    .setTitle(R.string.illegal_block_size_exception)
                                    .setMessage(R.string.illegal_block_size_exception_msg)
                                    .setPositiveButton(R.string.reset_hash_storage) { _, _ ->
                                        resetHashStorage()
                                    }
                                    .setNegativeButton(R.string.cancel, null)
                                    .show()
                            }
                        } else {
                            Toast.makeText(applicationContext, R.string.error_cipher_null, Toast.LENGTH_SHORT).show()
                        }
                        if (actionMode == Cipher.ENCRYPT_MODE){
                            onAuthenticationResult(success)
                        }
                    }
                }
                biometricPrompt = BiometricPrompt(this, executor, callback)
            }
        } else {
            WidgetUtil.hide(checkbox_save_password)
        }
    }

    protected fun setupActionBar(){
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun canAuthenticate(): Int {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return if (!keyguardManager.isKeyguardSecure) {
            1
        } else {
            when (biometricManager.canAuthenticate()){
                BiometricManager.BIOMETRIC_SUCCESS -> 0
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> 2
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> 3
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> 4
                else -> -1
            }
        }
    }

    private fun printAuthenticateImpossibleError() {
        Toast.makeText(this, when (biometricCanAuthenticateCode){
            1 -> R.string.fingerprint_error_no_fingerprints
            2 -> R.string.fingerprint_error_hw_not_present
            3 -> R.string.fingerprint_error_hw_not_available
            4 -> R.string.fingerprint_error_no_fingerprints
            else -> R.string.error
        }, Toast.LENGTH_SHORT).show()
    }

    fun onClickSavePasswordHash(view: View) {
        if (checkbox_save_password.isChecked){
            if (biometricCanAuthenticateCode == 0){
                checkbox_remember_path.isChecked = checkbox_remember_path.isEnabled
            } else {
                checkbox_save_password.isChecked = false
                printAuthenticateImpossibleError()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun prepareCipher() {
        keyStore = KeyStore.getInstance(ANDROID_KEY_STORE)
        keyStore.load(null)
        key = if (keyStore.containsAlias(KEY_ALIAS)){
            keyStore.getKey(KEY_ALIAS, null) as SecretKey
        } else {
            val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
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
        cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES+"/"+KeyProperties.BLOCK_MODE_GCM+"/"+KeyProperties.ENCRYPTION_PADDING_NONE)
    }

    private fun alertKeyPermanentlyInvalidatedException(){
        ColoredAlertDialogBuilder(this)
            .setTitle(R.string.key_permanently_invalidated_exception)
            .setMessage(R.string.key_permanently_invalidated_exception_msg)
            .setPositiveButton(R.string.reset_hash_storage) { _, _ ->
                resetHashStorage()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    protected fun savePasswordHash(plainText: ByteArray, onAuthenticationResult: (success: Boolean) -> Unit){
        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(rootCipherDir)
            .setSubtitle(getString(R.string.encrypt_action_description))
            .setDescription(getString(R.string.fingerprint_instruction))
            .setNegativeButtonText(getString(R.string.cancel))
            .setDeviceCredentialAllowed(false)
            .setConfirmationRequired(false)
            .build()
        if (!::cipher.isInitialized){
            prepareCipher()
        }
        actionMode = Cipher.ENCRYPT_MODE
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
            this.onAuthenticationResult = onAuthenticationResult
            dataToProcess = plainText
            biometricPrompt.authenticate(biometricPromptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: KeyPermanentlyInvalidatedException){
            alertKeyPermanentlyInvalidatedException()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    protected fun loadPasswordHash(cipherText: String, onPasswordDecrypted: (password: ByteArray) -> Unit){
        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(rootCipherDir)
            .setSubtitle(getString(R.string.decrypt_action_description))
            .setDescription(getString(R.string.fingerprint_instruction))
            .setNegativeButtonText(getString(R.string.cancel))
            .setDeviceCredentialAllowed(false)
            .setConfirmationRequired(false)
            .build()
        this.onPasswordDecrypted = onPasswordDecrypted
        actionMode = Cipher.DECRYPT_MODE
        if (!::cipher.isInitialized){
            prepareCipher()
        }
        val encodedElements = cipherText.split(":")
        dataToProcess = Base64.decode(encodedElements[1], 0)
        val iv = Base64.decode(encodedElements[0], 0)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LEN, iv)
        try {
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
            biometricPrompt.authenticate(biometricPromptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: KeyPermanentlyInvalidatedException){
            alertKeyPermanentlyInvalidatedException()
        }
    }

    private fun resetHashStorage() {
        keyStore.deleteEntry(KEY_ALIAS)
        val savedVolumePaths = sharedPrefs.getStringSet(ConstValues.saved_volumes_key, HashSet<String>()) as Set<String>
        val sharedPrefsEditor = sharedPrefs.edit()
        for (path in savedVolumePaths){
            val savedHash = sharedPrefs.getString(path, null)
            if (savedHash != null){
                sharedPrefsEditor.remove(path)
            }
        }
        sharedPrefsEditor.apply()
        Toast.makeText(this, R.string.hash_storage_reset, Toast.LENGTH_SHORT).show()
    }
}