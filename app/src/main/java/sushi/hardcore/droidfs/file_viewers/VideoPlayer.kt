package sushi.hardcore.droidfs.file_viewers

import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.android.synthetic.main.activity_video_player.*
import sushi.hardcore.droidfs.R

class VideoPlayer: MediaPlayer() {
    override fun viewFile() {
        super.viewFile()
        setContentView(R.layout.activity_video_player)
    }

    override fun bindPlayer(player: SimpleExoPlayer) {
        video_player.player = player
    }
}