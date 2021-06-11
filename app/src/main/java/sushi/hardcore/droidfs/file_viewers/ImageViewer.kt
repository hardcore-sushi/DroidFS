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
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityImageViewerBinding
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder
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
    private lateinit var glideImage: RequestBuilder<Drawable>
    private var x1 = 0F
    private var x2 = 0F
    private var slideshowActive = false
    private var rotationAngle: Float = 0F
    private var rotatedBitmap: Bitmap? = null
    private val handler = Handler()
    private val hideUI = Runnable {
        binding.actionButtons.visibility = View.GONE
        binding.actionBar.visibility = View.GONE
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
        binding.imageViewer.setOnInteractionListener(object : ZoomableImageView.OnInteractionListener {
            override fun onSingleTap(event: MotionEvent?) {
                handler.removeCallbacks(hideUI)
                if (binding.actionButtons.visibility == View.GONE) {
                    binding.actionButtons.visibility = View.VISIBLE
                    binding.actionBar.visibility = View.VISIBLE
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
            ColoredAlertDialogBuilder(this)
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
                        ColoredAlertDialogBuilder(this)
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
                handler.postDelayed(slideshowNext, ConstValues.slideshow_delay)
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
        loadWholeFile(filePath)?.let {
            glideImage = Glide.with(this).load(it)
            glideImage.into(binding.imageViewer)
            fileName = File(filePath).name
            binding.textFilename.text = fileName
            rotationAngle = 0F
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
            handler.postDelayed(slideshowNext, ConstValues.slideshow_delay)
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

    class RotateTransformation(private val imageViewer: ImageViewer): BitmapTransformation() {

        override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap? {
            val matrix = Matrix()
            matrix.postRotate(imageViewer.rotationAngle)
            imageViewer.rotatedBitmap = Bitmap.createBitmap(toTransform, 0, 0, toTransform.width, toTransform.height, matrix, true)
            return imageViewer.rotatedBitmap
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update("rotate${imageViewer.rotationAngle}".toByteArray())
        }
    }

    private fun rotateImage(){
        binding.imageViewer.restoreZoomNormal()
        glideImage.transform(RotateTransformation(this)).into(binding.imageViewer)
    }

    private fun askSaveRotation(callback: () -> Unit){
        if (rotationAngle%360 != 0f && !slideshowActive){
            ColoredAlertDialogBuilder(this)
                .keepFullScreen()
                .setTitle(R.string.warning)
                .setMessage(R.string.ask_save_img_rotated)
                .setNegativeButton(R.string.no) { _, _ -> callback() }
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                        val outputStream = ByteArrayOutputStream()
                        if (rotatedBitmap?.compress(
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
                                ColoredAlertDialogBuilder(this)
                                    .keepFullScreen()
                                    .setTitle(R.string.error)
                                    .setMessage(R.string.file_write_failed)
                                    .setPositiveButton(R.string.ok, null)
                                    .show()
                            }
                        } else {
                            ColoredAlertDialogBuilder(this)
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