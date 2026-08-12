-dontobfuscate
# Suppress warnings for FingerprintManager classes that are only available on API 23+
-dontwarn android.hardware.fingerprint.FingerprintManager
-dontwarn android.hardware.fingerprint.FingerprintManager$AuthenticationCallback
-dontwarn android.hardware.fingerprint.FingerprintManager$CryptoObject
# Suppress warnings for CameraX extension classes that are only implemented by
# device vendors at runtime (not shipped in the library)
-dontwarn androidx.camera.extensions.impl.**

-keep class sushi.hardcore.droidfs.SettingsActivity$**
-keep class sushi.hardcore.droidfs.explorers.ExplorerElement
-keepclassmembers class sushi.hardcore.droidfs.explorers.ExplorerElement {
    static sushi.hardcore.droidfs.explorers.ExplorerElement new(...);
}
-keepclassmembers class sushi.hardcore.droidfs.video_recording.FFmpegMuxer {
    void writePacket(byte[]);
    void seek(long);
}
-keep class app.grapheneos.pdfviewer.PdfViewer$Channel { *; }
# Keep all JNI native methods and their classes
-keepclasseswithmembernames class * {
    native <methods>;
}
# ExifInterface serializers are invoked via reflection to circumvent on disk plain-text write
-keepclassmembers class androidx.exifinterface.media.ExifInterface {
    int IMAGE_TYPE_JPEG;
    int IMAGE_TYPE_PNG;
    int IMAGE_TYPE_WEBP;
    int mMimeType;
    private void saveJpegAttributes(java.io.InputStream, java.io.OutputStream);
    private void savePngAttributes(java.io.InputStream, java.io.OutputStream);
    private void saveWebpAttributes(java.io.InputStream, java.io.OutputStream);
}
# Required for Parcelable CREATOR fields to not be removed by R8
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}