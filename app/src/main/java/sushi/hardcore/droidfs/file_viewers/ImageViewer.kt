package sushi.hardcore.droidfs.file_viewers

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityImageViewerBinding
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.ZoomableImageView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

class ImageViewer: FileViewerActivity() {
    companion object {
        private const val hideDelay: Long = 3000
        private const val MIN_SWIPE_DISTANCE = 150
    }

    class ImageViewModel : ViewModel() {
        var imageBytes: ByteArray? = null
        var rotationAngle: Float = 0f
    }

    private lateinit var fileName: String
    private lateinit var handler: Handler
    private val imageViewModel: ImageViewModel by viewModels()
    private var requestBuilder: RequestBuilder<Drawable>? = null
    private var x1 = 0F
    private var x2 = 0F
    private var slideshowActive = false
    private var orientationTransformation: OrientationTransformation? = null
    private val hideUI = Runnable {
        binding.actionButtons.visibility = View.GONE
        binding.topBar.visibility = View.GONE
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
        supportActionBar?.hide()
        showPartialSystemUi()
        applyNavigationBarMargin(binding.root)
        handler = Handler(mainLooper)
        binding.imageViewer.setOnInteractionListener(object : ZoomableImageView.OnInteractionListener {
            override fun onSingleTap(event: MotionEvent?) {
                handler.removeCallbacks(hideUI)
                if (binding.actionButtons.visibility == View.GONE) {
                    binding.actionButtons.visibility = View.VISIBLE
                    binding.topBar.visibility = View.VISIBLE
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
                    createPlaylist() //be sure the playlist is created before deleting if there is only one image
                    if (encryptedVolume.deleteFile(filePath)) {
                        playlistNext(true)
                        refreshPlaylist()
                        if (mappedPlaylist.size == 0) { //deleted all images of the playlist
                            goBackToExplorer()
                        } else {
                            loadImage(true)
                        }
                    } else {
                        CustomAlertDialogBuilder(this, theme)
                            .keepFullScreen()
                            .setTitle(R.string.error)
                            .setMessage(getString(R.string.remove_failed, fileName))
                            .setPositiveButton(R.string.ok, null)
                            .show()
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
    fileName = File(filePath).name
    binding.textFilename.text = fileName
    val isVideo = isVideoFile(filePath)
    val thumbnailPath = File(filePath).parent + "/thumbnails" // path to files in volume
    if (newImage || imageViewModel.imageBytes == null) {
        loadWholeFile(filePath) {
            imageViewModel.imageBytes = it
            requestBuilder = if (isVideo) {
                Glide.with(this)
                    .asBitmap()
                    .load(Uri.fromFile(File(filePath)))
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .override(Target.SIZE_ORIGINAL) // load the original thumbnail
            } else {
                Glide.with(this)
                    .load(it)
                    .thumbnail(0.1f)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
            }
            requestBuilder?.into(binding.imageViewer)
            imageViewModel.rotationAngle = 0f

            // Save the thumbnail in the path of the files.
            if (!isVideo) {
                requestBuilder?.downloadOnly() // load the downsized thumbnail
                    ?.into(object : SimpleTarget<File>() {
                        override fun onResourceReady(resource: File, transition: Transition<in File>?) {
                            val thumbnailFile = File(thumbnailPath, fileName)
                            if (!thumbnailFile.exists()) {
                                thumbnailFile.createNewFile()
                            }
                            resource.copyTo(thumbnailFile, true)
                        }
                    })
            }
        }
    } else {
        requestBuilder = if (isVideo) {
            Glide.with(this)
                .asBitmap()
                .load(Uri.fromFile(File(filePath)))
                .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .override(Target.SIZE_ORIGINAL)
        } else {
            Glide.with(this)
                .load(imageViewModel.imageBytes)
                .thumbnail(0.1f)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
        }
        if (imageViewModel.rotationAngle.mod(360f) != 0f) {
            rotateImage()
        } else {
            requestBuilder?.into(binding.imageViewer)
        }
    }
}

private fun isVideoFile(filePath: String): Boolean {
    val mimeType = URLConnection.guessContentTypeFromName(filePath)
    return mimeType?.startsWith("video") ?: false
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

    private fun swipeImage(deltaX: Float, slideshowSwipe: Boolean = false){
        playlistNext(deltaX < 0)
        loadImage(true)
        if (slideshowActive) {
            if (!slideshowSwipe) { //reset slideshow delay if user swipes
                handler.removeCallbacks(slideshowNext)
            }
            handler.postDelayed(slideshowNext, Constants.SLIDESHOW_DELAY)
        }
    }

    private fun stopSlideshow(){
        slideshowActive = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(this, R.string.slideshow_stopped, Toast.LENGTH_SHORT).show()
    }

    class OrientationTransformation(private val orientation: Float): BitmapTransformation() {

        lateinit var bitmap: Bitmap

        override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap? {
            return Bitmap.createBitmap(toTransform, 0, 0, toTransform.width, toTransform.height, Matrix().apply {
                postRotate(orientation)
            }, true).also {
                bitmap = it
            }
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update("rotate$orientation".toByteArray())
        }
    }

    private fun rotateImage() {
        orientationTransformation = OrientationTransformation(imageViewModel.rotationAngle)
        requestBuilder?.transform(orientationTransformation)?.into(binding.imageViewer)
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
                            if (encryptedVolume.importFile(ByteArrayInputStream(outputStream.toByteArray()), filePath)) {
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
