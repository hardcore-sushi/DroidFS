package sushi.hardcore.droidfs

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Flash
import kotlinx.android.synthetic.main.activity_camera.*
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.MiscUtils
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : AppCompatActivity() {
    companion object {
        private val flashModes = listOf(Flash.AUTO, Flash.ON, Flash.OFF)
        private const val fileNameRandomMin = 100000
        private const val fileNameRandomMax = 999999
        private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        private val random = Random()
    }
    private var currentFlashModeIndex = 0
    private lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var outputDirectory: String
    private lateinit var fileName: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        gocryptfsVolume = GocryptfsVolume(intent.getIntExtra("sessionID", -1))
        outputDirectory = intent.getStringExtra("path")!!
        camera.setLifecycleOwner(this)
        camera.addCameraListener(object: CameraListener(){
            override fun onPictureTaken(result: PictureResult) {
                take_photo_button.onPhotoTaken()
                val inputStream = ByteArrayInputStream(result.data)
                if (gocryptfsVolume.importFile(inputStream, PathUtils.path_join(outputDirectory, fileName))){
                    Toast.makeText(applicationContext, getString(R.string.picture_save_success, fileName), Toast.LENGTH_SHORT).show()
                } else {
                    ColoredAlertDialogBuilder(applicationContext)
                        .setTitle(R.string.error)
                        .setMessage(R.string.picture_save_failed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .show()
                }
            }
        })
        take_photo_button.onClick = ::onClickTakePhoto
    }

    private fun onClickTakePhoto() {
        val baseName = "IMG_"+dateFormat.format(Date())+"_"
        do {
            fileName = baseName+(random.nextInt(fileNameRandomMax-fileNameRandomMin)+fileNameRandomMin)+".jpg"
        } while (gocryptfsVolume.pathExists(fileName))
        camera.takePicture()
    }

    fun onClickFlash(view: View) {
        currentFlashModeIndex = MiscUtils.incrementIndex(currentFlashModeIndex, flashModes)
        camera.flash = flashModes[currentFlashModeIndex]
        image_flash.setImageResource(when (camera.flash) {
            Flash.AUTO -> R.drawable.icon_flash_auto
            Flash.ON -> R.drawable.icon_flash_on
            else -> R.drawable.icon_flash_off
        })
    }

    fun onClickCameraSwitch(view: View) {
        camera.toggleFacing()
        if (camera.facing == Facing.FRONT){
            image_camera_switch.setImageResource(R.drawable.icon_camera_back)
        } else {
            image_camera_switch.setImageResource(R.drawable.icon_camera_front)
            Thread {
                Thread.sleep(25)
                camera.flash = flashModes[currentFlashModeIndex] //refresh flash mode after switching camera
            }.start()
        }
    }
}