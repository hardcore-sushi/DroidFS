package sushi.hardcore.droidfs.file_viewers

import com.google.android.exoplayer2.SimpleExoPlayer
import sushi.hardcore.droidfs.databinding.ActivityAudioPlayerBinding
import java.io.File

class AudioPlayer: MediaPlayer(){
    private lateinit var binding: ActivityAudioPlayerBinding

    override fun viewFile() {
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.viewFile()
        refreshFileName()
    }

    override fun getFileType(): String {
        return "audio"
    }

    override fun bindPlayer(player: SimpleExoPlayer) {
        binding.audioController.player = player
    }

    override fun onPlaylistIndexChanged() {
        refreshFileName()
    }

    private fun refreshFileName() {
        val filename = File(filePath).name
        val pos = filename.lastIndexOf('.')
        binding.musicTitle.text = if (pos != -1){
            filename.substring(0,pos)
        } else {
            filename
        }
    }
}