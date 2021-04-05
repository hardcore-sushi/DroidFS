package sushi.hardcore.droidfs.file_viewers

import android.content.pm.ActivityInfo
import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.android.synthetic.main.activity_video_player.*
import sushi.hardcore.droidfs.R


class VideoPlayer: MediaPlayer() {
    private var firstPlay = true
    private val autoFit by lazy {
        sharedPrefs.getBoolean("autoFit", false)
    }
    override fun viewFile() {
        setContentView(R.layout.activity_video_player)
        super.viewFile()
    }

    override fun bindPlayer(player: SimpleExoPlayer) {
        video_player.player = player
    }

    override fun getFileType(): String {
        return "video"
    }

    override fun onPlayerReady() {
        if (firstPlay && autoFit) {
            requestedOrientation = if (video_player.width <  video_player.height) ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            firstPlay = false
        }
    }
}