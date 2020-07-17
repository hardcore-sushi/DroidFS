package sushi.hardcore.droidfs.file_viewers

import android.widget.MediaController
import kotlinx.android.synthetic.main.activity_video_player.*
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredAlertDialog

class VideoPlayer: FileViewerActivity() {
    override fun viewFile() {
        val mc = MediaController(this)
        setContentView(R.layout.activity_video_player)
        mc.setAnchorView(video_player)
        video_player.setOnErrorListener { _, _, _ ->
            ColoredAlertDialog(this)
                .setTitle(R.string.error)
                .setMessage(getString(R.string.video_play_failed))
                .setCancelable(false)
                .setPositiveButton(R.string.ok) { _, _ -> finish() }
                .show()
            true
        }
        val tmpFileUri = exportFile(filePath)
        video_player.setVideoURI(tmpFileUri)
        video_player.setMediaController(mc)
        video_player.start()
    }
}