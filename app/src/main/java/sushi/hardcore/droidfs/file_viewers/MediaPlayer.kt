package sushi.hardcore.droidfs.file_viewers

import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.extractor.flv.FlvExtractor
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor
import com.google.android.exoplayer2.extractor.ogg.OggExtractor
import com.google.android.exoplayer2.extractor.wav.WavExtractor
import com.google.android.exoplayer2.source.LoopingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredAlertDialogBuilder

abstract class MediaPlayer: FileViewerActivity() {
    private lateinit var player: SimpleExoPlayer
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private lateinit var errorDialog: AlertDialog

    override fun viewFile() {
        errorDialog = ColoredAlertDialogBuilder(this)
            .setTitle(R.string.error)
            .setMessage(R.string.playing_failed)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> goBackToExplorer()}
            .create()
        hideSystemUi()
        initializePlayer()
    }

    abstract fun bindPlayer(player: SimpleExoPlayer)

    private fun initializePlayer(){
        player = SimpleExoPlayer.Builder(this).build()
        bindPlayer(player)
        val dataSourceFactory = GocryptfsDataSource.Factory(gocryptfsVolume, filePath)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory, ExtractorsFactory {
            arrayOf(
                MatroskaExtractor(),
                Mp4Extractor(),
                Mp3Extractor(),
                FlvExtractor(),
                OggExtractor(),
                WavExtractor()
            )
        }).createMediaSource(ConstValues.fakeUri)
        player.seekTo(currentWindow, playbackPosition)
        player.playWhenReady = true
        player.addListener(object : Player.EventListener{
            override fun onPlayerError(error: ExoPlaybackException) {
                if (error.type == ExoPlaybackException.TYPE_SOURCE){
                    errorDialog.show()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying){
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        })
        player.prepare(LoopingMediaSource(mediaSource), false, false)
    }

    private fun releasePlayer(){
        if (::player.isInitialized) {
            playbackPosition = player.currentPosition
            currentWindow = player.currentWindowIndex
            player.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}