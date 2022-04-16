package sushi.hardcore.droidfs.file_viewers

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import sushi.hardcore.droidfs.ConstValues
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

    private lateinit var fileName: String
    private lateinit var handler: Handler
    private var bitmap: Bitmap? = null
    private var requestBuilder: RequestBuilder<Drawable>? = null
    private var x1 = 0F
    private var x2 = 0F
    private var slideshowActive = false
    private var originalOrientation: Float = 0f
    private var rotationAngle: Float = 0F
    private var orientationTransformation: OrientationTransformation? = null
    private val hideUI = Runnable {
        binding.actionButtons.visibility = View.GONE
        binding.topBar.visibility = View.GONE
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
        handler = Handler(mainLooper)
        binding.imageViewer.setOnInteractionListener(object : ZoomableImageView.OnInteractionListener {
            override fun onSingleTap(event: MotionEvent?) {
                handler.removeCallbacks(hideUI)
                if (binding.actionButtons.visibility == View.GONE) {
                    binding.actionButtons.visibility = View.VISIBLE
                    binding.topBar.visibility = View.VISIBLE
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
            CustomAlertDialogBuilder(this, themeValue)
                .keepFullScreen()
                .setTitle(R.string.warning)
                .setPositiveButton(R.string.ok) { _, _ ->
                    createPlaylist() //be sure the playlist is created before deleting if there is only one image
                    if (gocryptfsVolume.removeFile(filePath)) {
                        playlistNext(true)
                        refreshPlaylist()
                        if (mappedPlaylist.size == 0) { //deleted all images of the playlist
                            goBackToExplorer()
                        } else {
                            loadImage()
                        }
                    } else {
                        CustomAlertDialogBuilder(this, themeValue)
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
                handler.postDelayed(slideshowNext, ConstValues.SLIDESHOW_DELAY)
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
        binding.imageRotateRight.setOnClickListener {
            rotationAngle += 90
            rotateImage()
        }
        binding.imageRotateLeft.setOnClickListener {
            rotationAngle -= 90
            rotateImage()
        }
        loadImage()
        handler.postDelayed(hideUI, hideDelay)
    }

    private fun loadImage(){
        bitmap = null
        requestBuilder = null
        loadWholeFile(filePath)?.let {
            val displayWithGlide = if (it.size < 5_000_000) {
                true
            } else {
                bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                if (bitmap == null) {
                    true
                } else {
                    val orientation = ExifInterface(ByteArrayInputStream(it)).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    originalOrientation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                    val displayMetrics = Resources.getSystem().displayMetrics
                    if (displayMetrics.widthPixels < bitmap!!.width || displayMetrics.heightPixels < bitmap!!.height) {
                        val newWidth: Int
                        val newHeight: Int
                        if (displayMetrics.widthPixels > displayMetrics.heightPixels) {
                            newWidth = displayMetrics.widthPixels
                            newHeight = bitmap!!.height*displayMetrics.widthPixels/bitmap!!.width
                        } else {
                            newHeight = displayMetrics.heightPixels
                            newWidth = bitmap!!.width*displayMetrics.heightPixels/bitmap!!.height
                        }
                        bitmap = Bitmap.createScaledBitmap(bitmap!!, newWidth, newHeight, false)
                    }
                    Glide.with(this).load(bitmap).transform(OrientationTransformation(originalOrientation)).into(binding.imageViewer)
                    false
                }
            }
            if (displayWithGlide) {
                originalOrientation = 0f
                requestBuilder = Glide.with(this).load(it)
                requestBuilder?.into(binding.imageViewer)
            }
            fileName = File(filePath).name
            binding.textFilename.text = fileName
            rotationAngle = originalOrientation
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        handler.removeCallbacks(hideUI)
        handler.postDelayed(hideUI, hideDelay)
    }

    private fun swipeImage(deltaX: Float, slideshowSwipe: Boolean = false){
        playlistNext(deltaX < 0)
        loadImage()
        if (slideshowActive){
            if (!slideshowSwipe) { //reset slideshow delay if user swipes
                handler.removeCallbacks(slideshowNext)
            }
            handler.postDelayed(slideshowNext, ConstValues.SLIDESHOW_DELAY)
        }
    }

    private fun stopSlideshow(){
        slideshowActive = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Toast.makeText(this, R.string.slideshow_stopped, Toast.LENGTH_SHORT).show()
    }

    override fun onBackPressed() {
        if (slideshowActive){
            stopSlideshow()
        } else {
            askSaveRotation { super.onBackPressed() }
        }
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

    private fun rotateImage(){
        binding.imageViewer.restoreZoomNormal()
        orientationTransformation = OrientationTransformation(rotationAngle)
        (requestBuilder ?: Glide.with(this).load(bitmap)).transform(orientationTransformation).into(binding.imageViewer)
    }

    private fun askSaveRotation(callback: () -> Unit){
        if (rotationAngle.mod(360f) != originalOrientation && !slideshowActive) {
            CustomAlertDialogBuilder(this, themeValue)
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
                                }, 100, outputStream) == true
                        ){
                            if (gocryptfsVolume.importFile(ByteArrayInputStream(outputStream.toByteArray()), filePath)){
                                Toast.makeText(this, R.string.image_saved_successfully, Toast.LENGTH_SHORT).show()
                                callback()
                            } else {
                                CustomAlertDialogBuilder(this, themeValue)
                                    .keepFullScreen()
                                    .setTitle(R.string.error)
                                    .setMessage(R.string.file_write_failed)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        } else {
                            CustomAlertDialogBuilder(this, themeValue)
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