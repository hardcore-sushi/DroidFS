package sushi.hardcore.droidfs.file_viewers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Handler
import android.util.DisplayMetrics
import android.view.View
import kotlinx.android.synthetic.main.activity_image_viewer.*
import sushi.hardcore.droidfs.R

class ImageViewer: FileViewerActivity() {
    companion object {
        private const val hideDelay: Long = 3000
    }
    private lateinit var bmpImage: Bitmap
    private val handler = Handler()
    private val hideActionButtons = Runnable { action_buttons.visibility = View.GONE }
    override fun viewFile() {
        loadWholeFile(filePath)?.let {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            bmpImage = decodeSampledBitmapFromBuffer(it, metrics.widthPixels, metrics.heightPixels)
            setContentView(R.layout.activity_image_viewer)
            image_viewer.setImageBitmap(bmpImage)
            handler.postDelayed(hideActionButtons, hideDelay)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth){
            val halfHeight = options.outHeight/2
            val halfWidth = options.outWidth/2
            while (halfHeight/inSampleSize >= reqHeight && halfWidth/inSampleSize >= reqWidth){
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    private fun decodeSampledBitmapFromBuffer(buff: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap {
        return BitmapFactory.Options().run {
            inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(buff, 0, buff.size, this)
            inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)
            inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(buff, 0, buff.size, this)
        }
    }

    private fun rotateImage(degrees: Float){
        val matrix = Matrix()
        matrix.postRotate(degrees)
        bmpImage = Bitmap.createBitmap(bmpImage, 0, 0, bmpImage.width, bmpImage.height, matrix, true)
        image_viewer.setImageBitmap(bmpImage)
    }
    fun onCLickRotateRight(view: View){
        rotateImage(90F)
    }
    fun onClickRotateLeft(view: View){
        rotateImage(-90F)
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