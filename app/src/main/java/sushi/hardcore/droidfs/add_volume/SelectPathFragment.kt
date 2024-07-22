package sushi.hardcore.droidfs.add_volume

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
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
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.Theme
import sushi.hardcore.droidfs.VolumeData
import sushi.hardcore.droidfs.VolumeDatabase
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.databinding.DialogSdcardErrorBinding
import sushi.hardcore.droidfs.databinding.FragmentSelectPathBinding
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.Compat
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

class SelectPathFragment: Fragment() {
    internal class InputViewModel: ViewModel() {
        var showEditText = false
    }

    companion object {
        private const val KEY_THEME_VALUE = "theme"
        private const val KEY_PICK_MODE = "pick"

        fun newInstance(theme: Theme, pickMode: Boolean): SelectPathFragment {
            return SelectPathFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_THEME_VALUE, theme)
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
            CustomAlertDialogBuilder(requireContext(), theme)
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
    private lateinit var app: VolumeManagerApp
    private lateinit var theme: Theme
    private lateinit var volumeDatabase: VolumeDatabase
    private lateinit var filesDir: String
    private lateinit var sharedPrefs: SharedPreferences
    private var pickMode = false
    private var originalRememberVolume = true
    private var currentVolumeData: VolumeData? = null
    private var volumeAction: Action? = null
    private val inputViewModel: InputViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSelectPathBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        app = requireActivity().application as VolumeManagerApp
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        originalRememberVolume = sharedPrefs.getBoolean(Constants.REMEMBER_VOLUME_KEY, true)
        binding.switchRemember.isChecked = originalRememberVolume
        arguments?.let { arguments ->
            theme = Compat.getParcelable(arguments, KEY_THEME_VALUE)!!
            pickMode = arguments.getBoolean(KEY_PICK_MODE)
        }
        volumeDatabase = VolumeDatabase(requireContext())
        filesDir = requireContext().filesDir.path
        binding.containerHiddenVolume.setOnClickListener {
            binding.switchHiddenVolume.performClick()
        }
        binding.switchHiddenVolume.setOnClickListener {
            updateUi()
        }
        binding.buttonPickDirectory.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    launchPickDirectory()
                } else {
                    app.isStartingExternalApp = true
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
                    app.isStartingExternalApp = true
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
        binding.buttonEnterPath.setOnClickListener {
            inputViewModel.showEditText = true
            updateUi()
            binding.editVolumeName.requestFocus()
            (app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                binding.editVolumeName,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
        binding.editVolumeName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                updateUi(s)
            }
        })
        binding.switchRemember.setOnCheckedChangeListener { _, _ -> updateUi() }
        binding.editVolumeName.setOnEditorActionListener { _, _, _ ->
            if (binding.editVolumeName.text.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    if (binding.switchHiddenVolume.isChecked) R.string.empty_volume_name else R.string.empty_volume_path,
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                onPathSelected()
            }
            true
        }
        binding.buttonAction.setOnClickListener { onPathSelected() }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        (activity as AddVolumeActivity).onFragmentLoaded(true)
    }

    private fun launchPickDirectory() {
        app.isStartingExternalApp = true
        PathUtils.safePickDirectory(pickDirectory, requireContext(), theme)
    }

    private fun updateUi(volumeName: CharSequence = binding.editVolumeName.text) {
        var warning = -1
        fun updateWarning() {
            if (warning == -1) {
                binding.textWarning.isVisible = false
            } else {
                binding.textWarning.isVisible = true
                binding.textWarning.text = getString(warning)
            }
        }

        val hidden = binding.switchHiddenVolume.isChecked
        binding.editVolumeName.isVisible = hidden || inputViewModel.showEditText
        binding.buttonPickDirectory.isVisible = !hidden
        binding.textOr.isVisible = !hidden && !inputViewModel.showEditText
        binding.buttonEnterPath.isVisible = !hidden && !inputViewModel.showEditText
        if (hidden) {
            binding.textLabel.text = getString(R.string.volume_name_label)
            binding.editVolumeName.hint = getString(R.string.volume_name_hint)
        } else {
            binding.textLabel.text = getString(R.string.volume_path_label)
            binding.editVolumeName.hint = getString(R.string.volume_path_hint)
        }
        if (hidden && volumeName.contains(PathUtils.SEPARATOR)) {
            warning = R.string.error_slash_in_name
        }
        // exit early if possible to avoid filesystem queries
        if (volumeName.isEmpty() || warning != -1 || (!hidden && !inputViewModel.showEditText)) {
            binding.buttonAction.isVisible = false
            binding.switchRemember.isVisible = false
            updateWarning()
            return
        }
        val path = File(getCurrentVolumePath())
        volumeAction = if (path.isDirectory) {
            if (path.list()?.isEmpty() == true) {
                Action.CREATE
            } else if (pickMode || !binding.switchRemember.isChecked) {
                Action.OPEN
            } else {
                Action.ADD
            }
        } else {
            Action.CREATE
        }
        val valid = !(volumeAction == Action.CREATE && pickMode)
        binding.switchRemember.isVisible = valid
        binding.buttonAction.isVisible = valid
        if (valid) {
            binding.buttonAction.text = getString(volumeAction!!.getStringResId())
            currentVolumeData = if (volumeAction == Action.CREATE) {
                null
            } else {
                volumeDatabase.getVolume(volumeName.toString(), hidden)
            }
            if (currentVolumeData != null) {
                warning = R.string.volume_alread_saved
            }
        } else {
            warning = R.string.choose_existing_volume
        }
        updateWarning()
    }

    private fun onDirectoryPicked(uri: Uri) {
        val path = PathUtils.getFullPathFromTreeUri(uri, requireContext())
        if (path == null) {
            CustomAlertDialogBuilder(requireContext(), theme)
                .setTitle(R.string.error)
                .setMessage(R.string.path_error)
                .setPositiveButton(R.string.ok, null)
                .show()
        } else {
            binding.editVolumeName.setText(path)
            inputViewModel.showEditText = true
            updateUi(path)
        }
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
                putBoolean(Constants.REMEMBER_VOLUME_KEY, binding.switchRemember.isChecked)
                apply()
            }
        }
        if (currentVolumeData == null) { // volume not known
            val currentVolumeValue = binding.editVolumeName.text.toString()
            val isHidden = binding.switchHiddenVolume.isChecked
            if (isHidden && currentVolumeValue.contains(PathUtils.SEPARATOR)) {
                Toast.makeText(requireContext(), R.string.error_slash_in_name, Toast.LENGTH_SHORT).show()
            } else if (isHidden && volumeAction == Action.CREATE) {
                CustomAlertDialogBuilder(requireContext(), theme)
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
            with (activity as AddVolumeActivity) {
                if (volumeAction!! == Action.OPEN) {
                    openVolume(currentVolumeData!!, true)
                } else {
                    onVolumeAdded()
                }
            }
        }
    }

    private fun onNewVolumeSelected(currentVolumeValue: String, isHidden: Boolean) {
        val volumePath = getCurrentVolumePath()
        if (volumeAction!! == Action.CREATE) {
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
                        Toast.makeText(requireContext(), R.string.dir_not_empty, Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        R.string.listdir_null_error_msg,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                if (File(PathUtils.getParentPath(volumePath)).canWrite()) {
                    goodDirectory = true
                } else {
                    errorDirectoryNotWritable(volumePath)
                }
            }
            if (goodDirectory) {
                (activity as AddVolumeActivity).createVolume(
                    volumePath,
                    isHidden,
                    binding.switchRemember.isChecked
                )
            }
        } else {
            val volumeType = EncryptedVolume.getVolumeType(volumePath)
            if (volumeType < 0) {
                CustomAlertDialogBuilder(requireContext(), theme)
                    .setTitle(R.string.error)
                    .setMessage(R.string.error_not_a_volume)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            } else if (!File(volumePath).canWrite()) {
                val dialog = CustomAlertDialogBuilder(requireContext(), theme)
                    .setTitle(R.string.warning)
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok) { _, _ -> onExistingVolumeSelected(if (isHidden) currentVolumeValue else volumePath, isHidden, volumeType) }
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
                onExistingVolumeSelected(if (isHidden) currentVolumeValue else volumePath, isHidden, volumeType)
            }
        }
    }

    // called when the user tries to create a volume in a non-writable directory
    private fun errorDirectoryNotWritable(volumePath: String) {
        val dialog = CustomAlertDialogBuilder(requireContext(), theme)
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

    private fun onExistingVolumeSelected(volumeName: String, isHidden: Boolean, volumeType: Byte) {
        val volumeData = VolumeData(VolumeData.newUuid(), volumeName, isHidden, volumeType)
        if (binding.switchRemember.isChecked) {
            volumeDatabase.saveVolume(volumeData)
        }
        with (activity as AddVolumeActivity) {
            if (volumeAction!! == Action.OPEN) {
                openVolume(volumeData, binding.switchRemember.isChecked)
            } else {
                onVolumeAdded()
            }
        }
    }
}