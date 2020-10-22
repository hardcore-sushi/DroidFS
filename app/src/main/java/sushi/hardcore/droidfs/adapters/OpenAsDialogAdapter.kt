package sushi.hardcore.droidfs.adapters

import android.content.Context
import sushi.hardcore.droidfs.R

class OpenAsDialogAdapter(context: Context, showOpenWithExternalApp: Boolean) : IconTextDialogAdapter(context) {
    private val openAsItems: MutableList<List<Any>> = mutableListOf(
        listOf("image", R.string.image, R.drawable.icon_file_image),
        listOf("video", R.string.video, R.drawable.icon_file_video),
        listOf("audio", R.string.audio, R.drawable.icon_file_audio),
        listOf("text", R.string.text, R.drawable.icon_file_text)
    )
    init {
        if (showOpenWithExternalApp){
            openAsItems.add(listOf("external", R.string.external_open, R.drawable.icon_open_in_new))
        }
        items = openAsItems
    }
}