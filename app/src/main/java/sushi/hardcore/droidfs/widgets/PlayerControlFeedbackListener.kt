package sushi.hardcore.droidfs.widgets

enum class PlayerControl {
    BRIGHTNESS,
    VOLUME
}

interface PlayerControlFeedbackListener {
    fun onPlayerControlChanged(control: PlayerControl, value: Float)
    fun onSeekPreview(positionMs: Long, durationMs: Long) {}
    fun onPlayerControlFinished() {}
    fun onSeekPreviewFinished() {}
}
