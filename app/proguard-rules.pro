-keepattributes SourceFile,LineNumberTable

-keep class sushi.hardcore.droidfs.SettingsActivity$**
-keep class sushi.hardcore.droidfs.explorers.ExplorerElement
-keepclassmembers class sushi.hardcore.droidfs.explorers.ExplorerElement {
    static sushi.hardcore.droidfs.explorers.ExplorerElement new(...);
}
-keepclassmembers class sushi.hardcore.droidfs.video_recording.FFmpegMuxer {
    void writePacket(byte[]);
    void seek(long);
}
# Required for Intent.getParcelableExtra() to work on Android 13
-keep class sushi.hardcore.droidfs.VolumeData {
    public int describeContents();
}
-keep class sushi.hardcore.droidfs.VolumeData$* {
    static public android.os.Parcelable$Creator CREATOR;
}
