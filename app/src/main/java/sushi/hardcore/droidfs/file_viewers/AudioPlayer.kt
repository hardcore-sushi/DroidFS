package sushi.hardcore.droidfs.file_viewers

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import sushi.hardcore.droidfs.databinding.ActivityAudioPlayerBinding

@OptIn(UnstableApi::class)
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