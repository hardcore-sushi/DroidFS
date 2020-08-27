# DroidFS
DroidFS is an implementation of encrypted overlay filesystems for Android.
It allows you to store files and folder in encrypted virtual volumes so that you can share them, store them in the cloud or simply access them more securely.
It currently only supports [gocryptfs](https://github.com/rfjakob/gocryptfs) but support for [CryFS](https://github.com/cryfs/cryfs) is expected to be added soon.<br>

<p align="center">
<img src="https://raw.githubusercontent.com/hardcore-sushi/DroidFS/master/Screenshots/1.jpg" height="500">
<img src="https://raw.githubusercontent.com/hardcore-sushi/DroidFS/master/Screenshots/2.jpg" height="500">
<img src="https://raw.githubusercontent.com/hardcore-sushi/DroidFS/master/Screenshots/3.jpg" height="500">
</p>

# Disclamer
DroidFS is provided "as is", without any warranty of any kind.
It shouldn't be considered an absolute safe way to store files.
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
  <li><h4>Allow opening files with other applications:</h4>
  Decrypt and open file using external apps. This require writing the plain file to disk (DroidFS internal storage).
  </li>
  <li><h4>Allow exporting files:</h4>
  Decrypt and write file to disk (external storage). Any app with storage permissions can access exported files.
  </li>
  <li><h4>Allow sharing files via the android share menu:</h4>
  Decrypt and share file with other apps. This require writing the plain file to disk (DroidFS internal storage).
  </li>
  <li><h4>Allow saving password hash using fingerprint:</h4>
  Generate an AES-256 GCM key in the Android Keystore (protected by fingerprint authentication), then use it to encrypt the volume password hash and store it to the DroidFS internal storage. This require Android v6.0+.
  </li>
</ul>

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
  Needed to take photos directly from DroidFS to import them securely.
  </li>
</ul>

# Download
You can download the latest version [here](https://github.com/hardcore-sushi/DroidFS/releases).

# Build
Most of the original gocryptfs code was used as is (written in Go) and compiled to native code. That's why you need [Go](https://golang.org) and the [Android Native Development Kit (NDK)](https://developer.android.com/ndk/) to build DroidFS from source.

#### Install Requirements
- [Android Studio](https://developer.android.com/studio/)
- [Android NDK and CMake](https://developer.android.com/studio/projects/install-ndk)
- [Go](https://golang.org/doc/install)

#### Download Sources
```
$ git clone https://github.com/hardcore-sushi/DroidFS.git
```
Gocryptfs need openssl to work:
```
$ cd DroidFS/app/libgocryptfs
$ wget -qO - https://www.openssl.org/source/openssl-1.1.1g.tar.gz | tar -xvzf -
```

#### Build
First, we need to build libgocryptfs.<br>
Retrieve your Android NDK installation path, usually someting like "\<Android SDK path\>/ndk/\<NDK version\>".
```
$ cd DroidFS/app/libgocryptfs
$ env ANDROID_NDK_HOME="<your ndk path>" OPENSSL_PATH="./openssl-1.1.1g" ./build.sh
 ```
Then, open the DroidFS project with Android Studio.<br>
If a device (virtual or physical) is connected, just click on "Run".<br>
If you want to generate a signed APK, you can follow this [post](https://stackoverflow.com/a/28938286).

# Third party code
Thanks to these open source projects that DroidFS uses:

### Modified code:
- [gocryptfs](https://github.com/rfjakob/gocryptfs) to encrypt your data
### Libraries:
- [Cyanea](https://github.com/jaredrummler/Cyanea) to customize UI
- [Glide](https://github.com/bumptech/glide/) to display pictures
- [ExoPlayer](https://github.com/google/ExoPlayer) to play media files
- [CameraView](https://github.com/natario1/CameraView) to take photos
