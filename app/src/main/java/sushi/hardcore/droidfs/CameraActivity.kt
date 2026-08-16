package sushi.hardcore.droidfs

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Size
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.android.material.materialswitch.MaterialSwitch
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MuxerOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.SucklessRecorder
import androidx.camera.video.SucklessRecording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.databinding.ActivityCameraBinding
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.util.finishOnClose
import sushi.hardcore.droidfs.video_recording.AsynchronousSeekableWriter
import sushi.hardcore.droidfs.video_recording.FFmpegMuxer
import sushi.hardcore.droidfs.video_recording.SeekableWriter
import sushi.hardcore.droidfs.widgets.EditTextDialog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.Executor
import kotlin.math.pow
import kotlin.math.sqrt
import androidx.core.content.edit

@SuppressLint("RestrictedApi")
class CameraActivity : BaseActivity(), SensorOrientationListener.Listener {
    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 0
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1
        private const val fileNameRandomMin = 100000
        private const val fileNameRandomMax = 999999
        private const val PREFS_TIMER_DURATION = "camera_timer_duration"
        private const val PREFS_REPEAT = "camera_repeat"
        private const val PREFS_CAPTURE_MODE = "camera_capture_mode"
        private const val PREFS_BACK_CAMERA = "camera_back_camera"
        private const val PREFS_VIDEO_MODE = "camera_video_mode"
        private const val PREFS_ASPECT_RATIO = "camera_aspect_ratio"
        private const val PREFS_QUALITY = "camera_quality"
        private const val PREFS_RESOLUTION_WIDTH = "camera_resolution_width"
        private const val PREFS_RESOLUTION_HEIGHT = "camera_resolution_height"
        private const val PREFS_FLASH_MODE = "camera_flash_mode"
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
    private var repeat = false
    private var timerJob: Job? = null
    private lateinit var sensorOrientationListener: SensorOrientationListener
    private var currentRotation = 0
    private var previousOrientation: Float = 0f
    private lateinit var orientedIcons: List<ImageView>
    private lateinit var encryptedVolume: EncryptedVolume
    private lateinit var outputDirectory: String
    private var permissionsGranted = false
    private lateinit var executor: Executor
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var extensionsManager: ExtensionsManager
    private lateinit var cameraSelector: CameraSelector
    private val cameraPreview = Preview.Builder().build()
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<SucklessRecorder>? = null
    private var videoRecorder: SucklessRecorder? = null
    private var videoRecording: SucklessRecording? = null
    private var camera: Camera? = null
    private var resolutions: List<Size>? = null
    private var currentResolutionIndex: Int = 0
    private var currentResolution: Size? = null
    private val aspectRatios = arrayOf(AspectRatio.RATIO_16_9, AspectRatio.RATIO_4_3)
    private var currentAspectRatioIndex = 0
    private var qualities: List<Quality>? = null
    private var preferredQuality: Quality? = null
    private var photoFlashMode = ImageCapture.FLASH_MODE_AUTO
    private var captureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
    private var isBackCamera = true
    private var isInVideoMode = false
    private var isRecording = false
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val originalTopMargin = (binding.topControls.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.topControls) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = originalTopMargin + insets.top
            }
            windowInsets
        }
        val originalBottomMargin = (binding.bottomControls.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomControls) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = originalBottomMargin + insets.bottom
            }
            windowInsets
        }

        encryptedVolume = (application as VolumeManagerApp).volumeManager.getVolume(
            intent.getIntExtra("volumeId", -1)
        )!!
        finishOnClose(encryptedVolume)
        outputDirectory = intent.getStringExtra("path")!!

        loadSettings()
        applySettingsUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                permissionsGranted = true
            } else {
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
            if (isInVideoMode) {
                qualities?.let { qualities ->
                    val qualityNames = qualities.map { qualityName(it) }.toTypedArray()
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.choose_quality)
                        .setSingleChoiceItems(qualityNames, qualities.indexOf(preferredQuality)) { dialog, which ->
                            preferredQuality = qualities[which]
                            rebindUseCases()
                            saveSettings()
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.camera_optimization)
                    .setSingleChoiceItems(
                        arrayOf(getString(R.string.maximize_quality), getString(R.string.minimize_latency)),
                        if (captureMode == ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY) 0 else 1
                    ) { dialog, which ->
                        val newCaptureMode = if (which == 0) {
                            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        } else {
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        }
                        if (newCaptureMode != captureMode) {
                            captureMode = newCaptureMode
                            setCaptureModeIcon()
                            rebindUseCases()
                            saveSettings()
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
        binding.imageRatio.setOnClickListener {
            if (isInVideoMode) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.aspect_ratio)
                    .setSingleChoiceItems(arrayOf("16:9", "4:3"), currentAspectRatioIndex) { dialog, which ->
                        currentAspectRatioIndex = which
                        rebindUseCases()
                        saveSettings()
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                resolutions?.let {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.choose_resolution)
                        .setSingleChoiceItems(it.map { size -> size.toString() }.toTypedArray(), currentResolutionIndex) { dialog, which ->
                            currentResolution = resolutions!![which]
                            currentResolutionIndex = which
                            rebindUseCases()
                            saveSettings()
                            dialog.dismiss()
                        }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                }
            }
        }
        binding.imageTimer.setOnClickListener {
            val dialog = EditTextDialog(this, R.string.enter_timer_duration, R.layout.dialog_camera_timer)
            dialog.dialogEditText.inputType = InputType.TYPE_CLASS_NUMBER
            if (timerDuration != 0) {
                dialog.setSelectedText(timerDuration.toString())
            }
            val switch = dialog.root.findViewById<MaterialSwitch>(R.id.switch_repeat)
            switch.isVisible = !isInVideoMode
            switch.isChecked = repeat
            dialog.onSubmit {
                try {
                    timerDuration = it.toInt()
                    repeat = switch.isChecked
                    saveSettings()
                } catch (_: NumberFormatException) {
                    Toast.makeText(this, R.string.invalid_number, Toast.LENGTH_SHORT).show()
                }
            }
            dialog.show()
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
                        photoFlashMode = ImageCapture.FLASH_MODE_ON
                        imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
                        R.drawable.icon_flash_on
                    }
                    ImageCapture.FLASH_MODE_ON -> {
                        photoFlashMode = ImageCapture.FLASH_MODE_OFF
                        imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
                        R.drawable.icon_flash_off
                    }
                    else -> {
                        photoFlashMode = ImageCapture.FLASH_MODE_AUTO
                        imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
                        R.drawable.icon_flash_auto
                    }
                }
            })
            saveSettings()
        }
        binding.imageModeSwitch.setOnClickListener {
            cancelTimer()
            isInVideoMode = !isInVideoMode
            if (isInVideoMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST_CODE)
                    }
                }
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            } else {
                imageCapture?.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            applySettingsUI()
            rebindUseCases()
            saveSettings()
        }
        binding.imageCameraSwitch.setOnClickListener {
            cancelTimer()
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
            qualities = null
            setupCamera()
            saveSettings()
        }
        binding.takePhotoButton.onClick = ::onClickTakePhoto
        binding.recordVideoButton.onClick = ::onClickRecordVideo
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
        if (grantResults.size == 1) {
            when (requestCode) {
                CAMERA_PERMISSION_REQUEST_CODE -> if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsGranted = true
                    setupCamera()
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.camera_perm_needed)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }.show()
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

    private fun setCaptureModeIcon() {
        binding.imageCaptureMode.setImageResource(if (isInVideoMode) {
           R.drawable.icon_high_quality
        } else {
             if (captureMode == ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY) {
                 R.drawable.icon_speed
             } else {
                 R.drawable.icon_high_quality
             }
        })
    }

    private fun applySettingsUI() {
        if (isInVideoMode) {
            binding.takePhotoButton.visibility = View.GONE
            binding.recordVideoButton.visibility = View.VISIBLE
            binding.imageModeSwitch.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.icon_photo)?.mutate()?.also {
                it.setTint(ContextCompat.getColor(this, R.color.neutralIconTint))
            })
            binding.imageFlash.setImageResource(R.drawable.icon_flash_off)
        } else {
            binding.takePhotoButton.visibility = View.VISIBLE
            binding.recordVideoButton.visibility = View.GONE
            binding.imageModeSwitch.setImageResource(R.drawable.icon_video)
            binding.imageFlash.setImageResource(when (photoFlashMode) {
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.icon_flash_auto
                ImageCapture.FLASH_MODE_ON -> R.drawable.icon_flash_on
                else -> R.drawable.icon_flash_off
            })
        }
        binding.imageCameraSwitch.setImageResource(if (isBackCamera) R.drawable.icon_camera_front else R.drawable.icon_camera_back)
        setCaptureModeIcon()
    }

    private fun loadSettings() {
        timerDuration = sharedPrefs.getInt(PREFS_TIMER_DURATION, 0)
        repeat = sharedPrefs.getBoolean(PREFS_REPEAT, false)
        captureMode = sharedPrefs.getInt(PREFS_CAPTURE_MODE, ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        isBackCamera = sharedPrefs.getBoolean(PREFS_BACK_CAMERA, true)
        isInVideoMode = sharedPrefs.getBoolean(PREFS_VIDEO_MODE, false)
        currentAspectRatioIndex = sharedPrefs.getInt(PREFS_ASPECT_RATIO, 0)
        preferredQuality = sharedPrefs.getString(PREFS_QUALITY, null)?.let { qualityFromName(it) }
        val width = sharedPrefs.getInt(PREFS_RESOLUTION_WIDTH, 0)
        val height = sharedPrefs.getInt(PREFS_RESOLUTION_HEIGHT, 0)
        if (width > 0 && height > 0) {
            currentResolution = Size(width, height)
        }
        photoFlashMode = sharedPrefs.getInt(PREFS_FLASH_MODE, ImageCapture.FLASH_MODE_AUTO)
    }

    private fun saveSettings() {
        sharedPrefs.edit {
            putInt(PREFS_TIMER_DURATION, timerDuration)
                .putBoolean(PREFS_REPEAT, repeat)
                .putInt(PREFS_CAPTURE_MODE, captureMode)
                .putBoolean(PREFS_BACK_CAMERA, isBackCamera)
                .putBoolean(PREFS_VIDEO_MODE, isInVideoMode)
                .putInt(PREFS_ASPECT_RATIO, currentAspectRatioIndex)
                .putString(PREFS_QUALITY, preferredQuality?.let { qualityName(it) })
                .putInt(PREFS_RESOLUTION_WIDTH, currentResolution?.width ?: 0)
                .putInt(PREFS_RESOLUTION_HEIGHT, currentResolution?.height ?: 0)
                .putInt(PREFS_FLASH_MODE, photoFlashMode)
        }
    }

    private fun qualityName(quality: Quality): String {
        return when (quality) {
            Quality.UHD -> "UHD"
            Quality.FHD -> "FHD"
            Quality.HD -> "HD"
            Quality.SD -> "SD"
            else -> throw IllegalArgumentException("Invalid quality: $quality")
        }
    }

    private fun qualityFromName(name: String): Quality? {
        return when (name) {
            "UHD" -> Quality.UHD
            "FHD" -> Quality.FHD
            "HD" -> Quality.HD
            "SD" -> Quality.SD
            else -> null
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
            .setFlashMode(photoFlashMode)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionFilter { supportedSizes, _ ->
                resolutions = supportedSizes.sortedBy {
                    -it.width*it.height
                }
                currentResolution?.let { targetResolution ->
                    return@setResolutionFilter supportedSizes.sortedBy {
                        sqrt((it.width - targetResolution.width).toDouble().pow(2) + (it.height - targetResolution.height).toDouble().pow(2))
                    }.also { currentResolutionIndex = resolutions!!.indexOf(it[0]) }
                }
                supportedSizes
            }.build())
            .setTargetRotation(currentRotation)
            .build()
    }

    private fun refreshVideoCapture() {
        val recorderBuilder = SucklessRecorder.Builder()
            .setExecutor(executor)
            .setAspectRatio(aspectRatios[currentAspectRatioIndex])
        preferredQuality?.let { recorderBuilder.setQualitySelector(QualitySelector.from(it)) }
        videoRecorder = recorderBuilder.build()
        videoCapture = VideoCapture.withOutput(videoRecorder!!).apply {
            targetRotation = currentRotation
        }
    }

    private fun rebindUseCases(): UseCase {
        cameraProvider.unbindAll()
        val currentUseCase = (if (isInVideoMode) {
            refreshVideoCapture()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, videoCapture)
            if (qualities == null) {
                qualities = SucklessRecorder.getVideoCapabilities(camera!!.cameraInfo).getSupportedQualities(DynamicRange.UNSPECIFIED)
            }
            videoCapture
        } else {
            refreshImageCapture()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageCapture)
            imageCapture
        })!!
        adaptPreviewSize(currentUseCase.attachedSurfaceResolution!!.swap())
        return currentUseCase
    }

    private fun setupCamera() {
        if (permissionsGranted && ::extensionsManager.isInitialized && ::cameraProvider.isInitialized) {
            cameraSelector = if (isBackCamera){ CameraSelector.DEFAULT_BACK_CAMERA } else { CameraSelector.DEFAULT_FRONT_CAMERA }
            if (extensionsManager.isExtensionAvailable(cameraSelector, ExtensionMode.AUTO)) {
                cameraSelector = extensionsManager.getExtensionEnabledCameraSelector(cameraSelector, ExtensionMode.AUTO)
            }
            rebindUseCases()
        }
    }

    private fun getOutputPath(isVideo: Boolean): String {
        val baseName = if (isVideo) {"VID"} else {"IMG"}+'_'+dateFormat.format(Date())+'_'
        var outputPath: String
        do {
            val fileName = baseName+(random.nextInt(fileNameRandomMax-fileNameRandomMin)+fileNameRandomMin)+'.'+ if (isVideo) {"mp4"} else {"jpg"}
            outputPath = PathUtils.pathJoin(outputDirectory, fileName)
        } while (encryptedVolume.pathExists(outputPath))
        return outputPath
    }

    private fun cancelTimer() {
        if (timerJob?.isActive == true) {
            timerJob?.cancel()
        }
        binding.textTimer.visibility = View.GONE
        binding.takePhotoButton.release()
        binding.recordVideoButton.release()
    }

    private fun startTimerThen(action: () -> Unit) {
        if (timerDuration > 0){
            binding.textTimer.visibility = View.VISIBLE
            timerJob = lifecycleScope.launch {
                for (i in timerDuration downTo 1){
                    binding.textTimer.text = i.toString()
                    delay(1000)
                }
                if (!isFinishing) {
                    action()
                    binding.textTimer.visibility = View.GONE
                }
            }
        } else {
            action()
        }
    }

    private fun takePhoto() {
        imageCapture?.let { imageCapture ->
            val outputPath = getOutputPath(false)
            val outputBuff = ByteArrayOutputStream()
            val outputOptions = ImageCapture.OutputFileOptions.Builder(outputBuff).build()
            imageCapture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (encryptedVolume.importFile(ByteArrayInputStream(outputBuff.toByteArray()), outputPath)) {
                            Toast.makeText(applicationContext, getString(R.string.picture_save_success, outputPath), Toast.LENGTH_SHORT).show()
                            if (repeat) {
                                startTimerThen(::takePhoto)
                            } else {
                                binding.takePhotoButton.release()
                            }
                        } else {
                            MaterialAlertDialogBuilder(this@CameraActivity)
                                .setTitle(R.string.error)
                                .setMessage(R.string.picture_save_failed)
                                .setPositiveButton(R.string.ok, null)
                                .show()
                            binding.takePhotoButton.release()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        binding.takePhotoButton.release()
                        Toast.makeText(applicationContext, exception.message, Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun onClickTakePhoto() {
        if (timerJob?.isActive == true) {
            cancelTimer()
        } else {
            startTimerThen(::takePhoto)
        }
    }

    @SuppressLint("MissingPermission")
    private fun onClickRecordVideo() {
        if (isRecording) {
            videoRecording?.stop()
        } else if (timerJob?.isActive == true) {
            cancelTimer()
        } else {
            startTimerThen {
                val path = getOutputPath(true)
                val fileHandle = encryptedVolume.openFileWriteMode(path)
                if (fileHandle == -1L) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.error)
                        .setMessage(R.string.file_creation_failed)
                        .setPositiveButton(R.string.ok, null)
                        .show()
                    return@startTimerThen
                }
                val writer = AsynchronousSeekableWriter(object : SeekableWriter {
                    private var offset = 0L

                    override fun close() {
                        encryptedVolume.closeFile(fileHandle)
                    }

                    override fun seek(offset: Long) {
                        this.offset = offset
                    }

                    override fun write(buffer: ByteArray, size: Int) {
                        offset += encryptedVolume.write(fileHandle, offset, buffer, 0, size.toLong())
                    }
                })
                val pendingRecording = videoRecorder!!.prepareRecording(
                    this,
                    MuxerOutputOptions(FFmpegMuxer(writer))
                ).also {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        it.withAudioEnabled()
                    }
                }
                writer.start()
                videoRecording = pendingRecording.start(executor) {
                    val buttons = arrayOf(binding.imageCaptureMode, binding.imageRatio, binding.imageTimer, binding.imageModeSwitch, binding.imageCameraSwitch)
                    when (it) {
                        is VideoRecordEvent.Start -> {
                            binding.recordVideoButton.release()
                            binding.recordVideoButton.setImageResource(R.drawable.stop_recording_video_button)
                            for (i in buttons) {
                                i.isEnabled = false
                                i.alpha = 0.5F
                            }
                            isRecording = true
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (it.hasError()) {
                                it.cause?.printStackTrace()
                                Toast.makeText(applicationContext, it.cause?.message ?: ("Error: " + it.error), Toast.LENGTH_SHORT).show()
                                videoRecording?.close()
                                videoRecording = null
                            } else {
                                Toast.makeText(applicationContext, getString(R.string.video_save_success, path), Toast.LENGTH_SHORT).show()
                            }
                            binding.recordVideoButton.release()
                            binding.recordVideoButton.setImageResource(R.drawable.record_video_button)
                            for (i in buttons) {
                                i.isEnabled = true
                                i.alpha = 1F
                            }
                            isRecording = false
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorOrientationListener.remove(this)
    }

    override fun onResume() {
        super.onResume()
        sensorOrientationListener.addListener(this)
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
        videoCapture?.targetRotation = newOrientation
        currentRotation = newOrientation
    }
}

private fun Size.swap(): Size {
    return Size(height, width)
}