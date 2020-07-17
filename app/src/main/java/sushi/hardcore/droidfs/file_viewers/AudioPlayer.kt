package sushi.hardcore.droidfs.file_viewers

import android.media.MediaPlayer
import android.os.Handler
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_audio_player.*
import sushi.hardcore.droidfs.ConstValues
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog
import java.io.File
import java.io.IOException

class AudioPlayer: FileViewerActivity(){
    private lateinit var player: MediaPlayer
    private var isPrepared = false
    override fun viewFile() {
        setContentView(R.layout.activity_audio_player)
        val filename = File(filePath).name
        val pos = filename.lastIndexOf('.')
        music_title.text = if (pos != -1){
            filename.substring(0,pos)
        } else {
            filename
        }
        val tmpFileUri = exportFile(filePath)
        tmpFileUri?.let {
            player = MediaPlayer()
            player.setDataSource(this, tmpFileUri)
            try {
                player.prepare()
                isPrepared = true
            } catch (e: IOException){
                ColoredAlertDialog(this)
                    .setTitle(R.string.error)
                    .setMessage(getString(R.string.media_player_prepare_failed))
                    .setCancelable(false)
                    .setPositiveButton(R.string.ok) { _, _ -> finish() }
                    .show()
            }
            if (isPrepared){
                player.isLooping = true
                button_pause.setOnClickListener {
                    if (player.isPlaying) {
                        player.pause()
                        button_pause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.icon_play))
                    } else {
                        player.start()
                        button_pause.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.icon_pause))
                    }
                }
                button_stop.setOnClickListener { finish() }
                seekbar.max = player.duration / ConstValues.seek_bar_inc
                val handler = Handler()
                runOnUiThread(object : Runnable {
                    override fun run() {
                        if (isPrepared) {
                            seekbar.progress = player.currentPosition / ConstValues.seek_bar_inc
                        }
                        handler.postDelayed(this, ConstValues.seek_bar_inc.toLong())
                    }
                })
                seekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        if (::player.isInitialized && fromUser) {
                            player.seekTo(progress * ConstValues.seek_bar_inc)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar) {}
                })
                player.start()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            if (player.isPlaying) {
                player.stop()
            }
            isPrepared = false
            player.release()
        }
    }
}