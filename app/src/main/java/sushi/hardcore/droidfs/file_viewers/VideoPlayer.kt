package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import com.google.android.exoplayer2.SimpleExoPlayer
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

    override fun bindPlayer(player: SimpleExoPlayer) {
        binding.videoPlayer.player = player
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
}