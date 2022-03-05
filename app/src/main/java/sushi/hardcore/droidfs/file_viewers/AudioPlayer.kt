package sushi.hardcore.droidfs.file_viewers

import com.google.android.exoplayer2.ExoPlayer
import sushi.hardcore.droidfs.databinding.ActivityAudioPlayerBinding

class AudioPlayer: MediaPlayer(){
    private lateinit var binding: ActivityAudioPlayerBinding

    override fun viewFile() {
        binding = ActivityAudioPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        super.viewFile()
    }

    override fun getFileType(): String {
        return "audio"
    }

    override fun bindPlayer(player: ExoPlayer) {
        binding.audioController.player = player
    }

    override fun onNewFileName(fileName: String) {
        binding.musicTitle.text = fileName
    }
}