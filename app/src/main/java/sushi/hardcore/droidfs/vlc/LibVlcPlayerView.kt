package sushi.hardcore.droidfs.vlc

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import sushi.hardcore.droidfs.file_viewers.VlcMediaSource
import sushi.hardcore.droidfs.widgets.DoubleTapOverlay
import sushi.hardcore.droidfs.widgets.PlayerControlFeedbackListener
import sushi.hardcore.droidfs.widgets.PlayerGestureTarget
import sushi.hardcore.droidfs.widgets.PlayerSystemGestureController

class LibVlcPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VLCVideoLayout(context, attrs, defStyleAttr) {
    lateinit var doubleTapOverlay: DoubleTapOverlay
    var controlFeedbackListener: PlayerControlFeedbackListener? = null
    var onSingleTap: (() -> Unit)? = null
    var onHideControls: (() -> Unit)? = null
    var onPlaybackEnded: (() -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var mediaSource: VlcMediaSource? = null
    private var paused = true
    private var hasFile = false
    private var initialized = false
    private var scalingMode = VlcScalingMode.BEST_FIT

    private val gestureTarget = object : PlayerGestureTarget {
        override val currentPosition: Long
            get() = currentPositionMs()
        override val duration: Long
            get() = durationMs()
        override val canSeek: Boolean
            get() = hasFile && durationMs() > 0 && player?.isSeekable == true

        override fun seekTo(positionMs: Long) {
            this@LibVlcPlayerView.seekTo(positionMs)
        }
    }

    private val gestureController = PlayerSystemGestureController(
        context,
        viewWidth = { width },
        viewHeight = { height },
        player = { gestureTarget },
        doubleTapOverlay = {
            if (::doubleTapOverlay.isInitialized) {
                doubleTapOverlay
            } else {
                null
            }
        },
        singleTapHandler = { onSingleTap?.invoke() ?: performClick() },
        hideController = { onHideControls?.invoke() },
        feedbackListener = { controlFeedbackListener }
    )

    fun initialize(appContext: Context) {
        if (initialized) {
            return
        }
        val options = arrayListOf(
            "--no-video-title-show",
            "--avcodec-fast",
            "--file-caching=750",
            "--clock-jitter=0",
            "--clock-synchro=0"
        )
        val vlc = LibVLC(appContext, options)
        val mediaPlayer = MediaPlayer(vlc)
        mediaPlayer.attachViews(this, null, false, false)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    hasFile = true
                    paused = false
                    applyScalingMode()
                }
                MediaPlayer.Event.Paused -> paused = true
                MediaPlayer.Event.Stopped -> {
                    hasFile = false
                    paused = true
                }
                MediaPlayer.Event.EndReached -> {
                    hasFile = false
                    paused = true
                    post { onPlaybackEnded?.invoke() }
                }
                MediaPlayer.Event.EncounteredError -> {
                    hasFile = false
                    paused = true
                    post { onPlaybackError?.invoke("LibVLC encountered an error while playing this file (event=${event.type})") }
                }
                MediaPlayer.Event.Vout -> applyScalingMode()
            }
        }
        libVlc = vlc
        player = mediaPlayer
        initialized = true
    }

    fun destroy() {
        val mediaPlayer = player
        if (mediaPlayer != null) {
            mediaPlayer.setEventListener(null)
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
        }
        mediaSource?.close()
        mediaSource = null
        libVlc?.release()
        libVlc = null
        player = null
        initialized = false
    }

    fun load(source: VlcMediaSource) {
        val vlc = libVlc ?: return
        val mediaPlayer = player ?: return
        mediaSource?.close()
        mediaSource = source
        hasFile = false
        paused = true

        val media = Media(vlc, source.fileDescriptor)
        media.setHWDecoderEnabled(true, false)
        media.addOption(":file-caching=750")
        media.addOption(":avcodec-fast")
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    fun setScalingMode(mode: VlcScalingMode) {
        scalingMode = mode
        applyScalingMode()
    }

    fun getScalingMode() = scalingMode

    fun playPause() {
        val mediaPlayer = player ?: return
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            paused = true
        } else {
            mediaPlayer.play()
            paused = false
        }
    }

    fun seekTo(positionMs: Long) {
        player?.setTime(positionMs.coerceAtLeast(0))
    }

    fun isPaused() = paused
    fun currentPositionMs() = player?.time?.coerceAtLeast(0) ?: 0
    fun durationMs() = player?.length?.coerceAtLeast(0) ?: 0

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureController.onTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyScalingMode()
    }

    private fun applyScalingMode() {
        val mediaPlayer = player ?: return
        when (scalingMode) {
            VlcScalingMode.BEST_FIT -> {
                mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
            }
            VlcScalingMode.FILL -> {
                mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL)
            }
            VlcScalingMode.VERTICAL -> applyAxisFit(mediaPlayer, fitHeight = true)
            VlcScalingMode.HORIZONTAL -> applyAxisFit(mediaPlayer, fitHeight = false)
        }
    }

    private fun applyAxisFit(mediaPlayer: MediaPlayer, fitHeight: Boolean) {
        val videoSize = currentVideoSize(mediaPlayer)
        if (videoSize == null || width <= 0 || height <= 0) {
            return
        }

        val (videoWidth, videoHeight) = videoSize
        mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_ORIGINAL)
        mediaPlayer.setAspectRatio(null)
        val scale = if (fitHeight) {
            height / videoHeight
        } else {
            width / videoWidth
        }
        mediaPlayer.setScale(scale.coerceAtLeast(0f))
    }

    private fun currentVideoSize(mediaPlayer: MediaPlayer): Pair<Float, Float>? {
        val track = mediaPlayer.currentVideoTrack ?: return null

        var videoWidth = track.width
        var videoHeight = track.height
        if (track.orientation == ORIENTATION_LEFT_BOTTOM ||
            track.orientation == ORIENTATION_RIGHT_TOP) {
            videoWidth = track.height
            videoHeight = track.width
        }
        val sarNum = track.sarNum.takeIf { it > 0 } ?: 1
        val sarDen = track.sarDen.takeIf { it > 0 } ?: 1
        val displayWidth = videoWidth * sarNum / sarDen.toFloat()
        val displayHeight = videoHeight.toFloat()
        if (displayWidth <= 0f || displayHeight <= 0f) {
            return null
        }
        return displayWidth to displayHeight
    }

    companion object {
        private const val ORIENTATION_LEFT_BOTTOM = 5
        private const val ORIENTATION_RIGHT_TOP = 6
    }
}

enum class VlcScalingMode {
    BEST_FIT,
    FILL,
    VERTICAL,
    HORIZONTAL
}
