package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import sushi.hardcore.droidfs.databinding.ActivityVideoPlayerBinding

class VideoPlayer: MediaPlayer() {
    private var firstPlay = true
    private val autoFit by lazy {
        sharedPrefs.getBoolean("autoFit", false)
    }
    private lateinit var binding: ActivityVideoPlayerBinding

    override fun viewFile() {
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyNavigationBarMargin(binding.root)
        binding.videoPlayer.doubleTapOverlay = binding.doubleTapOverlay
        binding.videoPlayer.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            binding.topBar.visibility = visibility
            if (visibility == View.VISIBLE) {
                showPartialSystemUi()
            } else {
                hideSystemUi()
            }
        })
        binding.rotateButton.setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                }
        }
        super.viewFile()
    }

    override fun bindPlayer(player: ExoPlayer) {
        binding.videoPlayer.player = player
    }

    override fun onNewFileName(fileName: String) {
        binding.textFileName.text = fileName
    }

    override fun getFileType(): String {
        return "video"
    }

    override fun onVideoSizeChanged(width: Int, height: Int) {
        if (firstPlay && autoFit) {
            requestedOrientation = if (width < height)
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            firstPlay = false
        }
    }
}