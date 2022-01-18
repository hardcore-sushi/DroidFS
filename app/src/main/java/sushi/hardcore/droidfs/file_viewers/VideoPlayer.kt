package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import com.google.android.exoplayer2.ExoPlayer
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
        super.viewFile()
    }

    override fun bindPlayer(player: ExoPlayer) {
        binding.videoPlayer.player = player
        binding.videoPlayer.doubleTapOverlay = binding.doubleTapOverlay
        binding.videoPlayer.setControllerVisibilityListener { visibility ->
            binding.rotateButton.visibility = visibility
        }
        binding.rotateButton.setOnClickListener {
            requestedOrientation =
                if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }

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