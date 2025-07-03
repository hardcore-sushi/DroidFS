package sushi.hardcore.droidfs.file_viewers

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.target
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityImageViewerBinding
import sushi.hardcore.droidfs.filesystems.EncryptedFileReaderFileSystem
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.ZoomableImageView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs

class ImageViewer: FileViewerActivity(true) {
    companion object {
        private const val hideDelay: Long = 3000
        private const val MIN_SWIPE_DISTANCE = 150
    }

    class ImageViewModel : ViewModel() {
        var rotationAngle: Float = 0f
        var imageLoader: ImageLoader? = null
    }

    private lateinit var fileName: String
    private lateinit var handler: Handler
    private val imageViewModel: ImageViewModel by viewModels()
    private var imageRequestBuilder: ImageRequest.Builder? = null
    private var x1 = 0F
    private var x2 = 0F
    private var slideshowActive = false
    private var orientationTransformation: OrientationTransformation? = null
    private val hideUI = Runnable {
        binding.overlay.visibility = View.GONE
        hideSystemUi()
    }
    private val slideshowNext = Runnable {
        if (slideshowActive){
            binding.imageViewer.resetZoomFactor()
            swipeImage(-1F, true)
        }
    }
    private lateinit var binding: ActivityImageViewerBinding

    override fun getFileType(): String {
        return "image"
    }

    override fun viewFile() {
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.overlay.fitsSystemWindows = true
        if (imageViewModel.imageLoader == null) {
            imageViewModel.imageLoader = ImageLoader.Builder(this).diskCache(null)
                .fileSystem(EncryptedFileReaderFileSystem(encryptedVolume)).build()
        }
        handler = Handler(mainLooper)
        binding.imageViewer.setOnInteractionListener(object : ZoomableImageView.OnInteractionListener {
            override fun onSingleTap(event: MotionEvent?) {
                handler.removeCallbacks(hideUI)
                if (binding.overlay.isGone) {
                    binding.overlay.visibility = View.VISIBLE
                    showPartialSystemUi()
                    handler.postDelayed(hideUI, hideDelay)
                } else {
                    hideUI.run()
                }
            }

            override fun onTouch(event: MotionEvent?) {
                if (!binding.imageViewer.isZoomed) {
                    when (event?.action) {
                        MotionEvent.ACTION_DOWN -> {
                            x1 = event.x
                        }
                        MotionEvent.ACTION_UP -> {
                            x2 = event.x
                            val deltaX = x2 - x1
                            if (abs(deltaX) > MIN_SWIPE_DISTANCE) {
                                askSaveRotation { swipeImage(deltaX) }
                            }
                        }
                    }
                }
            }
        })
        binding.imageDelete.setOnClickListener {
            CustomAlertDialogBuilder(this, theme)
                .keepFullScreen()
                .setTitle(R.string.warning)
                .setPositiveButton(R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        if (deleteCurrentFile()) {
                            if (fileViewerViewModel.playlist.isEmpty()) { // no more image left
                                goBackToExplorer()
                            } else {
                                loadImage(true)
                            }
                        } else {
                            CustomAlertDialogBuilder(this@ImageViewer, theme)
                                .keepFullScreen()
                                .setTitle(R.string.error)
                                .setMessage(getString(R.string.remove_failed, fileName))
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        }
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .setMessage(getString(R.string.single_delete_confirm, fileName))
                .show()
        }
        binding.imageButtonSlideshow.setOnClickListener {
            if (!slideshowActive){
                slideshowActive = true
                handler.postDelayed(slideshowNext, Constants.SLIDESHOW_DELAY)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                hideUI.run()
                Toast.makeText(this, R.string.slideshow_started, Toast.LENGTH_SHORT).show()
            } else {
                stopSlideshow()
            }
        }
        binding.imagePrevious.setOnClickListener {
            askSaveRotation {
                binding.imageViewer.resetZoomFactor()
                swipeImage(1F)
            }
        }
        binding.imageNext.setOnClickListener {
            askSaveRotation {
                binding.imageViewer.resetZoomFactor()
                swipeImage(-1F)
            }
        }
        binding.imageRotateRight.setOnClickListener { onClickRotate(90f) }
        binding.imageRotateLeft.setOnClickListener { onClickRotate(-90f) }
        onBackPressedDispatcher.addCallback(this) {
            if (slideshowActive) {
                stopSlideshow()
            } else {
                askSaveRotation {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        loadImage(false)
        handler.postDelayed(hideUI, hideDelay)
    }

    private fun loadImage(newImage: Boolean) {
        fileName = File(fileViewerViewModel.filePath!!).name
        binding.textFilename.text = fileName
        if (newImage) {
            imageViewModel.rotationAngle = 0f
        }
        imageRequestBuilder = ImageRequest.Builder(this).data(fileViewerViewModel.filePath).target(binding.imageViewer)
        if (imageViewModel.rotationAngle.mod(360f) != 0f) {
            rotateImage()
        } else {
            imageViewModel.imageLoader!!.enqueue(imageRequestBuilder!!.build())
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        handler.removeCallbacks(hideUI)
        handler.postDelayed(hideUI, hideDelay)
    }

    private fun onClickRotate(angle: Float) {
        imageViewModel.rotationAngle += angle
        binding.imageViewer.restoreZoomNormal()
        rotateImage()
    }

    private fun swipeImage(deltaX: Float, slideshowSwipe: Boolean = false) {
        lifecycleScope.launch {
            playlistNext(deltaX < 0)
            loadImage(true)
            if (slideshowActive) {
                if (!slideshowSwipe) { // reset slideshow delay if user swipes
                    handler.removeCallbacks(slideshowNext)
                }
                handler.postDelayed(slideshowNext, Constants.SLIDESHOW_DELAY)
            }
        }
    }

    private fun stopSlideshow(){
        slideshowActive = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(this, R.string.slideshow_stopped, Toast.LENGTH_SHORT).show()
    }

    class OrientationTransformation(private val orientation: Float): Transformation() {
        lateinit var bitmap: coil3.Bitmap

        override val cacheKey = "rot$orientation"

        override suspend fun transform(input: coil3.Bitmap, size: Size): coil3.Bitmap {
            return coil3.Bitmap.createBitmap(input, 0, 0, input.width, input.height, Matrix().apply {
                postRotate(orientation)
            }, true).also {
                bitmap = it
            }
        }
    }

    private fun rotateImage() {
        orientationTransformation = OrientationTransformation(imageViewModel.rotationAngle).also {
            imageViewModel.imageLoader!!.enqueue(imageRequestBuilder!!.transformations(it).build())
        }
    }

    private fun askSaveRotation(callback: () -> Unit){
        if (imageViewModel.rotationAngle.mod(360f) != 0f && !slideshowActive) {
            CustomAlertDialogBuilder(this, theme)
                .keepFullScreen()
                .setTitle(R.string.warning)
                .setMessage(R.string.ask_save_img_rotated)
                .setNegativeButton(R.string.no) { _, _ -> callback() }
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                        val outputStream = ByteArrayOutputStream()
                        if (orientationTransformation?.bitmap?.compress(
                                if (fileName.endsWith("png", true)){
                                    Bitmap.CompressFormat.PNG
                                } else {
                                    Bitmap.CompressFormat.JPEG
                                }, 90, outputStream) == true
                        ){
                            if (encryptedVolume.importFile(ByteArrayInputStream(outputStream.toByteArray()), fileViewerViewModel.filePath!!)) {
                                Toast.makeText(this, R.string.image_saved_successfully, Toast.LENGTH_SHORT).show()
                                callback()
                            } else {
                                CustomAlertDialogBuilder(this, theme)
                                    .keepFullScreen()
                                    .setTitle(R.string.error)
                                    .setMessage(R.string.file_write_failed)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        } else {
                            CustomAlertDialogBuilder(this, theme)
                                .keepFullScreen()
                                .setTitle(R.string.error)
                                .setMessage(R.string.bitmap_compress_failed)
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        }
                }
                .show()
        } else {
            callback()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.imageViewer.restoreZoomNormal()
    }
}