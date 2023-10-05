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
import sushi.hardcore.droidfs.util.Compat
import sushi.hardcore.droidfs.util.ObjRef
import sushi.hardcore.droidfs.util.WidgetUtil
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File
import java.util.*

class CreateVolumeFragment: Fragment() {
    companion object {
        private const val KEY_THEME_VALUE = "theme"
        private const val KEY_VOLUME_PATH = "path"
        private const val KEY_IS_HIDDEN = "hidden"
        private const val KEY_REMEMBER_VOLUME = "remember"
        private const val KEY_PIN_PASSWORDS = Constants.PIN_PASSWORDS_KEY
        private const val KEY_USF_FINGERPRINT = "fingerprint"

        fun newInstance(
            theme: Theme,
            volumePath: String,
            isHidden: Boolean,
            rememberVolume: Boolean,
            pinPasswords: Boolean,
            usfFingerprint: Boolean,
        ): CreateVolumeFragment {
            return CreateVolumeFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_THEME_VALUE, theme)
                    putString(KEY_VOLUME_PATH, volumePath)
                    putBoolean(KEY_IS_HIDDEN, isHidden)
                    putBoolean(KEY_REMEMBER_VOLUME, rememberVolume)
                    putBoolean(KEY_PIN_PASSWORDS, pinPasswords)
                    putBoolean(KEY_USF_FINGERPRINT, usfFingerprint)
                }
            }
        }
    }

    private lateinit var binding: FragmentCreateVolumeBinding
    private lateinit var theme: Theme
    private val volumeTypes = ArrayList<String>(2)
    private lateinit var volumePath: String
    private var isHiddenVolume: Boolean = false
    private var rememberVolume: Boolean = false
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
            theme = Compat.getParcelable(arguments, KEY_THEME_VALUE)!!
            volumePath = arguments.getString(KEY_VOLUME_PATH)!!
            isHiddenVolume = arguments.getBoolean(KEY_IS_HIDDEN)
            rememberVolume = arguments.getBoolean(KEY_REMEMBER_VOLUME)
            usfFingerprint = arguments.getBoolean(KEY_USF_FINGERPRINT)
            arguments.getBoolean(KEY_PIN_PASSWORDS)
        }
        volumeDatabase = VolumeDatabase(requireContext())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            fingerprintProtector = FingerprintProtector.new(requireActivity(), theme, volumeDatabase)
        }
        if (!rememberVolume || !usfFingerprint || fingerprintProtector == null) {
            binding.checkboxSavePassword.visibility = View.GONE
        }
        if (!BuildConfig.GOCRYPTFS_DISABLED) {
            volumeTypes.add(resources.getString(R.string.gocryptfs))
        }
        if (!BuildConfig.CRYFS_DISABLED) {
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
                    R.array.gocryptfs_encryption_ciphers
                } else {
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

    private fun createVolume() {
        val password = WidgetUtil.encodeEditTextContent(binding.editPassword)
        val passwordConfirm = WidgetUtil.encodeEditTextContent(binding.editPasswordConfirm)
        if (!password.contentEquals(passwordConfirm)) {
            Toast.makeText(requireContext(), R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
            Arrays.fill(password, 0)
            Arrays.fill(passwordConfirm, 0)
        } else {
            Arrays.fill(passwordConfirm, 0)
            val returnedHash: ObjRef<ByteArray?>? = if (binding.checkboxSavePassword.isChecked) {
                ObjRef(null)
            } else {
                null
            }
            val encryptedVolume = ObjRef<EncryptedVolume?>(null)
            object: LoadingTask<Byte>(requireActivity() as AppCompatActivity, theme, R.string.loading_msg_create) {
                private fun generateResult(success: Boolean, volumeType: Byte): Byte {
                    return if (success) {
                        volumeType
                    } else {
                        -1
                    }
                }

                override suspend fun doTask(): Byte {
                    val volumeFile = File(volumePath)
                    if (!volumeFile.exists())
                        volumeFile.mkdirs()
                    val result = if (volumeTypes[binding.spinnerVolumeType.selectedItemPosition] == resources.getString(R.string.gocryptfs)) {
                        val xchacha = when (binding.spinnerCipher.selectedItemPosition) {
                            0 -> 0
                            1 -> 1
                            else -> -1
                        }
                        generateResult(GocryptfsVolume.createAndOpenVolume(
                            volumePath,
                            password,
                            false,
                            xchacha,
                            returnedHash?.apply {
                                value = ByteArray(GocryptfsVolume.KeyLen)
                            }?.value,
                            encryptedVolume,
                        ), EncryptedVolume.GOCRYPTFS_VOLUME_TYPE)
                    } else {
                        encryptedVolume.value = CryfsVolume.create(
                            volumePath,
                            CryfsVolume.getLocalStateDir(activity.filesDir.path),
                            password,
                            returnedHash,
                            resources.getStringArray(R.array.cryfs_encryption_ciphers)[binding.spinnerCipher.selectedItemPosition],
                        )
                        generateResult(encryptedVolume.value != null, EncryptedVolume.CRYFS_VOLUME_TYPE)
                    }
                    Arrays.fill(password, 0)
                    return result
                }
            }.startTask(lifecycleScope) { result ->
                if (result.compareTo(-1) == 0) {
                    CustomAlertDialogBuilder(requireContext(), theme)
                        .setTitle(R.string.error)
                        .setMessage(R.string.create_volume_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else {
                    val volumeName = if (isHiddenVolume) File(volumePath).name else volumePath
                    val volume = VolumeData(VolumeData.newUuid(), volumeName, isHiddenVolume, result)
                    var isVolumeSaved = false
                    volumeDatabase.apply {
                        if (isVolumeSaved(volumeName, isHiddenVolume)) // cleaning old saved path
                            removeVolume(volume)
                        if (rememberVolume) {
                            isVolumeSaved = saveVolume(volume)
                        }
                    }
                    val volumeId = encryptedVolume.value?.let {
                        (activity?.application as VolumeManagerApp).volumeManager.insert(it, volume)
                    }
                    @SuppressLint("NewApi") // if fingerprintProtector is null checkboxSavePassword is hidden
                    if (isVolumeSaved && binding.checkboxSavePassword.isChecked && returnedHash != null) {
                        fingerprintProtector!!.let {
                            it.listener = object : FingerprintProtector.Listener {
                                override fun onHashStorageReset() {
                                    hashStorageReset = true
                                    // retry
                                    it.savePasswordHash(volume, returnedHash.value!!)
                                }
                                override fun onPasswordHashDecrypted(hash: ByteArray) {} // shouldn't happen here
                                override fun onPasswordHashSaved() {
                                    Arrays.fill(returnedHash.value!!, 0)
                                    onVolumeCreated(volumeId, volume.shortName)
                                }
                                override fun onFailed(pending: Boolean) {
                                    if (!pending) {
                                        Arrays.fill(returnedHash.value!!, 0)
                                        onVolumeCreated(volumeId, volume.shortName)
                                    }
                                }
                            }
                            it.savePasswordHash(volume, returnedHash.value!!)
                        }
                    } else {
                        onVolumeCreated(volumeId, volume.shortName)
                    }
                }
            }
        }
    }

    private fun onVolumeCreated(id: Int?, volumeShortName: String) {
        (activity as AddVolumeActivity).apply {
            if (id == null) {
                finish()
            } else {
                startExplorer(id, volumeShortName)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        binding.editPassword.text.clear()
        binding.editPasswordConfirm.text.clear()
    }
}