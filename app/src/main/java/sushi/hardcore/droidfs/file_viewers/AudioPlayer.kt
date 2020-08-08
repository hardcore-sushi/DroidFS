package sushi.hardcore.droidfs.file_viewers

import com.google.android.exoplayer2.SimpleExoPlayer
import kotlinx.android.synthetic.main.activity_audio_player.*
import sushi.hardcore.droidfs.R
import java.io.File

class AudioPlayer: MediaPlayer(){
    override fun viewFile() {
        setContentView(R.layout.activity_audio_player)
        super.viewFile()
        val filename = File(filePath).name
        val pos = filename.lastIndexOf('.')
        music_title.text = if (pos != -1){
            filename.substring(0,pos)
        } else {
            filename
        }
    }

    override fun bindPlayer(player: SimpleExoPlayer) {
        audio_controller.player = player
    }
}