-dontobfuscate
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
# Keep all JNI native methods and their classes
-keepclasseswithmembernames class * {
    native <methods>;
}
# Required for Parcelable CREATOR fields to not be removed by R8
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}