package sushi.hardcore.droidfs

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.DisplayMetrics
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.extensions.HdrImageCaptureExtender
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.adapters.DialogSingleChoiceAdapter
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.databinding.ActivityCameraBinding
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : BaseActivity(), SensorOrientationListener.Listener {
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
        private const val fileNameRandomMin = 100000
        private const val fileNameRandomMax = 999999
        private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        private val random = Random()
    }

    private var timerDuration = 0
        set(value) {
            field = value
            if (value > 0){
                binding.imageTimer.setImageResource(R.drawable.icon_timer_on)
            } else {
                binding.imageTimer.setImageResource(R.drawable.icon_timer_off)
            }
        }
    private var usf_keep_open = false
    private lateinit var sensorOrientationListener: SensorOrientationListener
    private var previousOrientation: Float = 0f
    private lateinit var orientedIcons: List<ImageView>
    private lateinit var gocryptfsVolume: GocryptfsVolume
    private lateinit var outputDirectory: String
    private lateinit var fileName: String
    private var isFinishingIntentionally = false
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var resolutions: Array<Size>? = null
    private var currentResolutionIndex: Int = 0
    private var isBackCamera = true
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usf_keep_open = sharedPrefs.getBoolean("usf_keep_open", false)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        gocryptfsVolume = GocryptfsVolume(intent.getIntExtra("sessionID", -1))
        outputDirectory = intent.getStringExtra("path")!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                setupCamera()
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            }
        } else {
            setupCamera()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.imageRatio.setOnClickListener {
            resolutions?.let {
                ColoredAlertDialogBuilder(this)
                    .setTitle(R.string.choose_resolution)
                    .setSingleChoiceItems(DialogSingleChoiceAdapter(this, it.map { size -> size.toString() }.toTypedArray()), currentResolutionIndex) { dialog, which ->
                        setupCamera(resolutions!![which])
                        dialog.dismiss()
                        currentResolutionIndex = which
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        binding.imageTimer.setOnClickListener {
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
        binding.imageClose.setOnClickListener {
            isFinishingIntentionally = true
            finish()
        }
        binding.imageFlash.setOnClickListener {
            binding.imageFlash.setImageResource(when (imageCapture?.flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                    R.drawable.icon_flash_on
                }
                ImageCapture.FLASH_MODE_ON -> {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    R.drawable.icon_flash_off
                }
                else -> {
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                    R.drawable.icon_flash_auto
                }
            })
        }
        binding.imageCameraSwitch.setOnClickListener {
            isBackCamera = if (isBackCamera) {
                binding.imageCameraSwitch.setImageResource(R.drawable.icon_camera_front)
                false
            } else {
                binding.imageCameraSwitch.setImageResource(R.drawable.icon_camera_back)
                true
            }
            setupCamera()
        }
        binding.takePhotoButton.onClick = ::onClickTakePhoto
        orientedIcons = listOf(binding.imageRatio, binding.imageTimer, binding.imageClose, binding.imageFlash, binding.imageCameraSwitch)
        sensorOrientationListener = SensorOrientationListener(this)

        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = imageCapture?.camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0F
                imageCapture?.camera?.cameraControl?.setZoomRatio(currentZoomRatio*detector.scaleFactor)
                return true
            }
        })
        binding.cameraPreview.setOnTouchListener { view, event ->
            view.performClick()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_UP -> {
                    val factory = binding.cameraPreview.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    imageCapture?.camera?.cameraControl?.startFocusAndMetering(action)
                    true
                }
                MotionEvent.ACTION_MOVE -> scaleGestureDetector.onTouchEvent(event)
                else -> false
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> if (grantResults.size == 1) {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    ColoredAlertDialogBuilder(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.camera_perm_needed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            isFinishingIntentionally = true
                            finish()
                        }.show()
                } else {
                    setupCamera()
                }
            }
        }
    }

    private fun adaptPreviewSize(resolution: Size) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        //resolution.width and resolution.height seem to be inverted
        val width = resolution.height
        val height = resolution.width
        binding.cameraPreview.layoutParams = if (metrics.widthPixels < width){
            RelativeLayout.LayoutParams(
                metrics.widthPixels,
                (height * (metrics.widthPixels.toFloat() / width)).toInt()
            )
        } else {
            RelativeLayout.LayoutParams(width, height)
        }
        (binding.cameraPreview.layoutParams as RelativeLayout.LayoutParams).addRule(RelativeLayout.CENTER_IN_PARENT)
    }

    private fun setupCamera(resolution: Size? = null){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
            val builder = ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
            resolution?.let {
                builder.setTargetResolution(it)
            }
            val hdrImageCapture = HdrImageCaptureExtender.create(builder)
            val cameraSelector = if (isBackCamera){ CameraSelector.DEFAULT_BACK_CAMERA } else { CameraSelector.DEFAULT_FRONT_CAMERA }

            if (hdrImageCapture.isExtensionAvailable(cameraSelector)){
                hdrImageCapture.enableExtension(cameraSelector)
            }

            imageCapture = builder.build()

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            adaptPreviewSize(imageCapture!!.attachedSurfaceResolution!!)

            val info = Camera2CameraInfo.from(camera.cameraInfo)
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val characteristics = cameraManager.getCameraCharacteristics(info.cameraId)
            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let { streamConfigurationMap ->
                resolutions = streamConfigurationMap.getOutputSizes(imageCapture!!.imageFormat)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val outputBuff = ByteArrayOutputStream()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputBuff).build()
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                binding.takePhotoButton.onPhotoTaken()
                if (gocryptfsVolume.importFile(ByteArrayInputStream(outputBuff.toByteArray()), PathUtils.pathJoin(outputDirectory, fileName))){
                    Toast.makeText(applicationContext, getString(R.string.picture_save_success, fileName), Toast.LENGTH_SHORT).show()
                } else {
                    ColoredAlertDialogBuilder(this@CameraActivity)
                        .setTitle(R.string.error)
                        .setMessage(R.string.picture_save_failed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            isFinishingIntentionally = true
                            finish()
                        }
                        .show()
                }
            }
            override fun onError(exception: ImageCaptureException) {
                binding.takePhotoButton.onPhotoTaken()
                Toast.makeText(applicationContext, exception.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun onClickTakePhoto() {
        val baseName = "IMG_"+dateFormat.format(Date())+"_"
        do {
            fileName = baseName+(random.nextInt(fileNameRandomMax-fileNameRandomMin)+fileNameRandomMin)+".jpg"
        } while (gocryptfsVolume.pathExists(fileName))
        if (timerDuration > 0){
            binding.textTimer.visibility = View.VISIBLE
            Thread{
                for (i in timerDuration downTo 1){
                    runOnUiThread { binding.textTimer.text = i.toString() }
                    Thread.sleep(1000)
                }
                runOnUiThread {
                    takePhoto()
                    binding.textTimer.visibility = View.GONE
                }
            }.start()
        } else {
            takePhoto()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (!isFinishingIntentionally) {
            gocryptfsVolume.close()
            RestrictedFileProvider.wipeAll(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing && !usf_keep_open){
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorOrientationListener.remove(this)
        if (
                (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) //not asking for permission
                && !usf_keep_open
        ){
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