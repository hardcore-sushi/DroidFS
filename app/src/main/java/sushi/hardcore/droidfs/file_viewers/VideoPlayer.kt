package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.exoplayer2.ExoPlayer
import sushi.hardcore.droidfs.R
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
        binding.videoPlayer.doubleTapOverlay = binding.doubleTapOverlay

        binding.videoPlayer.setControllerVisibilityListener { visibility ->
            if (visibility == View.VISIBLE) {
                showPartialSystemUi()
            }
            else {
                hideSystemUi()
            }
        }

        val rotateButton = findViewById<ImageButton>(R.id.rotate_button)
        rotateButton.setOnClickListener {
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
        val textFileName = findViewById<TextView>(R.id.text_file_name)
        textFileName.text = fileName
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