package sushi.hardcore.droidfs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.Facing
import com.otaliastudios.cameraview.controls.Flash
import com.otaliastudios.cameraview.controls.Grid
import com.otaliastudios.cameraview.controls.Hdr
import kotlinx.android.synthetic.main.activity_camera.*
import sushi.hardcore.droidfs.provider.RestrictedFileProvider
import sushi.hardcore.droidfs.util.GocryptfsVolume
import sushi.hardcore.droidfs.util.MiscUtils
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.*

class CameraActivity : BaseActivity(), SensorOrientationListener.Listener {
    companion object {
        private val flashModes = listOf(Flash.AUTO, Flash.ON, Flash.OFF)
        private val gridTitles = listOf(R.string.grid_none, R.string.grid_3x3, R.string.grid_4x4)
        private val gridValues = listOf(Grid.OFF, Grid.DRAW_3X3, Grid.DRAW_4X4)
        private const val fileNameRandomMin = 100000
        private const val fileNameRandomMax = 999999
        private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss")
        private val random = Random()
    }
    private var currentFlashModeIndex = 0
    private var timerDuration = 0
        set(value) {
            field = value
            if (value > 0){
                image_timer.setImageResource(R.drawable.icon_timer_on)
            } else {
                image_timer.setImageResource(R.drawable.icon_timer_off)
            }
        }
    private lateinit var sensorOrientationListener: SensorOrientationListener
    private var previousOrientation: Float = 0f
    private lateinit var orientedIcons: List<ImageView>
    private lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var outputDirectory: String
    private lateinit var fileName: String
    private var isFinishingIntentionally = false
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
                if (gocryptfsVolume.importFile(inputStream, PathUtils.pathJoin(outputDirectory, fileName))){
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
        orientedIcons = listOf(image_hdr, image_timer, image_grid, image_close, image_flash, image_camera_switch)
        sensorOrientationListener = SensorOrientationListener(this)
    }

    private fun onClickTakePhoto() {
        val baseName = "IMG_"+dateFormat.format(Date())+"_"
        do {
            fileName = baseName+(random.nextInt(fileNameRandomMax-fileNameRandomMin)+fileNameRandomMin)+".jpg"
        } while (gocryptfsVolume.pathExists(fileName))
        if (timerDuration > 0){
            text_timer.visibility = View.VISIBLE
            Thread{
                for (i in timerDuration downTo 1){
                    runOnUiThread { text_timer.text = i.toString() }
                    Thread.sleep(1000)
                }
                runOnUiThread {
                    camera.takePicture()
                    text_timer.visibility = View.GONE
                }
            }.start()
        } else {
            camera.takePicture()
        }
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

    fun onClickHDR(view: View) {
        camera.hdr = when (camera.hdr){
            Hdr.ON -> {
                image_hdr.setImageResource(R.drawable.icon_hdr_off)
                Hdr.OFF
            }
            Hdr.OFF -> {
                image_hdr.setImageResource(R.drawable.icon_hdr_on)
                Hdr.ON
            }
        }
    }

    fun onClickTimer(view: View) {
        val dialogEditTextView = layoutInflater.inflate(R.layout.dialog_edit_text, null)
        val dialogEditText = dialogEditTextView.findViewById<EditText>(R.id.dialog_edit_text)
        dialogEditText.inputType = InputType.TYPE_CLASS_NUMBER
        val dialog = ColoredAlertDialogBuilder(this)
            .setView(dialogEditTextView)
            .setTitle(getString(R.string.enter_timer_duration))
            .setPositiveButton(R.string.ok) { _, _ ->
                val enteredValue = dialogEditText.text.toString()
                if (enteredValue.isEmpty()){
                    Toast.makeText(this, getString(R.string.timer_empty_error_msg), Toast.LENGTH_SHORT).show()
                } else {
                    timerDuration = enteredValue.toInt()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialogEditText.setOnEditorActionListener { _, _, _ ->
            timerDuration = dialogEditText.text.toString().toInt()
            dialog.dismiss()
            true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    fun onClickGrid(view: View) {
        ColoredAlertDialogBuilder(this)
            .setTitle(getString(R.string.choose_grid))
            .setSingleChoiceItems(gridTitles.map { getString(it) }.toTypedArray(), gridValues.indexOf(camera.grid)){ dialog, which ->
                camera.grid = gridValues[which]
                image_grid.setImageResource(if (camera.grid == Grid.OFF){ R.drawable.icon_grid_off } else { R.drawable.icon_grid_on })
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun onClickClose(view: View) {
        isFinishingIntentionally = true
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isFinishingIntentionally) {
            gocryptfsVolume.close()
            RestrictedFileProvider.wipeAll(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing){
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorOrientationListener.remove(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){ //if not asking for permission
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        sensorOrientationListener.addListener(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        isFinishingIntentionally = true
    }

    override fun onOrientationChange(newOrientation: Int) {
        val reversedOrientation = when (newOrientation){
            90 -> 270
            270 -> 90
            else -> newOrientation
        }.toFloat()
        val rotateAnimation = RotateAnimation(previousOrientation, when {
            reversedOrientation - previousOrientation > 180 -> reversedOrientation - 360
            reversedOrientation - previousOrientation < -180 -> reversedOrientation + 360
            else -> reversedOrientation
        }, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        rotateAnimation.duration = 300
        rotateAnimation.interpolator = LinearInterpolator()
        rotateAnimation.fillAfter = true
        orientedIcons.map { it.startAnimation(rotateAnimation) }
        previousOrientation = reversedOrientation
    }
}