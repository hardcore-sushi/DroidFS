package sushi.hardcore.droidfs.file_viewers

import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import kotlinx.coroutines.launch
import sushi.hardcore.droidfs.Constants
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.CustomAlertDialogBuilder
import java.io.File

@OptIn(UnstableApi::class)
abstract class MediaPlayer(fullscreen: Boolean): FileViewerActivity(fullscreen) {
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
        player.addMediaSource(createMediaSource(fileViewerViewModel.filePath!!))
        lifecycleScope.launch {
            createPlaylist()
            fileViewerViewModel.playlist.forEachIndexed { index, e ->
                if (index != fileViewerViewModel.currentPlaylistIndex) {
                    player.addMediaSource(index, createMediaSource(e.fullPath))
                }
            }
        }
        player.repeatMode = sharedPrefs.getInt("playerRepeatMode", Player.REPEAT_MODE_ALL)
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
                if (player.repeatMode != Player.REPEAT_MODE_ONE && fileViewerViewModel.currentPlaylistIndex != -1) {
                    lifecycleScope.launch {
                        playlistNext(player.currentMediaItemIndex == (fileViewerViewModel.currentPlaylistIndex + 1) % player.mediaItemCount)
                        refreshFileName()
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                with (sharedPrefs.edit()) {
                    putInt("playerRepeatMode", repeatMode)
                    apply()
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
        onNewFileName(File(fileViewerViewModel.filePath!!).name)
    }
}