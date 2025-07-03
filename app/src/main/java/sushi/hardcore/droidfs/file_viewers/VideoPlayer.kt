package sushi.hardcore.droidfs.file_viewers

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.databinding.ActivityVideoPlayerBinding

class VideoPlayer: MediaPlayer(true) {
    private var firstPlay = true
    private val autoFit by lazy {
        sharedPrefs.getBoolean("autoFit", false)
    }
    private lateinit var binding: ActivityVideoPlayerBinding

    override fun viewFile() {
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.topBar.fitsSystemWindows = true
        binding.videoPlayer.doubleTapOverlay = binding.doubleTapOverlay
        val bottomBar = findViewById<FrameLayout>(R.id.exo_bottom_bar)
        val progressBar = findViewById<View>(R.id.exo_progress)
        ViewCompat.setOnApplyWindowInsetsListener(binding.videoPlayer) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomBar.apply {
                updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
                updateLayoutParams<FrameLayout.LayoutParams> {
                    @SuppressLint("PrivateResource")
                    height = resources.getDimensionPixelSize(R.dimen.exo_styled_bottom_bar_height) + insets.bottom
                }
            }
            progressBar.apply {
                updatePadding(left = insets.left, right = insets.right)
                updateLayoutParams<FrameLayout.LayoutParams> {
                    @SuppressLint("PrivateResource")
                    bottomMargin = resources.getDimensionPixelSize(R.dimen.exo_styled_progress_margin_bottom) + insets.bottom
                }
            }
            windowInsets
        }

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