package sushi.hardcore.droidfs.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.media3.ui.PlayerView
import sushi.hardcore.droidfs.R

class DoubleTapPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    companion object {
        const val SEEK_SECONDS = 10
        const val SEEK_MILLISECONDS = SEEK_SECONDS*1000
    }

    lateinit var doubleTapOverlay: DoubleTapOverlay
    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        private var isDoubleTapping = false
        private val handler = Handler(Looper.getMainLooper())
        private val stopDoubleTap = Runnable {
            isDoubleTapping = false
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (isDoubleTapping) {
                handleDoubleTap(e.x, e.y)
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isDoubleTapping)
                performClick()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!isDoubleTapping)
                keepDoubleTapping()
            return true
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP && isDoubleTapping)
                handleDoubleTap(e.x, e.y)
            return true
        }

        fun cancelDoubleTap() {
            handler.removeCallbacks(stopDoubleTap)
            isDoubleTapping = false
        }

        fun keepDoubleTapping() {
            handler.removeCallbacks(stopDoubleTap)
            isDoubleTapping = true
            handler.postDelayed(stopDoubleTap, 700)
        }
    }
    private val gestureDetector = GestureDetectorCompat(context, gestureListener)
    private val density by lazy {
        context.resources.displayMetrics.density
    }
    private val originalExoIconPaddingBottom by lazy {
        resources.getDimension(R.dimen.exo_icon_padding_bottom)
    }
    private val originalExoIconSize by lazy {
        resources.getDimension(R.dimen.exo_icon_size)
    }

    init {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            handleOrientationChange(Configuration.ORIENTATION_LANDSCAPE)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    fun handleDoubleTap(x: Float, y: Float) {
        player?.let { player ->
            if (player.playbackState == PlaybackState.STATE_ERROR ||
                player.playbackState == PlaybackState.STATE_NONE ||
                player.playbackState == PlaybackState.STATE_STOPPED)
                gestureListener.cancelDoubleTap()
            else if (player.currentPosition > 500 && x < doubleTapOverlay.width * 0.35)
                triggerSeek(false, x, y)
            else if (player.currentPosition < player.duration && x > doubleTapOverlay.width * 0.65)
                triggerSeek(true, x, y)
        }
    }

    private fun triggerSeek(forward: Boolean, x: Float, y: Float) {
        doubleTapOverlay.showAnimation(forward, x, y)
        player?.let { player ->
            seekTo(
                if (forward)
                    player.currentPosition + SEEK_MILLISECONDS
                else
                    player.currentPosition - SEEK_MILLISECONDS
            )
        }
    }

    private fun seekTo(position: Long) {
        player?.let { player ->
            when {
                position <= 0 -> player.seekTo(0)
                position >= player.duration -> player.seekTo(player.duration)
                else -> {
                    gestureListener.keepDoubleTapping()
                    player.seekTo(position)
                }
            }
        }
    }

    private fun updateButtonSize(orientation: Int) {
        val size = (if (orientation == Configuration.ORIENTATION_LANDSCAPE) 45*density else originalExoIconSize).toInt()
        listOf(R.id.exo_prev, R.id.exo_rew_with_amount, R.id.exo_play_pause, R.id.exo_ffwd_with_amount, R.id.exo_next).forEach {
            findViewById<View>(it).updateLayoutParams {
                width = size
                height = size
            }
        }
        // fix text vertical alignment inside icons
        val paddingBottom = (if (orientation == Configuration.ORIENTATION_LANDSCAPE) 15*density else originalExoIconPaddingBottom).toInt()
        listOf(R.id.exo_rew_with_amount, R.id.exo_ffwd_with_amount).forEach {
            findViewById<Button>(it).updatePadding(bottom = paddingBottom)
        }
    }

    private fun handleOrientationChange(orientation: Int) {
        val centerControls = findViewById<LinearLayout>(R.id.exo_center_controls)
        (centerControls.parent as ViewGroup).removeView(centerControls)
        findViewById<FrameLayout>(if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            R.id.center_controls_bar
        } else {
            R.id.center_controls_external
        }).addView(centerControls)
        updateButtonSize(orientation)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        handleOrientationChange(newConfig.orientation)
    }
}