package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import android.view.View
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
        binding.videoPlayer.hideController()
        binding.videoPlayer.setControllerVisibilityListener { visibility ->
            binding.rotateButton.visibility = visibility;
        }

    }

    override fun getFileType(): String {
        return "video"
    }

    override fun onPlayerReady() {
        if (firstPlay && autoFit) {
            requestedOrientation = if (binding.videoPlayer.width <  binding.videoPlayer.height) ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            firstPlay = false
        }
    }

    fun toggleScreenOrientation(view: View) {
        requestedOrientation =
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
    }

}