package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityVideoPlayerBinding
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import sushi.hardcore.droidfs.widgets.PlayerControl
import sushi.hardcore.droidfs.widgets.PlayerControlFeedbackListener
import sushi.hardcore.droidfs.vlc.VlcScalingMode
import java.io.File
import kotlin.math.roundToInt

class VideoPlayer : FileViewerActivity(true) {
    private lateinit var binding: ActivityVideoPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private var userSeeking = false
    private var playerInitialized = false
    private var orientationLocked = false
    private var abStartMs: Long? = null
    private var abEndMs: Long? = null

    private val hideControlFeedback = Runnable {
        binding.gestureFeedback.visibility = View.GONE
    }
    private val hideScrubFeedback = Runnable {
        binding.scrubFeedback.visibility = View.GONE
    }
    private val refreshControls = object : Runnable {
        override fun run() {
            enforceAbRepeat()
            updatePlaybackControls()
            handler.postDelayed(this, 500)
        }
    }

    override fun viewFile() {
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

            override fun onSeekPreview(positionMs: Long, durationMs: Long) {
                showScrubFeedback(positionMs, durationMs)
            }

            override fun onSeekPreviewFinished() {
                binding.scrubFeedback.removeCallbacks(hideScrubFeedback)
                binding.scrubFeedback.postDelayed(hideScrubFeedback, 700)
            }
        }
        binding.videoPlayer.onSingleTap = { toggleControls() }
        binding.videoPlayer.onHideControls = { setControlsVisible(false) }
        binding.videoPlayer.onPlaybackEnded = { onPlaybackEnded() }
        binding.videoPlayer.onPlaybackError = {
            showPlaybackError(getString(R.string.video_load_failed))
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.videoControls) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = insets.left + 12, right = insets.right + 12, bottom = insets.bottom)
            windowInsets
        }

        binding.rotateButton.setOnClickListener {
            orientationLocked = true
            requestedOrientation =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                }
            updateOrientationLockButton()
        }
        binding.buttonPlayPause.setOnClickListener {
            binding.videoPlayer.playPause()
            updatePlaybackControls()
        }
        binding.buttonRepeatMode.setOnClickListener {
            cycleRepeatMode()
        }
        binding.buttonScalingMode.setOnClickListener {
            cycleScalingMode()
        }
        binding.buttonOrientationLock.setOnClickListener {
            toggleOrientationLock()
        }
        binding.buttonAbStart.setOnClickListener {
            setAbRepeatStart()
        }
        binding.buttonAbEnd.setOnClickListener {
            setAbRepeatEnd()
        }
        binding.buttonAbClear.setOnClickListener {
            clearAbRepeat(showToast = true)
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
            binding.videoPlayer.initialize(applicationContext)
            playerInitialized = true
            binding.videoPlayer.setScalingMode(currentScalingMode())
        } catch (e: Throwable) {
            showPlaybackError(getString(R.string.video_playback_init_failed))
            return
        }

        updateRepeatModeButton()
        updateScalingModeButton()
        updateOrientationLockButton()
        updateAbRepeatUi()
        loadCurrentFile()
        lifecycleScope.launch {
            createPlaylist()
        }
        handler.post(refreshControls)
    }

    override fun getFileType() = "video"

    override fun onDestroy() {
        handler.removeCallbacks(refreshControls)
        if (playerInitialized) {
            binding.videoPlayer.destroy()
        }
        super.onDestroy()
    }

    private fun loadCurrentFile() {
        clearAbRepeat(showToast = false)
        val filePath = fileViewerViewModel.filePath!!
        binding.textFileName.text = File(filePath).name
        val requiresUnsafeExport = Build.VERSION.SDK_INT < Build.VERSION_CODES.O
        val allowUnsafeExport = sharedPrefs.getBoolean(PREF_UNSAFE_EXTERNAL_OPEN, false)
        if (requiresUnsafeExport && !allowUnsafeExport) {
            showPlaybackError(getString(R.string.video_legacy_unsafe_export_required))
            return
        }
        val mediaSource = try {
            VlcMediaSource.open(
                this,
                encryptedVolume,
                filePath,
                allowUnsafeExport = allowUnsafeExport
            )
        } catch (e: Throwable) {
            showPlaybackError(getString(R.string.video_source_open_failed))
            return
        }
        if (mediaSource == null) {
            showPlaybackError(getString(R.string.video_source_open_failed))
            return
        }
        try {
            binding.videoPlayer.load(mediaSource)
            binding.videoPlayer.setScalingMode(currentScalingMode())
        } catch (e: Throwable) {
            mediaSource.close()
            showPlaybackError(getString(R.string.video_load_failed))
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

    private fun showScrubFeedback(positionMs: Long, durationMs: Long) {
        binding.scrubFeedback.removeCallbacks(hideScrubFeedback)
        binding.textScrubPosition.text = formatDuration(positionMs)
        binding.textScrubDuration.text = formatDuration(durationMs)
        binding.progressScrubFeedback.progress = if (durationMs > 0) {
            (positionMs.coerceIn(0, durationMs) *
                binding.progressScrubFeedback.max / durationMs).toInt()
        } else {
            0
        }
        binding.scrubFeedback.visibility = View.VISIBLE
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

    private fun cycleRepeatMode() {
        val nextMode = when (sharedPrefs.getInt(PREF_REPEAT_MODE, REPEAT_MODE_ALL)) {
            REPEAT_MODE_OFF -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE -> REPEAT_MODE_ALL
            else -> REPEAT_MODE_OFF
        }
        sharedPrefs.edit()
            .putInt(PREF_REPEAT_MODE, nextMode)
            .apply()
        updateRepeatModeButton()
        Toast.makeText(this, repeatModeLabel(nextMode), Toast.LENGTH_SHORT).show()
    }

    private fun updateRepeatModeButton() {
        when (sharedPrefs.getInt(PREF_REPEAT_MODE, REPEAT_MODE_ALL)) {
            REPEAT_MODE_OFF -> {
                binding.buttonRepeatMode.setImageResource(R.drawable.icon_repeat_off)
                binding.buttonRepeatMode.contentDescription = getString(R.string.repeat_off)
            }
            REPEAT_MODE_ONE -> {
                binding.buttonRepeatMode.setImageResource(R.drawable.icon_repeat_one)
                binding.buttonRepeatMode.contentDescription = getString(R.string.repeat_one)
            }
            else -> {
                binding.buttonRepeatMode.setImageResource(R.drawable.icon_repeat)
                binding.buttonRepeatMode.contentDescription = getString(R.string.repeat_all)
            }
        }
    }

    private fun repeatModeLabel(repeatMode: Int): String {
        return getString(
            when (repeatMode) {
                REPEAT_MODE_OFF -> R.string.repeat_off
                REPEAT_MODE_ONE -> R.string.repeat_one
                else -> R.string.repeat_all
            }
        )
    }

    private fun cycleScalingMode() {
        val modes = arrayOf(
            VlcScalingMode.BEST_FIT,
            VlcScalingMode.FILL,
            VlcScalingMode.VERTICAL,
            VlcScalingMode.HORIZONTAL
        )
        val currentIndex = modes.indexOf(currentScalingMode()).coerceAtLeast(0)
        val nextMode = modes[(currentIndex + 1) % modes.size]
        sharedPrefs.edit()
            .putString(PREF_SCALING_MODE, nextMode.name)
            .apply()
        binding.videoPlayer.setScalingMode(nextMode)
        updateScalingModeButton()
        Toast.makeText(this, scalingModeLabel(nextMode), Toast.LENGTH_SHORT).show()
    }

    private fun currentScalingMode(): VlcScalingMode {
        val modeName = sharedPrefs.getString(PREF_SCALING_MODE, VlcScalingMode.BEST_FIT.name)
        return VlcScalingMode.values().firstOrNull { it.name == modeName }
            ?: VlcScalingMode.BEST_FIT
    }

    private fun updateScalingModeButton() {
        binding.buttonScalingMode.contentDescription = scalingModeLabel(currentScalingMode())
    }

    private fun scalingModeLabel(mode: VlcScalingMode): String {
        return getString(
            when (mode) {
                VlcScalingMode.BEST_FIT -> R.string.scale_best_fit
                VlcScalingMode.FILL -> R.string.scale_fill
                VlcScalingMode.VERTICAL -> R.string.scale_vertical
                VlcScalingMode.HORIZONTAL -> R.string.scale_horizontal
            }
        )
    }

    private fun toggleOrientationLock() {
        orientationLocked = !orientationLocked
        requestedOrientation = if (orientationLocked) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        updateOrientationLockButton()
        Toast.makeText(
            this,
            if (orientationLocked) R.string.orientation_locked else R.string.orientation_unlocked,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun updateOrientationLockButton() {
        binding.buttonOrientationLock.setImageResource(
            if (orientationLocked) {
                R.drawable.icon_lock
            } else {
                R.drawable.icon_lock_open
            }
        )
        binding.buttonOrientationLock.contentDescription = getString(
            if (orientationLocked) {
                R.string.orientation_locked
            } else {
                R.string.orientation_unlocked
            }
        )
    }

    private fun setAbRepeatStart() {
        val position = binding.videoPlayer.currentPositionMs()
        abStartMs = position
        if ((abEndMs ?: Long.MAX_VALUE) <= position) {
            abEndMs = null
        }
        updateAbRepeatUi()
        Toast.makeText(
            this,
            getString(R.string.ab_repeat_start_set, formatDuration(position)),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun setAbRepeatEnd() {
        val start = abStartMs
        if (start == null) {
            Toast.makeText(this, R.string.ab_repeat_need_start, Toast.LENGTH_SHORT).show()
            return
        }

        val position = binding.videoPlayer.currentPositionMs()
        if (position <= start + MIN_AB_REPEAT_DURATION_MS) {
            Toast.makeText(this, R.string.ab_repeat_need_later_end, Toast.LENGTH_SHORT).show()
            return
        }

        abEndMs = position
        updateAbRepeatUi()
        Toast.makeText(
            this,
            getString(R.string.ab_repeat_end_set, formatDuration(position)),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearAbRepeat(showToast: Boolean) {
        abStartMs = null
        abEndMs = null
        if (::binding.isInitialized) {
            updateAbRepeatUi()
        }
        if (showToast) {
            Toast.makeText(this, R.string.ab_repeat_cleared, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAbRepeatUi() {
        val start = abStartMs
        val end = abEndMs
        binding.textAbRange.text = when {
            start == null -> getString(R.string.ab_repeat_not_set)
            end == null -> getString(R.string.ab_repeat_waiting_end, formatDuration(start))
            else -> getString(R.string.ab_repeat_range, formatDuration(start), formatDuration(end))
        }
        binding.buttonAbEnd.isEnabled = start != null
        binding.buttonAbClear.isEnabled = start != null || end != null
        binding.buttonAbEnd.alpha = if (binding.buttonAbEnd.isEnabled) 1f else 0.45f
        binding.buttonAbClear.alpha = if (binding.buttonAbClear.isEnabled) 1f else 0.45f
    }

    private fun enforceAbRepeat() {
        val start = abStartMs ?: return
        val end = abEndMs ?: return
        if (end <= start) {
            return
        }

        val position = binding.videoPlayer.currentPositionMs()
        if (position >= end) {
            binding.videoPlayer.seekTo(start)
        }
    }

    private fun onPlaybackEnded() {
        val repeatMode = sharedPrefs.getInt(PREF_REPEAT_MODE, REPEAT_MODE_ALL)
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
        private const val PREF_REPEAT_MODE = "playerRepeatMode"
        private const val PREF_SCALING_MODE = "videoScalingMode"
        private const val PREF_UNSAFE_EXTERNAL_OPEN = "usf_open"
        private const val REPEAT_MODE_OFF = 0
        private const val REPEAT_MODE_ONE = 1
        private const val REPEAT_MODE_ALL = 2
        private const val MIN_AB_REPEAT_DURATION_MS = 500L
    }
}
