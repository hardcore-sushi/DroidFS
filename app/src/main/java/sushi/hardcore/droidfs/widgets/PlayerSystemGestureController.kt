package sushi.hardcore.droidfs.widgets

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerSystemGestureController(
    context: Context,
    private val viewWidth: () -> Int,
    private val viewHeight: () -> Int,
    private val player: () -> Player?,
    private val doubleTapOverlay: () -> DoubleTapOverlay?,
    private val singleTapHandler: () -> Unit,
    private val hideController: () -> Unit,
    private val feedbackListener: () -> PlayerControlFeedbackListener?
) {
    companion object {
        const val SEEK_SECONDS = 10
        const val SEEK_MILLISECONDS = SEEK_SECONDS * 1000
    }

    private val activity = context.findActivity()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private val stopDoubleTap = Runnable {
        isDoubleTapping = false
    }
    private val gestureListener = GestureListener()
    private val gestureDetector = GestureDetector(context, gestureListener)
    private var startBrightness = 0f
    private var startVolume = 0
    private var isDoubleTapping = false
    private var isVerticalSwiping = false
    private var suppressNextSingleTap = false
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isVerticalSwipeOnLeftSide = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL) {
            onTouchEnd()
        }
        return true
    }

    private fun onTouchEnd() {
        if (!isVerticalSwiping) {
            return
        }

        feedbackListener()?.onPlayerControlFinished()
        isVerticalSwiping = false
    }

    private fun onVerticalSwipeStart(leftSide: Boolean) {
        hideController()
        if (leftSide) {
            startBrightness = currentScreenBrightness()
            notifyControlChanged(PlayerControl.BRIGHTNESS, startBrightness)
        } else {
            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            notifyControlChanged(PlayerControl.VOLUME, currentVolumeFraction())
        }
    }

    private fun onVerticalSwipeChanged(leftSide: Boolean, distanceFraction: Float) {
        if (leftSide) {
            val brightness = (startBrightness + distanceFraction).coerceIn(0f, 1f)
            setScreenBrightness(brightness)
            notifyControlChanged(PlayerControl.BRIGHTNESS, brightness)
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val volume = (startVolume + distanceFraction * maxVolume).roundToInt()
                .coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
            notifyControlChanged(PlayerControl.VOLUME, volumeFraction(volume, maxVolume))
        }
    }

    private fun notifyControlChanged(control: PlayerControl, value: Float) {
        feedbackListener()?.onPlayerControlChanged(control, value)
    }

    private fun handleDoubleTap(x: Float, y: Float) {
        val player = player() ?: return
        val doubleTapOverlay = doubleTapOverlay() ?: return
        if (player.playbackState == PlaybackState.STATE_ERROR ||
            player.playbackState == PlaybackState.STATE_NONE ||
            player.playbackState == PlaybackState.STATE_STOPPED) {
            cancelDoubleTap()
        } else if (player.currentPosition > 500 && x < doubleTapOverlay.width * 0.35) {
            triggerSeek(false, x, y, player, doubleTapOverlay)
        } else if (player.currentPosition < player.duration && x > doubleTapOverlay.width * 0.65) {
            triggerSeek(true, x, y, player, doubleTapOverlay)
        }
    }

    private fun triggerSeek(
        forward: Boolean,
        x: Float,
        y: Float,
        player: Player,
        doubleTapOverlay: DoubleTapOverlay
    ) {
        doubleTapOverlay.showAnimation(forward, x, y)
        seekTo(
            player,
            if (forward) {
                player.currentPosition + SEEK_MILLISECONDS
            } else {
                player.currentPosition - SEEK_MILLISECONDS
            }
        )
    }

    private fun seekTo(player: Player, position: Long) {
        when {
            position <= 0 -> player.seekTo(0)
            position >= player.duration -> player.seekTo(player.duration)
            else -> {
                keepDoubleTapping()
                player.seekTo(position)
            }
        }
    }

    private fun cancelDoubleTap() {
        handler.removeCallbacks(stopDoubleTap)
        isDoubleTapping = false
    }

    private fun keepDoubleTapping() {
        handler.removeCallbacks(stopDoubleTap)
        isDoubleTapping = true
        handler.postDelayed(stopDoubleTap, 700)
    }

    private fun currentScreenBrightness(): Float {
        val window = activity?.window ?: return 0.5f
        if (window.attributes.screenBrightness >= 0f) {
            return window.attributes.screenBrightness
        }

        return try {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Settings.SettingNotFoundException) {
            0.5f
        }
    }

    private fun setScreenBrightness(brightness: Float) {
        val window = activity?.window ?: return
        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness
        window.attributes = layoutParams
    }

    private fun currentVolumeFraction(): Float {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return volumeFraction(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), maxVolume)
    }

    private fun volumeFraction(volume: Int, maxVolume: Int): Float {
        return if (maxVolume == 0) 0f else volume / maxVolume.toFloat()
    }

    private tailrec fun Context.findActivity(): Activity? {
        return when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            touchStartX = e.x
            touchStartY = e.y
            isVerticalSwiping = false
            return true
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            if (!isVerticalSwiping && !isVerticalSwipe(e2)) {
                return false
            }

            handleVerticalSwipe(e2)
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (isDoubleTapping && !isVerticalSwiping) {
                handleDoubleTap(e.x, e.y)
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isDoubleTapping && !suppressNextSingleTap) {
                singleTapHandler()
            }
            suppressNextSingleTap = false
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!isDoubleTapping && !isVerticalSwiping) {
                keepDoubleTapping()
            }
            return true
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP && isDoubleTapping && !isVerticalSwiping) {
                handleDoubleTap(e.x, e.y)
            }
            return true
        }

        private fun isVerticalSwipe(event: MotionEvent): Boolean {
            val dx = event.x - touchStartX
            val dy = event.y - touchStartY
            return abs(dy) > touchSlop && abs(dy) > abs(dx)
        }

        private fun handleVerticalSwipe(event: MotionEvent) {
            if (!isVerticalSwiping) {
                cancelDoubleTap()
                isVerticalSwiping = true
                suppressNextSingleTap = true
                isVerticalSwipeOnLeftSide = touchStartX < viewWidth() / 2f
                onVerticalSwipeStart(isVerticalSwipeOnLeftSide)
            }

            val distanceFraction = (touchStartY - event.y) / viewHeight().coerceAtLeast(1)
            onVerticalSwipeChanged(isVerticalSwipeOnLeftSide, distanceFraction)
        }
    }
}
