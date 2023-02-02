package sushi.hardcore.droidfs.add_volume

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeDatabase
import sushi.hardcore.droidfs.databinding.DialogSdcardErrorBinding
import sushi.hardcore.droidfs.databinding.FragmentSelectPathBinding
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

class SelectPathFragment: Fragment() {
    companion object {
        private const val KEY_THEME_VALUE = "theme"
        private const val KEY_PICK_MODE = "pick"

        fun newInstance(themeValue: String, pickMode: Boolean): SelectPathFragment {
            return SelectPathFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_THEME_VALUE, themeValue)
                    putBoolean(KEY_PICK_MODE, pickMode)
                }
            }
        }
    }

    private lateinit var binding: FragmentSelectPathBinding
    private val askStoragePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_EXTERNAL_STORAGE] == true && result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true)
            launchPickDirectory()
        else
            CustomAlertDialogBuilder(requireContext(), themeValue)
                .setTitle(R.string.storage_perm_denied)
                .setMessage(R.string.storage_perm_denied_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, null)
                .show()
    }
    private val pickDirectory = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null)
            onDirectoryPicked(uri)
    }
    private var themeValue = ConstValues.DEFAULT_THEME_VALUE
    private lateinit var volumeDatabase: VolumeDatabase
    private lateinit var filesDir: String
    private lateinit var sharedPrefs: SharedPreferences
    private var pickMode = false
    private var originalRememberVolume = true
    private var currentVolumeData: VolumeData? = null
    private var volumeAction: Action? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSelectPathBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        originalRememberVolume = sharedPrefs.getBoolean(ConstValues.REMEMBER_VOLUME_KEY, true)
        binding.switchRemember.isChecked = originalRememberVolume
        arguments?.let { arguments ->
            arguments.getString(KEY_THEME_VALUE)?.let { themeValue = it }
            pickMode = arguments.getBoolean(KEY_PICK_MODE)
        }
        if (pickMode) {
            binding.buttonAction.text = getString(R.string.add_volume)
        }
        volumeDatabase = VolumeDatabase(requireContext())
        filesDir = requireContext().filesDir.path
        binding.containerHiddenVolume.setOnClickListener {
            binding.switchHiddenVolume.performClick()
        }
        binding.switchHiddenVolume.setOnClickListener {
            showRightSection()
            refreshStatus(binding.editVolumeName.text)
        }
        binding.buttonPickDirectory.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    launchPickDirectory()
                } else {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+requireContext().packageName)))
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) + ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    launchPickDirectory()
                } else {
                    askStoragePermissions.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            } else {
                launchPickDirectory()
            }
        }
        binding.editVolumeName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                refreshStatus(s)
            }
        })
        binding.switchRemember.setOnCheckedChangeListener { _, _ -> refreshButtonText() }
        binding.editVolumeName.setOnEditorActionListener { _, _, _ -> onPathSelected(); true }
        binding.buttonAction.setOnClickListener { onPathSelected() }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        (activity as AddVolumeActivity).onFragmentLoaded(true)
        showRightSection()
    }

    private fun launchPickDirectory() {
        (activity as AddVolumeActivity).shouldCloseVolume = false
        PathUtils.safePickDirectory(pickDirectory, requireContext(), themeValue)
    }

    private fun showRightSection() {
        if (binding.switchHiddenVolume.isChecked) {
            binding.textLabel.text = requireContext().getString(R.string.volume_name_label)
            binding.editVolumeName.hint = requireContext().getString(R.string.volume_name_hint)
            binding.buttonPickDirectory.visibility = View.GONE
        } else {
            binding.textLabel.text = requireContext().getString(R.string.volume_path_label)
            binding.editVolumeName.hint = requireContext().getString(R.string.volume_path_hint)
            binding.buttonPickDirectory.visibility = View.VISIBLE
        }
    }

    private fun refreshButtonText() {
        binding.buttonAction.text = getString(
            if (pickMode || volumeAction == Action.ADD) {
                if (binding.switchRemember.isChecked || currentVolumeData != null) {
                    R.string.add_volume
                } else {
                    R.string.open_volume
                }
            } else {
                R.string.create_volume
            }
        )
    }

    private fun refreshStatus(content: CharSequence) {
        val path = File(getCurrentVolumePath())
        volumeAction = if (path.isDirectory) {
            if (path.list()?.isEmpty() == true || content.isEmpty()) Action.CREATE else Action.ADD
        } else {
            Action.CREATE
        }
        currentVolumeData = if (volumeAction == Action.CREATE) {
            null
        } else {
            volumeDatabase.getVolume(content.toString(), binding.switchHiddenVolume.isChecked)
        }
        binding.textWarning.visibility = if (volumeAction == Action.CREATE && pickMode) {
            binding.textWarning.text = getString(R.string.choose_existing_volume)
            binding.buttonAction.isEnabled = false
            View.VISIBLE
        } else {
            refreshButtonText()
            binding.buttonAction.isEnabled = true
            if (currentVolumeData == null) {
                View.GONE
            } else {
                binding.textWarning.text = getString(R.string.volume_alread_saved)
                View.VISIBLE
            }
        }
    }

    private fun onDirectoryPicked(uri: Uri) {
        val path = PathUtils.getFullPathFromTreeUri(uri, requireContext())
        if (path != null)
            binding.editVolumeName.setText(path)
        else
            CustomAlertDialogBuilder(requireContext(), themeValue)
                .setTitle(R.string.error)
                .setMessage(R.string.path_error)
                .setPositiveButton(R.string.ok, null)
                .show()
    }

    private fun getCurrentVolumePath(): String {
        return if (binding.switchHiddenVolume.isChecked)
            VolumeData.getHiddenVolumeFullPath(filesDir, binding.editVolumeName.text.toString())
        else
            binding.editVolumeName.text.toString()
    }

    private fun onPathSelected() {
        if (binding.switchRemember.isChecked != originalRememberVolume) {
            with(sharedPrefs.edit()) {
                putBoolean(ConstValues.REMEMBER_VOLUME_KEY, binding.switchRemember.isChecked)
                apply()
            }
        }
        if (currentVolumeData == null) { // volume not known
            val currentVolumeValue = binding.editVolumeName.text.toString()
            val isHidden = binding.switchHiddenVolume.isChecked
            if (currentVolumeValue.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    if (isHidden) R.string.enter_volume_name else R.string.enter_volume_path,
                    Toast.LENGTH_SHORT
                ).show()
            } else if (isHidden && currentVolumeValue.contains(PathUtils.SEPARATOR)) {
                Toast.makeText(requireContext(), R.string.error_slash_in_name, Toast.LENGTH_SHORT).show()
            } else if (isHidden && volumeAction == Action.CREATE) {
                CustomAlertDialogBuilder(requireContext(), themeValue)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.hidden_volume_warning)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        onNewVolumeSelected(currentVolumeValue, isHidden)
                    }
                    .show()
            } else {
                onNewVolumeSelected(currentVolumeValue, isHidden)
            }
        } else {
            (activity as AddVolumeActivity).onVolumeSelected(currentVolumeData!!, true)
        }
    }

    private fun onNewVolumeSelected(currentVolumeValue: String, isHidden: Boolean) {
        val volumePath = getCurrentVolumePath()
        when (volumeAction!!) {
            Action.CREATE -> {
                val volumeFile = File(volumePath)
                var goodDirectory = false
                if (volumeFile.isFile) {
                    Toast.makeText(requireContext(), R.string.error_is_file, Toast.LENGTH_SHORT).show()
                } else if (volumeFile.isDirectory) {
                    val dirContent = volumeFile.list()
                    if (dirContent != null) {
                        if (dirContent.isEmpty()) {
                            if (volumeFile.canWrite()) {
                                goodDirectory = true
                            } else {
                                errorDirectoryNotWritable(volumePath)
                            }
                        } else {
                            Toast.makeText(requireContext(), R.string.dir_not_empty, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), R.string.listdir_null_error_msg, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (File(PathUtils.getParentPath(volumePath)).canWrite()) {
                        goodDirectory = true
                    } else {
                        errorDirectoryNotWritable(volumePath)
                    }
                }
                if (goodDirectory) {
                    (activity as AddVolumeActivity).createVolume(volumePath, isHidden, binding.switchRemember.isChecked)
                }
            }
            Action.ADD -> {
                val volumeType = EncryptedVolume.getVolumeType(volumePath)
                if (volumeType < 0) {
                    CustomAlertDialogBuilder(requireContext(), themeValue)
                        .setTitle(R.string.error)
                        .setMessage(R.string.error_not_a_volume)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                } else if (!File(volumePath).canWrite()) {
                    val dialog = CustomAlertDialogBuilder(requireContext(), themeValue)
                        .setTitle(R.string.warning)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> addVolume(if (isHidden) currentVolumeValue else volumePath, isHidden, volumeType) }
                    if (PathUtils.isPathOnExternalStorage(volumePath, requireContext())) {
                        dialog.setView(
                            DialogSdcardErrorBinding.inflate(layoutInflater).apply {
                                path.text = PathUtils.getPackageDataFolder(requireContext())
                                footer.text = getString(R.string.sdcard_error_add_footer)
                            }.root
                        )
                    } else {
                        dialog.setMessage(R.string.add_cant_write_warning)
                    }
                    dialog.show()
                } else {
                    addVolume(if (isHidden) currentVolumeValue else volumePath, isHidden, volumeType)
                }
            }
        }
    }

    // called when the user tries to create a volume in a non-writable directory
    private fun errorDirectoryNotWritable(volumePath: String) {
        val dialog = CustomAlertDialogBuilder(requireContext(), themeValue)
            .setTitle(R.string.error)
            .setPositiveButton(R.string.ok, null)
        @SuppressLint("InflateParams")
        if (PathUtils.isPathOnExternalStorage(volumePath, requireContext()))
            dialog.setView(
                DialogSdcardErrorBinding.inflate(layoutInflater).apply {
                    path.text = PathUtils.getPackageDataFolder(requireContext())
                }.root
            )
        else
            dialog.setMessage(R.string.create_cant_write_error_msg)
        dialog.show()
    }

    private fun addVolume(volumeName: String, isHidden: Boolean, volumeType: Byte) {
        val volumeData = VolumeData(volumeName, isHidden, volumeType)
        if (binding.switchRemember.isChecked) {
            volumeDatabase.saveVolume(volumeData)
        }
        (activity as AddVolumeActivity).onVolumeSelected(volumeData, binding.switchRemember.isChecked)
    }
}