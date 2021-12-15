package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.exoplayer2.ExoPlayer
import sushi.hardcore.droidfs.gestures.PlayerTouchListener
import sushi.hardcore.droidfs.databinding.ActivityVideoPlayerBinding

class VideoPlayer: MediaPlayer() {
    private var firstPlay = true
    private val autoFit by lazy {
        sharedPrefs.getBoolean("autoFit", false)
    }
    private val legacyMod by lazy {
        sharedPrefs.getBoolean("legacyMod", false)
    }
    private lateinit var binding: ActivityVideoPlayerBinding

    override fun viewFile() {
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.viewFile()
    }

    override fun hideSystemUi(){
        if (legacyMod) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        } else {
            val windowInsetsController =
                ViewCompat.getWindowInsetsController(window.decorView) ?: return
            // Configure the behavior of the hidden system bars
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // Hide both the status bar and the navigation bar
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun bindPlayer(player: ExoPlayer) {
        binding.videoPlayer.player = player
        binding.videoPlayer.hideController()
        binding.videoPlayer.setControllerVisibilityListener { visibility ->
            binding.rotateButton.visibility = visibility;
        }

        binding.videoPlayer.setOnTouchListener(object : PlayerTouchListener(this@VideoPlayer) {
            override fun onHorizontalSwipe(deltaX: Float): Boolean {
                player.seekTo(player.currentPosition + if (deltaX < SENSITIVITY_BIG) JUMP_SPAN else JUMP_SPAN_BIG)
                return true
            }
        })

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