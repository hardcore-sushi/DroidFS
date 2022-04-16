package sushi.hardcore.droidfs

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Size
import android.view.*
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.*
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import sushi.hardcore.droidfs.content_providers.RestrictedFileProvider
import sushi.hardcore.droidfs.databinding.ActivityCameraBinding
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.video_recording.SeekableWriter
import sushi.hardcore.droidfs.video_recording.VideoCapture
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.EditTextDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor

class CameraActivity : BaseActivity(), SensorOrientationListener.Listener {
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 0
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
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
    private var isFinishingIntentionally = false
    private var isAskingPermissions = false
    private var permissionsGranted = false
    private lateinit var executor: Executor
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager
    private lateinit var cameraSelector: CameraSelector
    private val cameraPreview = Preview.Builder().build()
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var camera: Camera? = null
    private var resolutions: List<Size>? = null
    private var currentResolutionIndex: Int = 0
    private var currentResolution: Size? = null
    private var captureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
    private var isBackCamera = true
    private var isInVideoMode = false
    private var isRecording = false
    private var isWaitingForTimer = false
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usf_keep_open = sharedPrefs.getBoolean("usf_keep_open", false)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()
        gocryptfsVolume = GocryptfsVolume(applicationContext, intent.getIntExtra("sessionID", -1))
        outputDirectory = intent.getStringExtra("path")!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                permissionsGranted = true
            } else {
                isAskingPermissions = true
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
            }
        } else {
            permissionsGranted = true
        }

        executor = ContextCompat.getMainExecutor(this)
        cameraPreview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
        ProcessCameraProvider.getInstance(this).apply {
            addListener({
                cameraProvider = get()
                ExtensionsManager.getInstanceAsync(this@CameraActivity, cameraProvider).apply {
                    addListener({
                        extensionsManager = get()
                        setupCamera()
                    }, executor)
                }
            }, executor)
        }

        binding.imageCaptureMode.setOnClickListener {
            val currentIndex = if (captureMode == ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) {
                0
            } else {
                1
            }
            CustomAlertDialogBuilder(this, themeValue)
                .setTitle(R.string.camera_optimization)
                .setSingleChoiceItems(arrayOf(getString(R.string.maximize_quality), getString(R.string.minimize_latency)), currentIndex) { dialog, which ->
                    val resId: Int
                    val newCaptureMode = if (which == 0) {
                        resId = R.drawable.icon_high_quality
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    } else {
                        resId = R.drawable.icon_speed
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    }
                    if (newCaptureMode != captureMode) {
                        captureMode = newCaptureMode
                        binding.imageCaptureMode.setImageResource(resId)
                        if (!isInVideoMode) {
                            cameraProvider.unbind(imageCapture)
                            refreshImageCapture()
                            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
                        }
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        binding.imageRatio.setOnClickListener {
            resolutions?.let {
                CustomAlertDialogBuilder(this, themeValue)
                    .setTitle(R.string.choose_resolution)
                    .setSingleChoiceItems(it.map { size -> size.toString() }.toTypedArray(), currentResolutionIndex) { dialog, which ->
                        currentResolution = resolutions!![which]
                        currentResolutionIndex = which
                        setupCamera()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        binding.imageTimer.setOnClickListener {
            with (EditTextDialog(this, R.string.enter_timer_duration) {
                try {
                    timerDuration = it.toInt()
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show()
                }
            }) {
                binding.dialogEditText.inputType = InputType.TYPE_CLASS_NUMBER
                show()
            }
        }
        binding.imageFlash.setOnClickListener {
            binding.imageFlash.setImageResource(if (isInVideoMode) {
                when (imageCapture?.flashMode) {
                    ImageCapture.FLASH_MODE_ON -> {
                        camera?.cameraControl?.enableTorch(false)
                        imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                        R.drawable.icon_flash_off
                    }
                    else -> {
                        camera?.cameraControl?.enableTorch(true)
                        imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                        R.drawable.icon_flash_on
                    }
                }
            } else {
                when (imageCapture?.flashMode) {
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
                }
            })
        }
        binding.imageModeSwitch.setOnClickListener {
            isInVideoMode = !isInVideoMode
            setupCamera()
            binding.imageFlash.setImageResource(if (isInVideoMode) {
                binding.recordVideoButton.visibility = View.VISIBLE
                binding.takePhotoButton.visibility = View.GONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        isAskingPermissions = true
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST_CODE)
                    }
                }
                binding.imageModeSwitch.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.icon_photo)?.also {
                    it.setTint(ContextCompat.getColor(this, R.color.neutralIconTint))
                })
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                R.drawable.icon_flash_off
            } else {
                binding.recordVideoButton.visibility = View.GONE
                binding.takePhotoButton.visibility = View.VISIBLE
                binding.imageModeSwitch.setImageResource(R.drawable.icon_video)
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                R.drawable.icon_flash_auto
            })
        }
        binding.imageCameraSwitch.setOnClickListener {
            isBackCamera = if (isBackCamera) {
                binding.imageCameraSwitch.setImageResource(R.drawable.icon_camera_back)
                false
            } else {
                binding.imageCameraSwitch.setImageResource(R.drawable.icon_camera_front)
                if (isInVideoMode) {
                    //reset flash state
                    imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                    binding.imageFlash.setImageResource(R.drawable.icon_flash_off)
                }
                true
            }
            resolutions = null
            setupCamera()
        }
        binding.takePhotoButton.onClick = ::onClickTakePhoto
        binding.recordVideoButton.setOnClickListener { onClickRecordVideo() }
        orientedIcons = listOf(binding.imageRatio, binding.imageTimer, binding.imageCaptureMode, binding.imageFlash, binding.imageModeSwitch, binding.imageCameraSwitch)
        sensorOrientationListener = SensorOrientationListener(this)

        val scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener(){
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 0F
                camera?.cameraControl?.setZoomRatio(currentZoomRatio*detector.scaleFactor)
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
                    camera?.cameraControl?.startFocusAndMetering(action)
                    true
                }
                MotionEvent.ACTION_MOVE -> scaleGestureDetector.onTouchEvent(event)
                else -> false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        isAskingPermissions = false
        if (grantResults.size == 1) {
            when (requestCode) {
                CAMERA_PERMISSION_REQUEST_CODE -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsGranted = true
                    setupCamera()
                } else {
                    CustomAlertDialogBuilder(this, themeValue)
                        .setTitle(R.string.error)
                        .setMessage(R.string.camera_perm_needed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            isFinishingIntentionally = true
                            finish()
                        }.show()
                }
                AUDIO_PERMISSION_REQUEST_CODE -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (videoCapture != null) {
                        cameraProvider.unbind(videoCapture)
                        camera = cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)
                    }
                }
            }
        }
    }

    private fun adaptPreviewSize(resolution: Size) {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        var height = (resolution.height * (screenWidth.toFloat() / resolution.width)).toInt()
        var width = screenWidth
        if (height > screenHeight) {
            width = (width * (screenHeight.toFloat() / height)).toInt()
            height = screenHeight
        }
        binding.cameraPreview.layoutParams = RelativeLayout.LayoutParams(width, height).apply {
            addRule(RelativeLayout.CENTER_IN_PARENT)
        }
    }

    private fun refreshImageCapture() {
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(captureMode)
            .setFlashMode(imageCapture?.flashMode ?: ImageCapture.FLASH_MODE_AUTO)
            .apply {
                currentResolution?.let {
                    setTargetResolution(it)
                }
            }
            .build()
    }

    private fun refreshVideoCapture() {
        videoCapture = VideoCapture.Builder().apply {
            currentResolution?.let {
                setTargetResolution(it)
            }
        }.build()
    }

    @SuppressLint("RestrictedApi")
    private fun setupCamera() {
        if (permissionsGranted && ::extensionsManager.isInitialized && ::cameraProvider.isInitialized) {
            cameraSelector = if (isBackCamera){ CameraSelector.DEFAULT_BACK_CAMERA } else { CameraSelector.DEFAULT_FRONT_CAMERA }
            if (extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.AUTO)) {
                cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, ExtensionMode.AUTO)
            }

            cameraProvider.unbindAll()

            val currentUseCase = (if (isInVideoMode) {
                refreshVideoCapture()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, videoCapture)
                videoCapture
            } else {
                refreshImageCapture()
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageCapture)
                imageCapture
            })!!

            adaptPreviewSize(currentResolution ?: currentUseCase.attachedSurfaceResolution!!.swap())

            if (resolutions == null) {
                val info = Camera2CameraInfo.from(camera!!.cameraInfo)
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val characteristics = cameraManager.getCameraCharacteristics(info.cameraId)
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.let { streamConfigurationMap ->
                    resolutions = streamConfigurationMap.getOutputSizes(currentUseCase.imageFormat).map { it.swap() }
                }
            }
        }
    }

    private fun getOutputPath(isVideo: Boolean): String {
        val baseName = if (isVideo) {"VID"} else {"IMG"}+'_'+dateFormat.format(Date())+'_'
        var fileName: String
        do {
            fileName = baseName+(random.nextInt(fileNameRandomMax-fileNameRandomMin)+fileNameRandomMin)+'.'+ if (isVideo) {"mp4"} else {"jpg"}
        } while (gocryptfsVolume.pathExists(fileName))
        return PathUtils.pathJoin(outputDirectory, fileName)
    }

    private fun startTimerThen(action: () -> Unit) {
        if (timerDuration > 0){
            binding.textTimer.visibility = View.VISIBLE
            isWaitingForTimer = true
            Thread{
                for (i in timerDuration downTo 1){
                    runOnUiThread { binding.textTimer.text = i.toString() }
                    Thread.sleep(1000)
                }
                if (!isFinishing) {
                    runOnUiThread {
                        action()
                        binding.textTimer.visibility = View.GONE
                    }
                }
                isWaitingForTimer = false
            }.start()
        } else {
            action()
        }
    }

    private fun onClickTakePhoto() {
        if (!isWaitingForTimer) {
            val outputPath = getOutputPath(false)
            startTimerThen {
                imageCapture?.let { imageCapture ->
                    val outputBuff = ByteArrayOutputStream()
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputBuff).build()
                    imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            binding.takePhotoButton.onPhotoTaken()
                            if (gocryptfsVolume.importFile(ByteArrayInputStream(outputBuff.toByteArray()), outputPath)) {
                                Toast.makeText(applicationContext, getString(R.string.picture_save_success, outputPath), Toast.LENGTH_SHORT).show()
                            } else {
                                CustomAlertDialogBuilder(this@CameraActivity, themeValue)
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
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun onClickRecordVideo() {
        if (isRecording) {
            videoCapture?.stopRecording()
            isRecording = false
        } else if (!isWaitingForTimer) {
            val path = getOutputPath(true)
            startTimerThen {
                val handleId = gocryptfsVolume.openWriteMode(path)
                videoCapture?.startRecording(VideoCapture.OutputFileOptions(object : SeekableWriter {
                    var offset = 0L
                    override fun write(byteArray: ByteArray) {
                        offset += gocryptfsVolume.writeFile(handleId, offset, byteArray, byteArray.size)
                    }
                    override fun seek(offset: Long) {
                        this.offset = offset
                    }
                    override fun close() {
                        gocryptfsVolume.closeFile(handleId)
                    }
                }), executor, object : VideoCapture.OnVideoSavedCallback {
                    override fun onVideoSaved() {
                        Toast.makeText(applicationContext, getString(R.string.video_save_success, path), Toast.LENGTH_SHORT).show()
                        binding.recordVideoButton.setImageResource(R.drawable.record_video_button)
                    }
                    override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                        cause?.printStackTrace()
                        binding.recordVideoButton.setImageResource(R.drawable.record_video_button)
                    }
                })
                binding.recordVideoButton.setImageResource(R.drawable.stop_recording_video_button)
                isRecording = true
            }
        }
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
        if (!isFinishing && !usf_keep_open){
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        sensorOrientationListener.remove(this)
        if (!isAskingPermissions && !usf_keep_open) {
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
        val realOrientation = when (newOrientation) {
            Surface.ROTATION_0 -> 0f
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            else -> 270f
        }
        val rotateAnimation = RotateAnimation(previousOrientation, when {
            realOrientation - previousOrientation > 180 -> realOrientation - 360
            realOrientation - previousOrientation < -180 -> realOrientation + 360
            else -> realOrientation
        }, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
        rotateAnimation.duration = 300
        rotateAnimation.interpolator = LinearInterpolator()
        rotateAnimation.fillAfter = true
        orientedIcons.map { it.startAnimation(rotateAnimation) }
        previousOrientation = realOrientation
        imageCapture?.targetRotation = newOrientation
        videoCapture?.setTargetRotation(newOrientation)
    }
}

private fun Size.swap(): Size {
    return Size(height, width)
}