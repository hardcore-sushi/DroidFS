package sushi.hardcore.droidfs.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player

class DoubleTapPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    lateinit var doubleTapOverlay: DoubleTapOverlay
    var controlFeedbackListener: PlayerControlFeedbackListener? = null
    private val gestureController = PlayerSystemGestureController(
        context,
        viewWidth = { width },
        viewHeight = { height },
        player = {
            player?.let {
                object : PlayerGestureTarget {
                    override val currentPosition: Long
                        get() = it.currentPosition
                    override val duration: Long
                        get() = it.duration
                    override val canSeek: Boolean
                        get() = it.playbackState != Player.STATE_IDLE && it.duration > 0

                    override fun seekTo(positionMs: Long) {
                        it.seekTo(positionMs)
                    }
                }
            }
        },
        doubleTapOverlay = {
            if (::doubleTapOverlay.isInitialized) {
                doubleTapOverlay
            } else {
                null
            }
        },
        singleTapHandler = { performClick() },
        hideController = { hideController() },
        feedbackListener = { controlFeedbackListener }
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureController.onTouchEvent(event)
    }
}
