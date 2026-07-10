package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityVideoPlayerBinding
import sushi.hardcore.droidfs.util.CrashDiagnostics
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.PlayerControl
import sushi.hardcore.droidfs.widgets.PlayerControlFeedbackListener
import java.io.File
import kotlin.math.roundToInt

class VideoPlayer : FileViewerActivity(true) {
    private lateinit var binding: ActivityVideoPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var currentMediaSource: VlcMediaSource? = null
    private var controlsVisible = true
    private var userSeeking = false
    private var playerInitialized = false

    private val hideControlFeedback = Runnable {
        binding.gestureFeedback.visibility = View.GONE
    }
    private val refreshControls = object : Runnable {
        override fun run() {
            updatePlaybackControls()
            handler.postDelayed(this, 500)
        }
    }

    override fun viewFile() {
        CrashDiagnostics.record(this, "VideoPlayer.viewFile path=${intent.getStringExtra("path")}")
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topBar.fitsSystemWindows = true
        binding.videoPlayer.doubleTapOverlay = binding.doubleTapOverlay
        binding.videoPlayer.controlFeedbackListener = object : PlayerControlFeedbackListener {
            override fun onPlayerControlChanged(control: PlayerControl, value: Float) {
                showControlFeedback(control, value)
            }

            override fun onPlayerControlFinished() {
                binding.gestureFeedback.removeCallbacks(hideControlFeedback)
                binding.gestureFeedback.postDelayed(hideControlFeedback, 700)
            }
        }
        binding.videoPlayer.onSingleTap = { toggleControls() }
        binding.videoPlayer.onHideControls = { setControlsVisible(false) }
        binding.videoPlayer.onPlaybackEnded = { onPlaybackEnded() }
        binding.videoPlayer.onPlaybackError = { showPlaybackError(it) }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoControls) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = insets.left + 12, right = insets.right + 12, bottom = insets.bottom)
            windowInsets
        }

        binding.rotateButton.setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                }
        }
        binding.buttonPlayPause.setOnClickListener {
            binding.videoPlayer.playPause()
            updatePlaybackControls()
        }
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = binding.videoPlayer.durationMs()
                    binding.textPosition.text = formatDuration(duration * progress / seekBar.max)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val duration = binding.videoPlayer.durationMs()
                binding.videoPlayer.seekTo(duration * seekBar.progress / seekBar.max)
                userSeeking = false
            }
        })

        try {
            CrashDiagnostics.record(this, "VideoPlayer initializing LibVLC")
            binding.videoPlayer.initialize(applicationContext)
            playerInitialized = true
        } catch (e: Throwable) {
            showPlaybackError("LibVLC initialization failed\n\n${CrashDiagnostics.stackTrace(e)}")
            return
        }

        loadCurrentFile()
        lifecycleScope.launch {
            createPlaylist()
        }
        handler.post(refreshControls)
    }

    override fun getFileType() = "video"

    override fun onDestroy() {
        handler.removeCallbacks(refreshControls)
        currentMediaSource?.close()
        currentMediaSource = null
        if (playerInitialized) {
            binding.videoPlayer.destroy()
        }
        super.onDestroy()
    }

    private fun loadCurrentFile() {
        currentMediaSource?.close()
        currentMediaSource = null
        val filePath = fileViewerViewModel.filePath!!
        CrashDiagnostics.record(this, "VideoPlayer.loadCurrentFile path=$filePath")
        binding.textFileName.text = File(filePath).name
        val mediaSource = try {
            VlcMediaSource.open(this, encryptedVolume, filePath)
        } catch (e: Throwable) {
            showPlaybackError("Opening video source failed\n\n${CrashDiagnostics.stackTrace(e)}")
            return
        }
        if (mediaSource == null) {
            showPlaybackError(getString(R.string.get_size_failed))
            return
        }
        currentMediaSource = mediaSource
        try {
            CrashDiagnostics.record(this, "VideoPlayer loading source into LibVLC")
            binding.videoPlayer.load(mediaSource)
        } catch (e: Throwable) {
            showPlaybackError("Loading video into LibVLC failed\n\n${CrashDiagnostics.stackTrace(e)}")
        }
    }

    private fun showControlFeedback(control: PlayerControl, value: Float) {
        val progress = (value * 100).roundToInt().coerceIn(0, 100)
        binding.gestureFeedback.removeCallbacks(hideControlFeedback)
        binding.imageGestureFeedback.setImageResource(
            if (control == PlayerControl.BRIGHTNESS) {
                R.drawable.icon_brightness
            } else {
                R.drawable.icon_speaker_volume
            }
        )
        binding.progressGestureFeedback.progress = progress
        binding.textGestureFeedback.text = "$progress%"
        binding.gestureFeedback.visibility = View.VISIBLE
    }

    private fun toggleControls() {
        setControlsVisible(!controlsVisible)
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        binding.topBar.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        binding.videoControls.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        if (controlsVisible) {
            showPartialSystemUi()
        } else {
            hideSystemUi()
        }
    }

    private fun updatePlaybackControls() {
        val duration = binding.videoPlayer.durationMs().coerceAtLeast(0)
        val position = binding.videoPlayer.currentPositionMs().coerceIn(0, duration.coerceAtLeast(0))
        if (binding.videoPlayer.isPaused()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding.buttonPlayPause.setImageResource(
            if (binding.videoPlayer.isPaused()) {
                R.drawable.icon_triangle
            } else {
                R.drawable.icon_pause
            }
        )
        binding.textDuration.text = formatDuration(duration)
        if (!userSeeking) {
            binding.textPosition.text = formatDuration(position)
            binding.seekBar.progress = if (duration > 0) {
                (position * binding.seekBar.max / duration).toInt()
            } else {
                0
            }
        }
    }

    private fun onPlaybackEnded() {
        val repeatMode = sharedPrefs.getInt("playerRepeatMode", REPEAT_MODE_ALL)
        when (repeatMode) {
            REPEAT_MODE_ONE -> loadCurrentFile()
            else -> lifecycleScope.launch {
                createPlaylist()
                if (repeatMode == REPEAT_MODE_OFF &&
                    fileViewerViewModel.currentPlaylistIndex == fileViewerViewModel.playlist.lastIndex) {
                    return@launch
                }
                playlistNext(true)
                loadCurrentFile()
            }
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0) / 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun showPlaybackError(message: String) {
        CustomAlertDialogBuilder(this, theme)
            .setTitle(R.string.error)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> goBackToExplorer() }
            .show()
    }

    companion object {
        private const val REPEAT_MODE_OFF = 0
        private const val REPEAT_MODE_ONE = 1
        private const val REPEAT_MODE_ALL = 2
    }
}
