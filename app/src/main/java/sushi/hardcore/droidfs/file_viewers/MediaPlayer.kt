package sushi.hardcore.droidfs.file_viewers

import android.view.WindowManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.video.VideoSize
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

abstract class MediaPlayer: FileViewerActivity() {
    private lateinit var player: ExoPlayer

    override fun viewFile() {
        supportActionBar?.hide()
        initializePlayer()
        refreshFileName()
    }

    abstract fun bindPlayer(player: ExoPlayer)
    abstract fun onNewFileName(fileName: String)
    protected open fun onVideoSizeChanged(width: Int, height: Int) {}

    private fun createMediaSource(filePath: String): MediaSource {
        val dataSourceFactory = EncryptedVolumeDataSource.Factory(encryptedVolume, filePath)
        return ProgressiveMediaSource.Factory(dataSourceFactory, DefaultExtractorsFactory())
            .createMediaSource(MediaItem.fromUri(Constants.FAKE_URI))
    }

    private fun initializePlayer(){
        player = ExoPlayer.Builder(this).setSeekForwardIncrementMs(5000).build()
        bindPlayer(player)
        createPlaylist()
        for (e in mappedPlaylist) {
            player.addMediaSource(createMediaSource(e.fullPath))
        }
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.seekToDefaultPosition(currentPlaylistIndex)
        player.playWhenReady = true
        player.addListener(object : Player.Listener{
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                onVideoSizeChanged(videoSize.width, videoSize.height)
            }
            override fun onPlayerError(error: PlaybackException) {
                CustomAlertDialogBuilder(this@MediaPlayer, theme)
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player.repeatMode != Player.REPEAT_MODE_ONE) {
                    playlistNext(player.currentMediaItemIndex == (currentPlaylistIndex + 1) % mappedPlaylist.size)
                    refreshFileName()
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

    private fun refreshFileName() {
        onNewFileName(File(filePath).name)
    }
}