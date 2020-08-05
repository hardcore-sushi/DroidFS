package sushi.hardcore.droidfs.file_viewers

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import kotlinx.android.synthetic.main.activity_image_viewer.*
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.util.MiscUtils
import sushi.hardcore.droidfs.explorers.ExplorerElement
import sushi.hardcore.droidfs.util.PathUtils
import java.security.MessageDigest
import kotlin.math.abs

class ImageViewer: FileViewerActivity() {
    companion object {
        private const val hideDelay: Long = 3000
        private const val MIN_SWIPE_DISTANCE = 150
    }
    private lateinit var glideImage: RequestBuilder<Drawable>
    private var x1 = 0F
    private var x2 = 0F
    private val mappedImages = mutableListOf<ExplorerElement>()
    private lateinit var sortOrder: String
    private var wasMapped = false
    private var currentMappedImageIndex = -1
    private var rotationAngle: Float = 0F
    private val handler = Handler()
    private val hideActionButtons = Runnable { action_buttons.visibility = View.GONE }
    override fun viewFile() {
        val imageBuff = loadWholeFile(filePath)
        if (imageBuff != null){
            setContentView(R.layout.activity_image_viewer)
            glideImage = Glide.with(this).load(imageBuff)
            glideImage.into(image_viewer)
            handler.postDelayed(hideActionButtons, hideDelay)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (!image_viewer.isZoomed){
            when(event?.action){
                MotionEvent.ACTION_DOWN -> {
                    x1 = event.x
                }
                MotionEvent.ACTION_UP -> {
                    x2 = event.x
                    val deltaX = x2 - x1
                    if (abs(deltaX) > MIN_SWIPE_DISTANCE){
                        if (!wasMapped){
                            for (e in gocryptfsVolume.recursiveMapFiles(PathUtils.getParentPath(filePath))){
                                if (e.isRegularFile && ConstValues.isImage(e.name)){
                                    mappedImages.add(e)
                                }
                            }
                            sortOrder = intent.getStringExtra("sortOrder") ?: "name"
                            ExplorerElement.sortBy(sortOrder, mappedImages)
                            for ((i, e) in mappedImages.withIndex()){
                                if (filePath == e.fullPath){
                                    currentMappedImageIndex = i
                                }
                            }
                            wasMapped = true
                        }
                        currentMappedImageIndex = if (deltaX < 0){
                            MiscUtils.incrementIndex(currentMappedImageIndex, mappedImages)
                        } else {
                            MiscUtils.decrementIndex(currentMappedImageIndex, mappedImages)
                        }
                        loadWholeFile(mappedImages[currentMappedImageIndex].fullPath)?.let {
                            glideImage = Glide.with(this).load(it)
                            glideImage.into(image_viewer)
                        }
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    class RotateTransformation(private val rotationAngle: Float): BitmapTransformation() {

        override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(rotationAngle)
            return Bitmap.createBitmap(toTransform, 0, 0, toTransform.width, toTransform.height, matrix, true)
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update("rotate$rotationAngle".toByteArray())
        }
    }

    private fun rotateImage(){
        image_viewer.restoreZoomNormal()
        glideImage.transform(RotateTransformation(rotationAngle)).into(image_viewer)
    }
    fun onCLickRotateRight(view: View){
        rotationAngle += 90
        rotateImage()
    }
    fun onClickRotateLeft(view: View){
        rotationAngle -= 90
        rotateImage()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (action_buttons.visibility == View.GONE){
            action_buttons.visibility = View.VISIBLE
            handler.removeCallbacks(hideActionButtons)
            handler.postDelayed(hideActionButtons, hideDelay)
        }
    }
}