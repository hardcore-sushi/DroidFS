package sushi.hardcore.droidfs.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.marginEnd
import androidx.core.view.setPadding
import sushi.hardcore.droidfs.R
import sushi.hardcore.droidfs.widgets.ColoredImageView

class OpenAsDialogAdapter(context: Context) : IconTextDialogAdapter(context) {
    private val openAsItems = listOf(
        listOf("image", R.string.image, R.drawable.icon_file_image),
        listOf("video", R.string.video, R.drawable.icon_file_video),
        listOf("audio", R.string.audio, R.drawable.icon_file_audio),
        listOf("text", R.string.text, R.drawable.icon_file_text)
    )
    init {
        items = openAsItems
    }
}