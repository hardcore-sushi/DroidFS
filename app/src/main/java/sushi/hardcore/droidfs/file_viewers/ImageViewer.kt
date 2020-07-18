package sushi.hardcore.droidfs.file_viewers

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import kotlinx.android.synthetic.main.activity_image_viewer.*
import sushi.hardcore.droidfs.R
import java.security.MessageDigest

class ImageViewer: FileViewerActivity() {
    companion object {
        private const val hideDelay: Long = 3000
    }
    private lateinit var glideImage: RequestBuilder<Drawable>
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