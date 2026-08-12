package sushi.hardcore.droidfs.file_viewers

import android.app.ActivityManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.os.Handler
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.target
import coil3.request.transformations
import coil3.size.Precision
import coil3.size.Size
import coil3.transform.Transformation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.VolumeManagerApp
import sushi.hardcore.droidfs.databinding.ActivityImageViewerBinding
import sushi.hardcore.droidfs.filesystems.EncryptedVolume
import sushi.hardcore.droidfs.util.PathUtils
import sushi.hardcore.droidfs.widgets.ZoomableImageView
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.util.Random
import kotlin.math.abs
import kotlin.math.sqrt

class ImageViewer: FileViewerActivity() {
    companion object {
        private const val TAG = "ImageViewer"
        private const val hideDelay: Long = 3000
        private const val MIN_SWIPE_DISTANCE = 150

        private const val COIL_SIZE_EXTRA = "coil#size"
        private const val COIL_TRANSFORMATIONS_EXTRA = "coil#transformations"

        private val EXIF_IMAGE_TYPE_JPEG by lazy { getExifIntField("IMAGE_TYPE_JPEG") }
        private val EXIF_IMAGE_TYPE_PNG by lazy { getExifIntField("IMAGE_TYPE_PNG") }
        private val EXIF_IMAGE_TYPE_WEBP by lazy { getExifIntField("IMAGE_TYPE_WEBP") }

        private fun getExifIntField(name: String, instance: Any? = null): Int {
            return ExifInterface::class.java
                .getDeclaredField(name)
                .apply { isAccessible = true }
                .getInt(instance)
        }

        private fun ExifInterface.getSaveAttributesMethod(): Method {
            val mimeType = getExifIntField("mMimeType", this)
            val methodName = when (mimeType) {
                EXIF_IMAGE_TYPE_JPEG -> "saveJpegAttributes"
                EXIF_IMAGE_TYPE_PNG -> "savePngAttributes"
                EXIF_IMAGE_TYPE_WEBP -> "saveWebpAttributes"
                else -> throw IllegalArgumentException("Unsupported mime type: $mimeType")
            }
            return ExifInterface::class.java
                .getDeclaredMethod(methodName, InputStream::class.java, OutputStream::class.java)
        }
    }

    class ImageViewModel : ViewModel() {
        var rotationAngle: Int = 0
    }

    override val blackBackground: Boolean = true
    private lateinit var fileName: String
    private lateinit var handler: Handler
    private val imageViewModel: ImageViewModel by viewModels()
    private val imageLoader: ImageLoader by lazy {
        (application as VolumeManagerApp).volumeManager.getImageLoader(volumeId)
    }
    private val maxDimension by lazy {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val budget = am.memoryClass * 1024L * 1024L / 16L
        sqrt(budget / 4.0).toInt()
    }
    private var x1 = 0F
    private var x2 = 0F
    private var slideshowActive = false
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
    private val random = Random()

    override fun getFileType(): String {
        return "image"
    }

    override fun viewFile() {
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.overlay.fitsSystemWindows = true
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
            MaterialAlertDialogBuilder(this)
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
                            MaterialAlertDialogBuilder(this@ImageViewer)
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
        binding.imageRotateRight.setOnClickListener { onClickRotate(90) }
        binding.imageRotateLeft.setOnClickListener { onClickRotate(-90) }
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
            imageViewModel.rotationAngle = 0
        }
        displayImage()
    }

    private fun imageRequestBuilder() = ImageRequest.Builder(this).data(fileViewerViewModel.filePath)
        .target(binding.imageViewer)
        .size(maxDimension, maxDimension)
        .precision(Precision.INEXACT)

    override fun onUserInteraction() {
        super.onUserInteraction()
        handler.removeCallbacks(hideUI)
        handler.postDelayed(hideUI, hideDelay)
    }

    private fun onClickRotate(angle: Int) {
        imageViewModel.rotationAngle = (imageViewModel.rotationAngle + angle).mod(360)
        binding.imageViewer.restoreZoomNormal()
        displayImage()
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
        override val cacheKey = "rot$orientation"

        override suspend fun transform(input: coil3.Bitmap, size: Size): coil3.Bitmap {
            return coil3.Bitmap.createBitmap(input, 0, 0, input.width, input.height, Matrix().apply {
                postRotate(orientation)
            }, true)
        }
    }

    private fun displayImage() {
        val rotation = imageViewModel.rotationAngle
        imageLoader.enqueue(
            if (rotation == 0) {
                imageRequestBuilder().build()
            } else {
                imageRequestBuilder().transformations(OrientationTransformation(rotation.toFloat())).build()
            }
        )
    }

    private fun askSaveRotation(callback: () -> Unit){
        if (imageViewModel.rotationAngle != 0 && !slideshowActive) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.warning)
                .setMessage(R.string.ask_save_img_rotated)
                .setNegativeButton(R.string.no) { _, _ -> callback() }
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    lifecycleScope.launch {
                        val errorString = saveRotationMetadata()
                        if (errorString == null) {
                            Toast.makeText(this@ImageViewer, R.string.image_saved_successfully, Toast.LENGTH_SHORT).show()
                            callback()
                        } else {
                            MaterialAlertDialogBuilder(this@ImageViewer)
                                .setTitle(R.string.error)
                                .setMessage(errorString)
                                .setPositiveButton(R.string.ok, null)
                                .show()
                        }
                    }
                }
                .show()
        } else {
            callback()
        }
    }

    private suspend fun saveRotationMetadata(): String? = withContext(Dispatchers.IO) {
        val path = fileViewerViewModel.filePath!!
        val (fileBytes, errorCode) = encryptedVolume.loadWholeFile(path)
        if (errorCode != 0) {
            return@withContext EncryptedVolume.loadWholeFileErrorToString(this@ImageViewer, errorCode)
        }
        try {
            val exif = ByteArrayInputStream(fileBytes).use { ExifInterface(it) }
            val currentOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            if (currentOrientation == ExifInterface.ORIENTATION_UNDEFINED) {
                exif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            }
            exif.rotate(imageViewModel.rotationAngle)
            val saveMethod = exif.getSaveAttributesMethod()
            saveMethod.isAccessible = true
            val parentPath = PathUtils.getParentPath(path)
            var tmpPath: String
            do {
                tmpPath = PathUtils.pathJoin(parentPath, ".tmp."+random.nextInt())
            } while (encryptedVolume.pathExists(tmpPath))
            val out = encryptedVolume.openOutputStream(tmpPath)
                ?: return@withContext getString(R.string.file_write_failed)
            out.use { out ->
                ByteArrayInputStream(fileBytes).use { input ->
                    saveMethod.invoke(exif, input, out)
                }
            }
            if (!encryptedVolume.rename(tmpPath, path) ) {
                return@withContext getString(R.string.rename_failed, tmpPath)
            }
            rotateCacheKeys(path, imageViewModel.rotationAngle)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save rotation metadata", e)
            e.localizedMessage
        }
    }

    private fun rotateCacheKeys(path: String, rotationDelta: Int) {
        val cache = imageLoader.memoryCache ?: return
        val matchedKeys = cache.keys.filter { it.key == path }
        val sizeExtra = matchedKeys.firstNotNullOfOrNull { key -> key.extras[COIL_SIZE_EXTRA] }
        val newKeys = mutableMapOf<MemoryCache.Key, MemoryCache.Value>()
        for (key in matchedKeys) {
            val value = cache[key] ?: continue
            cache.remove(key)
            val rotation = key.extras[COIL_TRANSFORMATIONS_EXTRA]
                ?.takeIf { it.startsWith("0:rot") }
                ?.substringAfter("rot")
                ?.toFloatOrNull()
                ?.toInt()
                ?: 0
            val newRotation = (rotation - rotationDelta).mod(360)
            val newExtras = if (newRotation == 0) {
                emptyMap()
            } else {
                val size = key.extras[COIL_SIZE_EXTRA] ?: sizeExtra ?: continue
                mapOf(
                    COIL_TRANSFORMATIONS_EXTRA to "0:rot${newRotation.toFloat()}",
                    COIL_SIZE_EXTRA to size,
                )
            }
            val newKey = key.copy(extras = newExtras)
            newKeys[newKey] = value
        }
        newKeys.forEach { (newKey, value) -> cache[newKey] = value }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.imageViewer.restoreZoomNormal()
    }
}