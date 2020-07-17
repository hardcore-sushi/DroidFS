package sushi.hardcore.droidfs.fingerprint_stuff

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

@RequiresApi(Build.VERSION_CODES.M)
class FingerprintPasswordHashSaver(private val activityContext: AppCompatActivity, private val shared_prefs: SharedPreferences) {
    private var isPrepared = false
    var isListening = false
    var authenticationFailed = false
    private val shared_prefs_editor: SharedPreferences.Editor = shared_prefs.edit()
    private val fingerprintManager = activityContext.getSystemService(Context.FINGERPRINT_SERVICE) as FingerprintManager
    private lateinit var root_cipher_dir: String
    private lateinit var action_description: String
    private lateinit var onAuthenticationResult: (success: Boolean) -> Unit
    private lateinit var onPasswordDecrypted: (password: ByteArray) -> Unit
    private lateinit var keyStore: KeyStore
    private lateinit var key: SecretKey
    lateinit var fingerprintFragment: FingerprintFragment
    private val handler = Handler()
    private lateinit var cancellationSignal: CancellationSignal
    private var actionMode: Int? = null
    private lateinit var dataToProcess: ByteArray
    private lateinit var cipher: Cipher
    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "Hash Key"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LEN = 128
        private const val CIPHER_TYPE = "AES/GCM/NoPadding"
        private const val SUCCESS_DISMISS_DIALOG_DELAY: Long = 400
        private const val FAILED_DISMISS_DIALOG_DELAY: Long = 800
    }
    private fun reset_hash_storage() {
        keyStore.deleteEntry(KEY_ALIAS)
        val saved_volume_paths = shared_prefs.getStringSet(ConstValues.saved_volumes_key, HashSet<String>()) as Set<String>
        for (path in saved_volume_paths){
            val saved_hash = shared_prefs.getString(path, null)
            if (saved_hash != null){
                shared_prefs_editor.remove(path)
            }
        }
        shared_prefs_editor.apply()
        Toast.makeText(activityContext, activityContext.getString(R.string.hash_storage_reset), Toast.LENGTH_SHORT).show()
    }

    fun canAuthenticate(): Boolean{
        if (ContextCompat.checkSelfPermission(activityContext, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED){
            Toast.makeText(activityContext, activityContext.getString(R.string.fingerprint_perm_denied), Toast.LENGTH_SHORT).show()
        } else if (!fingerprintManager.isHardwareDetected){
            Toast.makeText(activityContext, activityContext.getString(R.string.no_fingerprint_sensor), Toast.LENGTH_SHORT).show()
        } else {
            val keyguardManager = activityContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (!keyguardManager.isKeyguardSecure || !fingerprintManager.hasEnrolledFingerprints()) {
                Toast.makeText(activityContext, activityContext.getString(R.string.no_fingerprint_configured), Toast.LENGTH_SHORT).show()
            } else {
                return true
            }
        }
        return false
    }
    private fun prepare() {
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
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
        cipher = Cipher.getInstance(CIPHER_TYPE)
        fingerprintFragment = FingerprintFragment(root_cipher_dir, action_description, ::stopListening)
        isPrepared = true
    }
    fun encryptAndSave(plainText: ByteArray, root_cipher_dir: String, onAuthenticationResult: (success: Boolean) -> Unit){
        if (shared_prefs.getString(root_cipher_dir, null) == null){
            this.root_cipher_dir = root_cipher_dir
            this.action_description = activityContext.getString(R.string.encrypt_action_description)
            this.onAuthenticationResult = onAuthenticationResult
            if (!isPrepared){
                prepare()
            }
            dataToProcess = plainText
            actionMode = Cipher.ENCRYPT_MODE
            cipher.init(Cipher.ENCRYPT_MODE, key)
            startListening()
        }
    }
    fun decrypt(cipherText: String, root_cipher_dir: String, onPasswordDecrypted: (password: ByteArray) -> Unit){
        this.root_cipher_dir = root_cipher_dir
        this.action_description = activityContext.getString(R.string.decrypt_action_description)
        this.onPasswordDecrypted = onPasswordDecrypted
        if (!isPrepared){
            prepare()
        }
        actionMode = Cipher.DECRYPT_MODE
        val encodedElements = cipherText.split(":")
        dataToProcess = Base64.decode(encodedElements[1], 0)
        val iv = Base64.decode(encodedElements[0], 0)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LEN, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        startListening()
    }

    private fun startListening(){
        cancellationSignal = CancellationSignal()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val biometricPrompt = BiometricPrompt.Builder(activityContext)
                    .setTitle(root_cipher_dir)
                    .setSubtitle(action_description)
                    .setDescription(activityContext.getString(R.string.fingerprint_instruction))
                    .setNegativeButton(activityContext.getString(R.string.cancel), activityContext.mainExecutor, DialogInterface.OnClickListener{_, _ ->
                        cancellationSignal.cancel()
                        callbackOnAuthenticationFailed() //toggle on onAuthenticationResult
                    }).build()
            biometricPrompt.authenticate(BiometricPrompt.CryptoObject(cipher), cancellationSignal, activityContext.mainExecutor, object: BiometricPrompt.AuthenticationCallback(){
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    callbackOnAuthenticationError()
                }
                override fun onAuthenticationFailed() {
                    callbackOnAuthenticationFailed()
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    callbackOnAuthenticationSucceeded()
                }
            })
        } else {
            fingerprintFragment.show(activityContext.supportFragmentManager, null)
            fingerprintManager.authenticate(FingerprintManager.CryptoObject(cipher), cancellationSignal, 0, object: FingerprintManager.AuthenticationCallback(){
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    callbackOnAuthenticationError()
                }
                override fun onAuthenticationFailed() {
                    callbackOnAuthenticationFailed()
                }
                override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                    callbackOnAuthenticationSucceeded()
                }
            }, null)
        }
        isListening = true
    }

    fun stopListening(){
        cancellationSignal.cancel()
        isListening = false
    }

    fun callbackOnAuthenticationError() {
        if (!authenticationFailed){
            if (fingerprintFragment.isAdded){
                fingerprintFragment.image_fingerprint.setColorFilter(ContextCompat.getColor(activityContext, R.color.fingerprint_failed))
                fingerprintFragment.text_instruction.setText(activityContext.getString(R.string.authentication_error))
                handler.postDelayed({ fingerprintFragment.dismiss() }, 1000)
            }
            if (actionMode == Cipher.ENCRYPT_MODE){
                handler.postDelayed({ onAuthenticationResult(false) }, FAILED_DISMISS_DIALOG_DELAY)
            }
        }
    }

    fun callbackOnAuthenticationFailed() {
        authenticationFailed = true
        if (fingerprintFragment.isAdded){
            fingerprintFragment.image_fingerprint.setColorFilter(ContextCompat.getColor(activityContext, R.color.fingerprint_failed))
            fingerprintFragment.text_instruction.text = activityContext.getString(R.string.authentication_failed)
            handler.postDelayed({ fingerprintFragment.dismiss() }, FAILED_DISMISS_DIALOG_DELAY)
            stopListening()
        } else {
            handler.postDelayed({ stopListening() }, FAILED_DISMISS_DIALOG_DELAY)
        }
        if (actionMode == Cipher.ENCRYPT_MODE){
            handler.postDelayed({ onAuthenticationResult(false) }, FAILED_DISMISS_DIALOG_DELAY)
        }
    }

    fun callbackOnAuthenticationSucceeded() {
        if (fingerprintFragment.isAdded){
            fingerprintFragment.image_fingerprint.setColorFilter(ContextCompat.getColor(activityContext, R.color.fingerprint_success))
            fingerprintFragment.text_instruction.text = activityContext.getString(R.string.authenticated)
        }
        try {
            when (actionMode) {
                Cipher.ENCRYPT_MODE -> {
                    val cipherText = cipher.doFinal(dataToProcess)
                    val encodedCipherText = Base64.encodeToString(cipherText, 0)
                    val encodedIv = Base64.encodeToString(cipher.iv, 0)
                    shared_prefs_editor.putString(root_cipher_dir, "$encodedIv:$encodedCipherText")
                    shared_prefs_editor.apply()
                    handler.postDelayed({
                        if (fingerprintFragment.isAdded){
                            fingerprintFragment.dismiss()
                        }
                        onAuthenticationResult(true)
                    }, SUCCESS_DISMISS_DIALOG_DELAY)
                }
                Cipher.DECRYPT_MODE -> {
                    try {
                        val plainText = cipher.doFinal(dataToProcess)
                        handler.postDelayed({
                            if (fingerprintFragment.isAdded){
                                fingerprintFragment.dismiss()
                            }
                            onPasswordDecrypted(plainText)
                        }, SUCCESS_DISMISS_DIALOG_DELAY)
                    } catch (e: AEADBadTagException){
                        ColoredAlertDialog(activityContext)
                                .setTitle(R.string.error)
                                .setMessage(activityContext.getString(R.string.MAC_verification_failed))
                                .setPositiveButton(activityContext.getString(R.string.reset_hash_storage)) { _, _ ->
                                    reset_hash_storage()
                                }
                                .setNegativeButton(R.string.cancel, null)
                                .show()
                    }
                }
            }
        } catch (e: IllegalBlockSizeException){
            stopListening()
            ColoredAlertDialog(activityContext)
                    .setTitle(R.string.authentication_error)
                    .setMessage(activityContext.getString(R.string.authentication_error_msg))
                    .setPositiveButton(activityContext.getString(R.string.reset_hash_storage)) { _, _ ->
                        reset_hash_storage()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
        }

    }
}
