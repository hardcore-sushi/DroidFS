package sushi.hardcore.droidfs.file_viewers

import androidx.appcompat.app.AlertDialog
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.LoopingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog

abstract class MediaPlayer: FileViewerActivity() {
    private lateinit var player: SimpleExoPlayer
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private lateinit var errorDialog: AlertDialog.Builder

    override fun viewFile() {
        errorDialog = ColoredAlertDialog(this)
            .setTitle(R.string.error)
            .setMessage(R.string.playing_failed)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { _, _ -> finish() }
    }

    abstract fun bindPlayer(player: SimpleExoPlayer)

    private fun initializePlayer(){
        player = SimpleExoPlayer.Builder(this).build()
        bindPlayer(player)
        val dataSourceFactory = GocryptfsDataSource.Factory(gocryptfsVolume, filePath)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(ConstValues.fakeUri)
        player.seekTo(currentWindow, playbackPosition)
        player.playWhenReady = true
        player.addListener(object : Player.EventListener{
            override fun onPlayerError(error: ExoPlaybackException) {
                if (error.type == ExoPlaybackException.TYPE_SOURCE){
                    errorDialog.show()
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

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        initializePlayer()
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }
}