package sushi.hardcore.droidfs.file_viewers

import android.view.WindowManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.flac.FlacExtractor
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor
import com.google.android.exoplayer2.extractor.ogg.OggExtractor
import com.google.android.exoplayer2.extractor.wav.WavExtractor
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

abstract class MediaPlayer: FileViewerActivity() {
    private lateinit var player: SimpleExoPlayer

    override fun viewFile() {
        initializePlayer()
    }

    abstract fun bindPlayer(player: SimpleExoPlayer)
    protected open fun onPlaylistIndexChanged() {}
    protected open fun onPlayerReady() {}

    private fun createMediaSource(filePath: String): MediaSource {
        val dataSourceFactory = GocryptfsDataSource.Factory(gocryptfsVolume, filePath)
        return ProgressiveMediaSource.Factory(dataSourceFactory, ExtractorsFactory { arrayOf(
                MatroskaExtractor(),
                Mp4Extractor(),
                Mp3Extractor(),
                OggExtractor(),
                WavExtractor(),
                FlacExtractor()
        ) }).createMediaSource(MediaItem.fromUri(ConstValues.fakeUri))
    }

    private fun initializePlayer(){
        player = SimpleExoPlayer.Builder(this).build()
        bindPlayer(player)
        createPlaylist()
        for (e in mappedPlaylist) {
            player.addMediaSource(createMediaSource(e.fullPath))
        }
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.seekToDefaultPosition(currentPlaylistIndex)
        player.playWhenReady = true
        player.addListener(object : Player.Listener{
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    onPlayerReady()
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                ColoredAlertDialogBuilder(this@MediaPlayer)
                        .setTitle(R.string.error)
                        .setMessage(getString(R.string.playing_failed, error.errorCodeName))
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> goBackToExplorer()}
                        .show()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying){
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            override fun onPositionDiscontinuity(reason: Int) {
                if (player.currentWindowIndex != currentPlaylistIndex) {
                    playlistNext(player.currentWindowIndex == (currentPlaylistIndex+1)%mappedPlaylist.size)
                    onPlaylistIndexChanged()
                }
            }
        })
        player.prepare()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            player.release()
        }
    }
}