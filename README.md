# DroidFS
DroidFS is an implementation of encrypted overlay filesystems for Android.
It allows you to store files and folder in encrypted virtual volumes so that you can share them, store them in the cloud or simply access them more securely.
It currently only supports [gocryptfs](https://github.com/rfjakob/gocryptfs) but support for [CryFS](https://github.com/cryfs/cryfs) is expected to be added soon.<br>
DroidFS require Android API level 21+ (Android Lollipop).

# Disclamer
DroidFS is provided "as is", without any warranty of any kind.
It shouldn't be considered an absolute safe way to store files.
DroidFS cannot protect you from screen recording apps, keyloggers, apk backdooring, compromised root accesses, memory dumps etc.
Do not use this app with volumes containing sensitive data unless you know exactly what you are doing.

# Unsafe features

DroidFS allows you to enable/disable unsafe features to fit your needs between security and comfort.
It is strongly recommended to read the documentation of a feature before enabling it.

#### Allow screenshots:
Disable the secure flag of DroidFS activities. This will allow you to take screenshots from the app, but will also allow other apps to record the screen while using DroidFS.
Note: apps with root access don't care about this flag: they can take screenshots or record the screen of any app without any permissions.

#### Allow opening files with other applications:
Decrypt and open file using external apps. This require writing the plain file to disk (DroidFS internal storage).

#### Allow exporting files:
Decrypt and write file to disk (external storage). Any app with storage permissions can access exported files.

#### Allow sharing files via the android share menu:
Decrypt and share file with other apps. This require writing the plain file to disk (DroidFS internal storage).

#### Allow saving password hash using fingerprint:
Generate an AES-256 GCM key in the Android Keystore (protected by fingerprint authentication), then use it to encrypt the volume password hash and store it to the DroidFS internal storage. This require Android v6.0+

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
