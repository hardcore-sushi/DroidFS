# DroidFS
DroidFS is an alternative way to use encrypted overlay filesystems on Android that uses its own internal file explorer instead of mounting virtual volumes.
It currently only works with [gocryptfs](https://github.com/rfjakob/gocryptfs) but support for [CryFS](https://github.com/cryfs/cryfs) could be added in the future.

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

# Unsafe features
DroidFS allows you to enable/disable unsafe features to fit your needs between security and comfort.
It is strongly recommended to read the documentation of a feature before enabling it.

<ul>
  <li><h4>Allow screenshots:</h4>
  Disable the secure flag of DroidFS activities. This will allow you to take screenshots from the app, but will also allow other apps to record the screen while using DroidFS.
  Note: apps with root access don't care about this flag: they can take screenshots or record the screen of any app without any permissions.
  </li>
  <li><h4>Allow opening files with other applications *:</h4>
  Decrypt and open file using external apps. These apps could save and send the files thus opened.
  </li>
  <li><h4>Allow exporting files:</h4>
  Decrypt and write file to disk (external storage). Any app with storage permissions could access exported files.
  </li>
  <li><h4>Allow sharing files via the android share menu *:</h4>
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

You can download DroidFS from [F-Droid](https://f-droid.org/packages/sushi.hardcore.droidfs) or from the "Releases" section in the repo. 

APKs available here are signed with my PGP key available on keyservers:

`gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys 007F84120107191E` \
Fingerprint: `BD56 2147 9E7B 74D3 6A40  5BE8 007F 8412 0107 191E` \
Email: `Hardcore Sushi <hardcore.sushi@disroot.org>`

To verify APKs, save the PGP-signed message to a file and run `gpg --verify <the file>`.  __Don't install any APK if the verification fails !__

If the signature is valid, you can compare the SHA256 checksums with:
```
sha256sum <APK file>
```
__Don't install the APK if the checksums don't match!__

F-Droid APKs should be signed with the F-Droid key. More details [here](https://f-droid.org/docs/Release_Channels_and_Signing_Keys).

# Permissions
DroidFS need some permissions to work properly. Here is why:

<ul>
  <li><h4>Read & write access to shared storage:</h4>
  Required for creating, opening and modifying volumes and for importing/exporting files to/from volumes.
  </li>
  <li><h4>Biometric/Fingerprint hardware:</h4>
  Required to encrypt/decrypt password hashes using a fingerprint protected key.
  </li>
  <li><h4>Camera:</h4>
  Needed to take photos & videos directly encrypted inside DroidFS. You can deny this permission if you don't want to use it.
  </li>
  <li><h4>Record audio:</h4>
  Required if you want sound on video recorded with DroidFS.
  </li>
</ul>

# Limitations
DroidFS use some parts of the original gocryptfs code, which is designed to run on Linux x86 systems: it accesses the underlying file system with file paths and syscalls. However in Android, you can't access other apps files with file paths. Instead, you must use the [ContentProvider](https://developer.android.com/guide/topics/providers/content-providers) API. And obviously, the original gocryptfs code doesn't work with this API. This is why DroidFS can't open volumes provided by other applications, such as cloud storage clients. You can only use DroidFS with volumes located on shared storage or in the app's internal storage (hidden volumes). External storage such as SD cards are only supported in read-only access for now.

# Build
Most of the original gocryptfs code was used as is (written in Go) and compiled to native code. That's why you need [Go](https://golang.org) and the [Android Native Development Kit (NDK)](https://developer.android.com/ndk/) to build DroidFS from source.

#### Install dependencies
On debian:
```
$ sudo apt-get install build-essential pkg-config libssl-dev
```
Install [Go](https://golang.org/doc/install):
```
$ sudo apt-get install golang-go
```
You also need to install the Android SDK build tools and the [Android NDK](https://developer.android.com/studio/projects/install-ndk).

#### Download Sources
```
$ git clone --recurse-submodules https://github.com/hardcore-sushi/DroidFS.git
$ cd DroidFS
```
[libgocryptfs](https://forge.chapril.org/hardcoresushi/libgocryptfs) needs OpenSSL:
```
$ cd app/libgocryptfs
$ wget https://www.openssl.org/source/openssl-1.1.1m.tar.gz
```
Verify OpenSSL signature:
```
$ wget https://www.openssl.org/source/openssl-1.1.1m.tar.gz.asc
$ gpg --verify openssl-1.1.1m.tar.gz.asc openssl-1.1.1m.tar.gz
```
Continue **ONLY** if the signature is **VALID**.
```
$ tar -xvzf openssl-1.1.1m.tar.gz
```
DroidFS also need [FFmpeg](https://ffmpeg.org) to record encrypted video:
```
$ cd app/ffmpeg
$ git clone --depth=1 https://git.ffmpeg.org/ffmpeg.git
```

#### Generate a keystore
APKs must be signed to be installed on an Android device. If you don't already have a keystore, you can generate one by running:
```
$ keytool -genkey -keystore <output file> -alias <key alias> -keyalg EC -validity 10000
```

#### Build
Retrieve your Android NDK installation path, usually something like "/home/\<user\>/Android/SDK/ndk/\<NDK version\>". Now you can build libgocryptfs:
```
$ cd DroidFS/app/libgocryptfs
$ env ANDROID_NDK_HOME="<your ndk path>" OPENSSL_PATH="./openssl-1.1.1m" ./build.sh
 ```
Then FFmpeg:
```
$ cd app/ffmpeg
$ env ANDROID_NDK_HOME="<your ndk path>" ./build.sh ffmpeg
```
Finally, compile the app:
```
$ ./gradlew assembleRelease
```
If the build succeeds, you will find the unsigned APKs in `app/build/outputs/apk/release/`. You need to sign them in order to install the app:
```
$ apksigner sign --out droidfs.apk -v --ks <keystore> app/build/outputs/apk/release/<unsigned apk file>
```
Now you can install `droidfs.apk` on your device.

# Third party code
Thanks to these open source projects that DroidFS uses:

### Modified code:
- [gocryptfs](https://github.com/rfjakob/gocryptfs) to encrypt your data
- [DoubleTapPlayerView](https://github.com/vkay94/DoubleTapPlayerView) to add double-click controls to the video player
### Borrowed code:
- [MaterialFiles](https://github.com/zhanghai/MaterialFiles) for kotlin natural sorting implementation
### Libraries:
- [Glide](https://github.com/bumptech/glide/) to display pictures
- [ExoPlayer](https://github.com/google/ExoPlayer) to play media files
