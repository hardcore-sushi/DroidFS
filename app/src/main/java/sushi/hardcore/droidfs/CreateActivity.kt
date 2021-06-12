package sushi.hardcore.droidfs

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import sushi.hardcore.droidfs.databinding.ActivityCreateBinding
import sushi.hardcore.droidfs.explorers.ExplorerActivity
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.Wiper
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.File
import java.util.*

class CreateActivity : VolumeActionActivity() {
    private var sessionID = -1
    private var isStartingExplorer = false
    private lateinit var binding: ActivityCreateBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupLayout()
        setupFingerprintStuff()
        binding.editPasswordConfirm.setOnEditorActionListener { _, _, _ ->
            createVolume()
            true
        }
        binding.buttonCreate.setOnClickListener {
            createVolume()
        }
    }

    override fun onClickSwitchHiddenVolume() {
        super.onClickSwitchHiddenVolume()
        if (switchHiddenVolume.isChecked){
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.hidden_volume_warning)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    override fun onDirectoryPicked(uri: Uri) {
        if (PathUtils.isTreeUriOnPrimaryStorage(uri)){
            val path = PathUtils.getFullPathFromTreeUri(uri, this)
            if (path != null){
                editVolumePath.setText(path)
            } else {
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.path_from_uri_null_error_msg)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
        } else {
            ColoredAlertDialogBuilder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.create_on_sdcard_error_msg)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    fun createVolume() {
        loadVolumePath {
            val password = binding.editPassword.text.toString().toCharArray()
            val passwordConfirm = binding.editPasswordConfirm.text.toString().toCharArray()
            if (!password.contentEquals(passwordConfirm)) {
                Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show()
            } else {
                object: LoadingTask(this, R.string.loading_msg_create){
                    override fun doTask(activity: AppCompatActivity) {
                        val volumeFile = File(currentVolumePath)
                        var goodDirectory = false
                        if (!volumeFile.isDirectory) {
                            if (volumeFile.mkdirs()) {
                                goodDirectory = true
                            } else {
                                stopTask {
                                    ColoredAlertDialogBuilder(activity)
                                        .setTitle(R.string.warning)
                                        .setMessage(R.string.create_cant_write_error_msg)
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                                }
                            }
                        } else {
                            val dirContent = volumeFile.list()
                            if (dirContent != null){
                                if (dirContent.isEmpty()) {
                                    if (volumeFile.canWrite()){
                                        goodDirectory = true
                                    } else {
                                        stopTask {
                                            ColoredAlertDialogBuilder(activity)
                                                .setTitle(R.string.warning)
                                                .setMessage(R.string.create_cant_write_error_msg)
                                                .setPositiveButton(R.string.ok, null)
                                                .show()
                                        }
                                    }
                                } else {
                                    stopTaskWithToast(R.string.dir_not_empty)
                                }
                            } else {
                                stopTaskWithToast(R.string.listdir_null_error_msg)
                            }
                        }
                        if (goodDirectory) {
                            if (GocryptfsVolume.createVolume(currentVolumePath, password, false, GocryptfsVolume.ScryptDefaultLogN, ConstValues.creator)) {
                                var returnedHash: ByteArray? = null
                                if (checkboxSavePassword.isChecked){
                                    returnedHash = ByteArray(GocryptfsVolume.KeyLen)
                                }
                                sessionID = GocryptfsVolume.init(currentVolumePath, password, null, returnedHash)
                                if (sessionID != -1) {
                                    if (checkboxRememberPath.isChecked) {
                                        if (volumeDatabase.isVolumeSaved(currentVolumeName)) { //cleaning old saved path
                                            volumeDatabase.removeVolume(Volume(currentVolumeName))
                                        }
                                        volumeDatabase.saveVolume(Volume(currentVolumeName, switchHiddenVolume.isChecked))
                                    }
                                    if (checkboxSavePassword.isChecked && returnedHash != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                                        stopTask {
                                            savePasswordHash(returnedHash) {
                                                startExplorer()
                                            }
                                        }
                                    } else {
                                        stopTask { startExplorer() }
                                    }
                                } else {
                                    stopTaskWithToast(R.string.open_volume_failed)
                                }
                            } else {
                                stopTask {
                                    ColoredAlertDialogBuilder(activity)
                                        .setTitle(R.string.error)
                                        .setMessage(R.string.create_volume_failed)
                                        .setPositiveButton(R.string.ok, null)
                                        .show()
                                }
                            }
                        }
                    }
                    override fun doFinally(activity: AppCompatActivity) {
                        Arrays.fill(password, 0.toChar())
                        Arrays.fill(passwordConfirm, 0.toChar())
                    }
                }
            }
        }
    }

    private fun startExplorer(){
        ColoredAlertDialogBuilder(this)
                .setTitle(R.string.success_volume_create)
                .setMessage(R.string.success_volume_create_msg)
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val intent = Intent(this, ExplorerActivity::class.java)
                    intent.putExtra("sessionID", sessionID)
                    intent.putExtra("volume_name", File(currentVolumeName).name)
                    startActivity(intent)
                    isStartingExplorer = true
                    finish()
                }
                .show()
    }

    override fun onPause() {
        super.onPause()
        //Closing volume if leaving activity while showing dialog
        if (sessionID != -1 && !isStartingExplorer) {
            GocryptfsVolume(sessionID).close()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Wiper.wipeEditText(binding.editPassword)
        Wiper.wipeEditText(binding.editPasswordConfirm)
    }
}
