package sushi.hardcore.droidfs

import android.Manifest
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SwitchCompat
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.ColoredImageButton
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

abstract class VolumeActionActivity : BaseActivity() {

    companion object {
        private const val STORAGE_PERMISSIONS_REQUEST = 0
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "Hash Key"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LEN = 128
    }

    protected lateinit var currentVolumeName: String
    protected lateinit var currentVolumePath: String
    protected lateinit var volumeDatabase: VolumeDatabase
    protected val pickDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            onDirectoryPicked(uri)
        }
    }
    private var usf_fingerprint = false
    private var biometricCanAuthenticateCode: Int = -1
    private lateinit var biometricManager: BiometricManager
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var keyStore: KeyStore
    private lateinit var key: SecretKey
    private lateinit var cipher: Cipher
    private var isCipherReady = false
    private var actionMode: Int? = null
    private lateinit var onAuthenticationResult: (success: Boolean) -> Unit
    private lateinit var onPasswordDecrypted: (password: ByteArray) -> Unit
    private lateinit var dataToProcess: ByteArray
    private lateinit var originalHiddenVolumeSectionLayoutParams: LinearLayout.LayoutParams
    private lateinit var originalNormalVolumeSectionLayoutParams: LinearLayout.LayoutParams
    protected lateinit var switchHiddenVolume: SwitchCompat
    protected lateinit var checkboxRememberPath: CheckBox
    protected lateinit var checkboxSavePassword: CheckBox
    protected lateinit var editVolumeName: EditText
    protected lateinit var editVolumePath: EditText
    private lateinit var hiddenVolumeSection: LinearLayout
    private lateinit var normalVolumeSection: LinearLayout

    protected fun setupLayout() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        findViewById<ColoredImageButton>(R.id.button_pick_directory).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) +
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSIONS_REQUEST)
                } else {
                    safePickDirectory()
                }
            } else {
                safePickDirectory()
            }
        }
        switchHiddenVolume = findViewById(R.id.switch_hidden_volume)
        checkboxRememberPath = findViewById(R.id.checkbox_remember_path)
        checkboxSavePassword = findViewById(R.id.checkbox_save_password)
        editVolumeName = findViewById(R.id.edit_volume_name)
        editVolumePath = findViewById(R.id.edit_volume_path)
        hiddenVolumeSection = findViewById(R.id.hidden_volume_section)
        normalVolumeSection = findViewById(R.id.normal_volume_section)
        switchHiddenVolume.setOnClickListener {
            onClickSwitchHiddenVolume()
        }
        checkboxRememberPath.setOnClickListener {
            if (!checkboxRememberPath.isChecked) {
                checkboxSavePassword.isChecked = false
            }
        }
        checkboxSavePassword.setOnClickListener {
            if (checkboxSavePassword.isChecked) {
                if (biometricCanAuthenticateCode == 0) {
                    checkboxRememberPath.isChecked = checkboxRememberPath.isEnabled
                } else {
                    checkboxSavePassword.isChecked = false
                    printAuthenticateImpossibleError()
                }
            }
        }
    }

    protected open fun onClickSwitchHiddenVolume() {
        if (switchHiddenVolume.isChecked){
            WidgetUtil.show(hiddenVolumeSection, originalHiddenVolumeSectionLayoutParams)
            WidgetUtil.hide(normalVolumeSection)
        } else {
            WidgetUtil.show(normalVolumeSection, originalNormalVolumeSectionLayoutParams)
            WidgetUtil.hide(hiddenVolumeSection)
        }
    }

    protected open fun onPickingDirectory() {}
    protected abstract fun onDirectoryPicked(uri: Uri)

    private fun safePickDirectory() {
        try {
            onPickingDirectory()
            pickDirectory.launch(null)
        } catch (e: ActivityNotFoundException) {
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.error)
                .setMessage(R.string.open_tree_failed)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            STORAGE_PERMISSIONS_REQUEST -> if (grantResults.size == 2) {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    ColoredAlertDialogBuilder(this)
                        .setTitle(R.string.storage_perm_denied)
                        .setMessage(R.string.storage_perm_denied_msg)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else {
                    safePickDirectory()
                }
            }
        }
    }

    protected fun setupFingerprintStuff(){
        originalHiddenVolumeSectionLayoutParams = hiddenVolumeSection.layoutParams as LinearLayout.LayoutParams
        originalNormalVolumeSectionLayoutParams = normalVolumeSection.layoutParams as LinearLayout.LayoutParams
        WidgetUtil.hide(hiddenVolumeSection)
        volumeDatabase = VolumeDatabase(this)
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
                                        success = volumeDatabase.addHash(Volume(currentVolumeName, switchHiddenVolume.isChecked, cipherText, cipherObject.iv))
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
            WidgetUtil.hideWithPadding(checkboxSavePassword)
        }
    }

    private fun canAuthenticate(): Int {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return if (!keyguardManager.isKeyguardSecure) {
            1
        } else {
            when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)){
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
        isCipherReady = true
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
            .setTitle(currentVolumeName)
            .setSubtitle(getString(R.string.encrypt_action_description))
            .setDescription(getString(R.string.fingerprint_instruction))
            .setNegativeButtonText(getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        if (!isCipherReady){
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
    protected fun loadPasswordHash(cipherText: ByteArray, iv: ByteArray, onPasswordDecrypted: (password: ByteArray) -> Unit){
        val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(currentVolumeName)
            .setSubtitle(getString(R.string.decrypt_action_description))
            .setDescription(getString(R.string.fingerprint_instruction))
            .setNegativeButtonText(getString(R.string.cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false)
            .build()
        this.onPasswordDecrypted = onPasswordDecrypted
        actionMode = Cipher.DECRYPT_MODE
        if (!isCipherReady){
            prepareCipher()
        }
        dataToProcess = cipherText
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
        volumeDatabase.getVolumes().forEach { volume ->
            volumeDatabase.removeHash(volume)
        }
        isCipherReady = false
        Toast.makeText(this, R.string.hash_storage_reset, Toast.LENGTH_SHORT).show()
    }

    protected fun loadVolumePath(callback: () -> Unit){
        currentVolumeName = if (switchHiddenVolume.isChecked){
            editVolumeName.text.toString()
        } else {
            editVolumePath.text.toString()
        }
        if (currentVolumeName.isEmpty()) {
            Toast.makeText(this, if (switchHiddenVolume.isChecked) {R.string.enter_volume_name} else {R.string.enter_volume_path}, Toast.LENGTH_SHORT).show()
        } else if (switchHiddenVolume.isChecked && currentVolumeName.contains("/")){
            Toast.makeText(this, R.string.error_slash_in_name, Toast.LENGTH_SHORT).show()
        } else {
            currentVolumePath = if (switchHiddenVolume.isChecked) {
                PathUtils.pathJoin(filesDir.path, currentVolumeName)
            } else {
                currentVolumeName
            }
            callback()
        }
    }
}