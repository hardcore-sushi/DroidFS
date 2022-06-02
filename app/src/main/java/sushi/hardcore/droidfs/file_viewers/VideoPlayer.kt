package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.widget.RelativeLayout
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
        binding.videoPlayer.doubleTapOverlay = binding.doubleTapOverlay
        binding.videoPlayer.setControllerVisibilityListener { visibility ->
            binding.topBar.visibility = visibility

            if (visibility == View.VISIBLE) {
                showPartialSystemUi()
            }
            else {
                hideSystemUi()
            }
        }
        binding.rotateButton.setOnClickListener {
            requestedOrientation =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                }
        }

        setTopBarLayoutSize(getCurrentScreenOrientation())

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        setTopBarLayoutSize(newConfig.orientation)
    }

    private fun setTopBarLayoutSize(screenOrientation: Int) {
        val statusBarHeight = getStatusBarHeight()
        if (screenOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            val navigationBarHeight = getNavigationBarHeight()
            binding.topBar.layoutParams = createTopBarLayoutParams(statusBarHeight, navigationBarHeight, navigationBarHeight)
        } else {
            binding.topBar.layoutParams = createTopBarLayoutParams(statusBarHeight, 20, 20)
        }
    }

    private fun getNavigationBarHeight() : Int {
        return getIdentifierDimensionPixelSize("navigation_bar_height")
    }

    private fun getStatusBarHeight() : Int {
        return getIdentifierDimensionPixelSize("status_bar_height")
    }

    private fun getIdentifierDimensionPixelSize(name: String) : Int {
        return resources.getDimensionPixelSize(
            resources.getIdentifier(
                name,
                "dimen",
                "android"
            )
        )
    }

    private fun createTopBarLayoutParams(topMargin: Int, leftMargin: Int, rightMargin: Int) : RelativeLayout.LayoutParams {
        val layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.topMargin = topMargin
        layoutParams.leftMargin = leftMargin
        layoutParams.rightMargin = rightMargin

        return layoutParams
    }

    private fun getCurrentScreenOrientation() : Int {
        return resources.configuration.orientation
    }
}