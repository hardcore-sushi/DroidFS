package sushi.hardcore.droidfs.add_volume

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import sushi.hardcore.droidfs.*
import sushi.hardcore.droidfs.databinding.DialogSdcardErrorBinding
import sushi.hardcore.droidfs.databinding.FragmentSelectPathBinding
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

class SelectPathFragment: Fragment() {
    companion object {
        private const val KEY_THEME_VALUE = "theme"

        fun newInstance(themeValue: String): SelectPathFragment {
            return SelectPathFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_THEME_VALUE, themeValue)
                }
            }
        }
    }

    private lateinit var binding: FragmentSelectPathBinding
    private val askStoragePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_EXTERNAL_STORAGE] == true && result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true)
            safePickDirectory()
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSelectPathBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.let { arguments ->
            arguments.getString(KEY_THEME_VALUE)?.let { themeValue = it }
        }
        volumeDatabase = VolumeDatabase(requireContext())
        binding.containerHiddenVolume.setOnClickListener {
            binding.switchHiddenVolume.performClick()
        }
        binding.switchHiddenVolume.setOnClickListener {
            showRightSection()
        }
        binding.buttonPickDirectory.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) +
                    ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                )
                    safePickDirectory()
                else
                    askStoragePermissions.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
            } else
                safePickDirectory()
        }
        var isVolumeAlreadySaved = false
        var volumeAction: Action? = null
        binding.editVolumeName.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isVolumeAlreadySaved = volumeDatabase.isVolumeSaved(s.toString(), binding.switchHiddenVolume.isChecked)
                if (isVolumeAlreadySaved)
                    binding.textWarning.apply {
                        text = getString(R.string.volume_alread_saved)
                        visibility = View.VISIBLE
                    }
                else
                    binding.textWarning.visibility = View.GONE
                val path = File(getCurrentVolumePath())
                volumeAction = if (path.isDirectory)
                    if (path.list()?.isEmpty() == true) Action.CREATE else Action.ADD
                else
                    Action.CREATE
                binding.buttonAction.text = getString(when (volumeAction) {
                    Action.CREATE -> R.string.create
                    else -> R.string.add_volume
                })
            }
        })
        binding.editVolumeName.setOnEditorActionListener { _, _, _ -> onPathSelected(isVolumeAlreadySaved, volumeAction); true }
        binding.buttonAction.setOnClickListener { onPathSelected(isVolumeAlreadySaved, volumeAction) }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        (activity as AddVolumeActivity).onFragmentLoaded(true)
        showRightSection()
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

    private fun safePickDirectory() {
        try {
            pickDirectory.launch(null)
        } catch (e: ActivityNotFoundException) {
            CustomAlertDialogBuilder(requireContext(), themeValue)
                .setTitle(R.string.error)
                .setMessage(R.string.open_tree_failed)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun onDirectoryPicked(uri: Uri) {
        val path = PathUtils.getFullPathFromTreeUri(uri, requireContext())
        if (path != null)
            binding.editVolumeName.setText(path)
        else
            CustomAlertDialogBuilder(requireContext(), themeValue)
                .setTitle(R.string.error)
                .setMessage(R.string.path_from_uri_null_error_msg)
                .setPositiveButton(R.string.ok, null)
                .show()
    }

    private fun getCurrentVolumePath(): String {
        return if (binding.switchHiddenVolume.isChecked)
            PathUtils.pathJoin(requireContext().filesDir.path, binding.editVolumeName.text.toString())
        else
            binding.editVolumeName.text.toString()
    }

    private fun onPathSelected(isVolumeAlreadySaved: Boolean, volumeAction: Action?) {
        if (isVolumeAlreadySaved) {
            (activity as AddVolumeActivity).onSelectedAlreadySavedVolume()
        } else {
            if (binding.switchHiddenVolume.isChecked && volumeAction == Action.CREATE) {
                CustomAlertDialogBuilder(requireContext(), themeValue)
                    .setTitle(R.string.warning)
                    .setMessage(R.string.hidden_volume_warning)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        addVolume(volumeAction)
                    }
                    .show()
            } else {
                addVolume(volumeAction)
            }
        }
    }

    private fun addVolume(volumeAction: Action?) {
        val currentVolumeValue = binding.editVolumeName.text.toString()
        val isHidden = binding.switchHiddenVolume.isChecked
        if (currentVolumeValue.isEmpty()) {
            Toast.makeText(
                requireContext(),
                if (isHidden) R.string.enter_volume_name else R.string.enter_volume_path,
                Toast.LENGTH_SHORT
            ).show()
        } else if (isHidden && currentVolumeValue.contains("/")) {
            Toast.makeText(requireContext(), R.string.error_slash_in_name, Toast.LENGTH_SHORT).show()
        } else {
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
                                if (volumeFile.canWrite())
                                    goodDirectory = true
                                else
                                    errorDirectoryNotWritable(volumePath)
                            } else
                                Toast.makeText(requireContext(), R.string.dir_not_empty, Toast.LENGTH_SHORT).show()
                        } else
                            Toast.makeText(requireContext(), R.string.listdir_null_error_msg, Toast.LENGTH_SHORT).show()
                    } else {
                        if (File(PathUtils.getParentPath(volumePath)).canWrite())
                            goodDirectory = true
                        else
                            errorDirectoryNotWritable(volumePath)
                    }
                    if (goodDirectory)
                        (activity as AddVolumeActivity).createVolume(volumePath, isHidden)
                }
                Action.ADD -> {
                    if (!GocryptfsVolume.isGocryptfsVolume(File(volumePath))) {
                        CustomAlertDialogBuilder(requireContext(), themeValue)
                            .setTitle(R.string.error)
                            .setMessage(R.string.error_not_a_volume)
                            .setPositiveButton(R.string.ok, null)
                            .show()
                    } else if (!File(volumePath).canWrite()) {
                        val dialog = CustomAlertDialogBuilder(requireContext(), themeValue)
                            .setTitle(R.string.warning)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok) { _, _ -> addVolume(if (isHidden) currentVolumeValue else volumePath, isHidden) }
                        if (PathUtils.isPathOnExternalStorage(volumePath, requireContext()))
                            dialog.setView(
                                DialogSdcardErrorBinding.inflate(layoutInflater).apply {
                                    path.text = PathUtils.getPackageDataFolder(requireContext())
                                }.root
                            )
                        else
                            dialog.setMessage(R.string.add_cant_write_warning)
                        dialog.show()
                    } else {
                        addVolume(if (isHidden) currentVolumeValue else volumePath, isHidden)
                    }
                }
            }
        }
    }

    private fun errorDirectoryNotWritable(volumePath: String) {
        val dialog = CustomAlertDialogBuilder(requireContext(), themeValue)
            .setTitle(R.string.error)
            .setPositiveButton(R.string.ok, null)
        @SuppressLint("InflateParams")
        if (PathUtils.isPathOnExternalStorage(volumePath, requireContext()))
            dialog.setView(
                layoutInflater.inflate(R.layout.dialog_sdcard_error, null).apply {
                    findViewById<TextView>(R.id.path).text = PathUtils.getPackageDataFolder(requireContext())
                }
            )
        else
            dialog.setMessage(R.string.create_cant_write_error_msg)
        dialog.show()
    }

    private fun addVolume(volumeName: String, isHidden: Boolean) {
        volumeDatabase.saveVolume(Volume(volumeName, isHidden))
        (activity as AddVolumeActivity).onVolumeAdded(false)
    }
}