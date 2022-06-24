package sushi.hardcore.droidfs.add_volume

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import sushi.hardcore.droidfs.*
import sushi.hardcore.droidfs.databinding.FragmentCreateVolumeBinding
import sushi.hardcore.droidfs.filesystems.CryfsVolume
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.filesystems.GocryptfsVolume
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class CreateVolumeFragment: Fragment() {
    companion object {
        private const val KEY_THEME_VALUE = "theme"
        private const val KEY_VOLUME_PATH = "path"
        private const val KEY_IS_HIDDEN = "hidden"
        private const val KEY_PIN_PASSWORDS = ConstValues.PIN_PASSWORDS_KEY
        private const val KEY_USF_FINGERPRINT = "fingerprint"

        fun newInstance(
            themeValue: String,
            volumePath: String,
            isHidden: Boolean,
            pinPasswords: Boolean,
            usfFingerprint: Boolean,
        ): CreateVolumeFragment {
            return CreateVolumeFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_THEME_VALUE, themeValue)
                    putString(KEY_VOLUME_PATH, volumePath)
                    putBoolean(KEY_IS_HIDDEN, isHidden)
                    putBoolean(KEY_PIN_PASSWORDS, pinPasswords)
                    putBoolean(KEY_USF_FINGERPRINT, usfFingerprint)
                }
            }
        }
    }

    private lateinit var binding: FragmentCreateVolumeBinding
    private var themeValue = ConstValues.DEFAULT_THEME_VALUE
    private val volumeTypes = ArrayList<String>(2)
    private lateinit var volumePath: String
    private var isHiddenVolume: Boolean = false
    private var usfFingerprint: Boolean = false
    private lateinit var volumeDatabase: VolumeDatabase
    private var fingerprintProtector: FingerprintProtector? = null
    private var hashStorageReset = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCreateVolumeBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val pinPasswords = requireArguments().let { arguments ->
            arguments.getString(KEY_THEME_VALUE)?.let { themeValue = it }
            volumePath = arguments.getString(KEY_VOLUME_PATH)!!
            isHiddenVolume = arguments.getBoolean(KEY_IS_HIDDEN)
            usfFingerprint = arguments.getBoolean(KEY_USF_FINGERPRINT)
            arguments.getBoolean(KEY_PIN_PASSWORDS)
        }
        volumeDatabase = VolumeDatabase(requireContext())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fingerprintProtector = FingerprintProtector.new(requireActivity(), themeValue, volumeDatabase)
        }
        if (!usfFingerprint || fingerprintProtector == null) {
            binding.checkboxSavePassword.visibility = View.GONE
        }
        if (BuildConfig.GOCRYPTFS_ENABLED) {
            volumeTypes.add(resources.getString(R.string.gocryptfs))
        }
        if (BuildConfig.CRYFS_ENABLED) {
            volumeTypes.add(resources.getString(R.string.cryfs))
        }
        binding.spinnerVolumeType.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            volumeTypes
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val encryptionCipherAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.gocryptfs_encryption_ciphers).toMutableList()
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerVolumeType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val ciphersArray = if (volumeTypes[position] == resources.getString(R.string.gocryptfs)) {
                    if (usfFingerprint && fingerprintProtector != null) {
                        binding.checkboxSavePassword.visibility = View.VISIBLE
                    }
                    R.array.gocryptfs_encryption_ciphers
                } else {
                    binding.checkboxSavePassword.visibility = View.GONE
                    R.array.cryfs_encryption_ciphers
                }
                with(encryptionCipherAdapter) {
                    clear()
                    addAll(resources.getStringArray(ciphersArray).asList())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        binding.spinnerCipher.adapter = encryptionCipherAdapter
        if (pinPasswords) {
            arrayOf(binding.editPassword, binding.editPasswordConfirm).forEach {
                it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            }
        }
        binding.editPasswordConfirm.setOnEditorActionListener { _, _, _ ->
            createVolume()
            true
        }
        binding.buttonCreate.setOnClickListener {
            createVolume()
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        (activity as AddVolumeActivity).onFragmentLoaded(false)
    }

    private fun saveVolume(success: Boolean, volumeType: Byte): SavedVolume? {
        return if (success) {
            val volumeName = if (isHiddenVolume) File(volumePath).name else volumePath
            val volume = SavedVolume(volumeName, isHiddenVolume, volumeType)
            volumeDatabase.apply {
                if (isVolumeSaved(volumeName, isHiddenVolume)) // cleaning old saved path
                    removeVolume(volumeName)
                saveVolume(volume)
            }
            volume
        } else {
            null
        }
    }

    private fun createVolume() {
        val password = WidgetUtil.editTextContentEncode(binding.editPassword)
        val passwordConfirm = WidgetUtil.editTextContentEncode(binding.editPasswordConfirm)
        if (!password.contentEquals(passwordConfirm)) {
            Toast.makeText(requireContext(), R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
            Arrays.fill(password, 0)
            Arrays.fill(passwordConfirm, 0)
        } else {
            Arrays.fill(passwordConfirm, 0)
            var returnedHash: ByteArray? = null
            if (binding.checkboxSavePassword.isChecked)
                returnedHash = ByteArray(GocryptfsVolume.KeyLen)
            object: LoadingTask<SavedVolume?>(requireActivity() as AppCompatActivity, themeValue, R.string.loading_msg_create) {
                override suspend fun doTask(): SavedVolume? {
                    val volumeFile = File(volumePath)
                    if (!volumeFile.exists())
                        volumeFile.mkdirs()
                    val volume = if (volumeTypes[binding.spinnerVolumeType.selectedItemPosition] == resources.getString(R.string.gocryptfs)) {
                        val xchacha = when (binding.spinnerCipher.selectedItemPosition) {
                            0 -> 0
                            1 -> 1
                            else -> -1
                        }
                        saveVolume(GocryptfsVolume.createVolume(
                            volumePath,
                            password,
                            false,
                            xchacha,
                            GocryptfsVolume.ScryptDefaultLogN,
                            ConstValues.CREATOR,
                            returnedHash
                        ), EncryptedVolume.GOCRYPTFS_VOLUME_TYPE)
                    } else {
                        saveVolume(CryfsVolume.create(
                            volumePath,
                            CryfsVolume.getLocalStateDir(activity.filesDir.path),
                            password,
                            resources.getStringArray(R.array.cryfs_encryption_ciphers)[binding.spinnerCipher.selectedItemPosition]
                        ), EncryptedVolume.CRYFS_VOLUME_TYPE)
                    }
                    Arrays.fill(password, 0)
                    return volume
                }
            }.startTask(lifecycleScope) { volume ->
                if (volume == null) {
                    CustomAlertDialogBuilder(requireContext(), themeValue)
                        .setTitle(R.string.error)
                        .setMessage(R.string.create_volume_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else {
                    @SuppressLint("NewApi") // if fingerprintProtector is null checkboxSavePassword is hidden
                    if (binding.checkboxSavePassword.isChecked && returnedHash != null) {
                        fingerprintProtector!!.let {
                            it.listener = object : FingerprintProtector.Listener {
                                override fun onHashStorageReset() {
                                    hashStorageReset = true
                                    // retry
                                    it.savePasswordHash(volume, returnedHash)
                                }
                                override fun onPasswordHashDecrypted(hash: ByteArray) {} // shouldn't happen here
                                override fun onPasswordHashSaved() {
                                    Arrays.fill(returnedHash, 0)
                                    onVolumeCreated()
                                }
                                override fun onFailed(pending: Boolean) {
                                    if (!pending) {
                                        Arrays.fill(returnedHash, 0)
                                        onVolumeCreated()
                                    }
                                }
                            }
                            it.savePasswordHash(volume, returnedHash)
                        }
                    } else onVolumeCreated()
                }
            }
        }
    }

    private fun onVolumeCreated() {
        (activity as AddVolumeActivity).onVolumeAdded(hashStorageReset)
    }
}