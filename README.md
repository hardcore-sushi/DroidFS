# DroidFS
An alternative way to use encrypted virtual filesystems on Android that uses its own internal file explorer instead of mounting volumes.
It currently supports [gocryptfs](https://github.com/rfjakob/gocryptfs) and [CryFS](https://github.com/cryfs/cryfs).

For mortals: An encrypted file manager for Android.

<p align="center">
<img src="https://forge.chapril.org/hardcoresushi/DroidFS/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" height="500">
<img src="https://forge.chapril.org/hardcoresushi/DroidFS/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" height="500">
<img src="https://forge.chapril.org/hardcoresushi/DroidFS/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" height="500">
</p>

# Disclaimer
DroidFS is provided "as is", without any warranty of any kind.
It shouldn't be considered as an absolute safe way to store files.
DroidFS cannot protect you from screen recording apps, keyloggers, apk backdooring, compromised root accesses, memory dumps etc.
Do not use this app with volumes containing sensitive data unless you know exactly what you are doing.

# Features
- Compatible with original encrypted volume implementations
- Internal support for video, audio, images, text and PDF files
- Built-in camera to take on-the-fly encrypted photos and videos
- Unlocking volumes using fingerprint authentication
- Volume auto-locking when the app goes in background

# Unsafe features
Some available features are considered risky and are therefore disabled by default. It is strongly recommended that you read the following documentation if you wish to activate one of these options.

<ul>
  <li><h4>Allow screenshots:</h4>
  Disable the secure flag of DroidFS activities. This will allow you to take screenshots from the app, but will also allow other apps to record the screen while using DroidFS.

  Note: apps with root access don't care about this flag: they can take screenshots or record the screen of any app without any permissions.
  </li>
  <li><h4>Allow opening files with other applications*:</h4>
  Decrypt and open file using external apps. These apps could save and send the files thus opened.
  </li>
  <li><h4>Allow exporting files:</h4>
  Decrypt and write file to disk (external storage). Any app with storage permissions could access exported files.
  </li>
  <li><h4>Allow sharing files via the android share menu*:</h4>
  Decrypt and share file with other apps. These apps could save and send the files thus shared.
  </li>
  <li><h4>Keep volume open when the app goes in background:</h4>
  Don't close the volume when you leave the app but keep running it in the background. Anyone going back to the activity could have access to the volume.
  </li>
  <li><h4>Allow saving password hash using fingerprint:</h4>
  Generate an AES-256 GCM key in the Android Keystore (protected by fingerprint authentication), then use it to encrypt the volume password hash and store it to the DroidFS internal storage. This require Android v6.0+. If your device is not encrypted, extracting the encryption key with physical access may be possible.
  </li>
</ul>
* Features requiring temporary writing of the plain file to disk (DroidFS internal storage). This file could be read by apps with root access or by physical access if your device is not encrypted.

# Download
<a href="https://f-droid.org/packages/sushi.hardcore.droidfs">
	<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="75">
</a>

You can download DroidFS from [F-Droid](https://f-droid.org/packages/sushi.hardcore.droidfs) or from the "Releases" section in this repository.

APKs available here are signed with my PGP key available on keyservers:

`gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys AFE384344A45E13A` \
Fingerprint: `B64E FE86 CEE1 D054 F082  1711 AFE3 8434 4A45 E13A` \
Email: `Hardcore Sushi <hardcore.sushi@disroot.org>`

To verify APKs, save the PGP-signed message to a file and run `gpg --verify <the file>`.  __Don't install any APK if the verification fails !__

If the signature is valid, you can compare the SHA256 checksums with:
```
sha256sum <APK file>
```
__Don't install the APK if the checksums don't match!__

F-Droid APKs should be signed with the F-Droid key. More details [here](https://f-droid.org/docs/Release_Channels_and_Signing_Keys).

# Permissions
DroidFS needs some permissions for certain features. However, you are free to deny them if you do not wish to use these features.

<ul>
  <li><h4>Read & write access to shared storage:</h4>
  Required to access volumes located on shared storage.
  </li>
  <li><h4>Biometric/Fingerprint hardware:</h4>
  Required to encrypt/decrypt password hashes using a fingerprint protected key.
  </li>
  <li><h4>Camera:</h4>
  Required to take encrypted photos or videos directly from the app.
  </li>
  <li><h4>Record audio:</h4>
  Required if you want sound on video recorded with DroidFS.
  </li>
</ul>

# Limitations
DroidFS works as a wrapper around modified versions of the original encrypted container implementations ([libgocryptfs](https://forge.chapril.org/hardcoresushi/libgocryptfs) and [libcryfs](https://forge.chapril.org/hardcoresushi/libcryfs)). These programs were designed to run on standard x86 Linux systems: they access the underlying file system with file paths and syscalls. However, on Android, you can't access files from other applications using file paths. Instead, one has to use the [ContentProvider](https://developer.android.com/guide/topics/providers/content-providers) API. Obviously, neither Gocryptfs nor CryFS support this API. As a result, DroidFS cannot open volumes provided by other applications (such as cloud storage clients), nor can it allow other applications to access encrypted volumes once opened.

Due to Android's storage restrictions, encrypted volumes located on SD cards must be placed under `/Android/data/sushi.hardcore.droidfs/` if you want DroidFS to be able to modify them.

# Building from source
You can follow the instructions in [BUILD.md](BUILD.md) to build DroidFS from source.

# Third party code
Thanks to these open source projects that DroidFS uses:

### Modified code:
- Encrypted filesystems (to protect your data):
    - [libgocryptfs](https://forge.chapril.org/hardcoresushi/libgocryptfs) (forked from [gocryptfs](https://github.com/rfjakob/gocryptfs))
    - [libcryfs](https://forge.chapril.org/hardcoresushi/libcryfs) (forked from [CryFS](https://github.com/cryfs/cryfs))
- [libpdfviewer](https://forge.chapril.org/hardcoresushi/libpdfviewer) (forked from [PdfViewer](https://github.com/GrapheneOS/PdfViewer)) to open PDF files
- [DoubleTapPlayerView](https://github.com/vkay94/DoubleTapPlayerView) to add double-click controls to the video player
### Borrowed code:
- [MaterialFiles](https://github.com/zhanghai/MaterialFiles) for Kotlin natural sorting implementation
### Libraries:
- [Glide](https://github.com/bumptech/glide) to display pictures
- [ExoPlayer](https://github.com/google/ExoPlayer) to play media files
