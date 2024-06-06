# Introduction
DroidFS relies on modified versions of the original encrypted filesystems programs to open volumes. [CryFS](https://github.com/cryfs/cryfs) is written in C++ while [gocryptfs](https://github.com/rfjakob/gocryptfs) is written in [Go](https://golang.org). Thus, building DroidFS requires the compilation of native code. However, for the sake of simplicity, the application has been designed in a modular way: you can build a version of DroidFS that supports both Gocryptfs and CryFS, or only one of the two.

Moreover, DroidFS aims to be accessible to as many people as possible. If you encounter any problems or need help with the build, feel free to open an issue, a discussion, or contact me (currently the main developer) by [email](mailto:gh@arkensys.dedyn.io) or on [Matrix](https://matrix.org): @hardcoresushi:matrix.underworld.fr

# Setup

The following two steps assume you're using a Debian-based Linux distribution. Package names might be similar for other distributions. Don't hesitate to ask if you're having trouble with this.

Install required packages:
```
$ sudo apt-get install openjdk-17-jdk-headless build-essential pkg-config git gnupg2 wget apksigner npm
```
You also need to manually install the [Android SDK](https://developer.android.com/studio/index.html#command-tools) and the [Android Native Development Kit (NDK)](https://github.com/android/ndk/wiki/Unsupported-Downloads#r25c) version `25.2.9519653` (r25c). libcryfs cannot be built with newer NDK versions at the moment due to compatibility issues with [boost](https://www.boost.org). If you succeed in building it with a more recent version of NDK, please report it.

If you want a support for Gocryptfs volumes, you need to install [Go](https://golang.org/doc/install):
```
$ sudo apt-get install golang-go
```
The code should be authenticated before being built. To verify the signatures, you will need my PGP key:
```
$ gpg --keyserver hkps://keyserver.ubuntu.com --recv-keys AFE384344A45E13A
```
Fingerprint: `B64E FE86 CEE1 D054 F082  1711 AFE3 8434 4A45 E13A` \
Email: `Hardcore Sushi <hardcore.sushi@disroot.org>`

# Download sources
Download DroidFS source code:
```
$ git clone --depth=1 https://forge.chapril.org/hardcoresushi/DroidFS.git
```
Verify sources:
```
$ cd DroidFS
$ git verify-commit HEAD
```
__Don't continue if the verification fails!__

Initialize submodules:
```
$ git submodule update --depth=1 --init
```
[FFmpeg](https://ffmpeg.org) is needed to record encrypted video:
```
$ cd app/ffmpeg
$ git clone --depth=1 https://git.ffmpeg.org/ffmpeg.git
```
If you want Gocryptfs support, you need to download OpenSSL:
```
$ cd ../libgocryptfs
$ wget https://openssl.org/source/openssl-3.3.1.tar.gz
```
Verify OpenSSL signature:
```
$ https://openssl.org/source/openssl-3.3.1.tar.gz.asc
$ gpg --verify openssl-3.3.1.tar.gz.asc openssl-3.3.1.tar.gz
```
Continue **ONLY** if the signature is **VALID**.
```
$ tar -xzf openssl-3.3.1.tar.gz
```
If you want CryFS support, initialize libcryfs:
```
$ cd app/libcryfs
$ git submodule update --depth=1 --init
```

# Build
Retrieve your Android NDK installation path, usually something like `/home/\<user\>/Android/SDK/ndk/\<NDK version\>`. Then, make it available in your shell:
```
$ export ANDROID_NDK_HOME="<your ndk path>"
```
Start by compiling FFmpeg:
```
$ cd app/ffmpeg
$ ./build.sh ffmpeg
```
## libgocryptfs
This step is only required if you want Gocryptfs support.
```
$ cd app/libgocryptfs
$ ANDROID_NDK_ROOT="$ANDROID_NDK_HOME" OPENSSL_PATH="./openssl-3.3.1" ./build.sh
```
## Compile APKs
Gradle build libgocryptfs and libcryfs by default.

To build DroidFS without Gocryptfs support, run:
```
$ ./gradlew assembleRelease -PdisableGocryptfs=true
```
To build DroidFS without CryFS support, run:
```
$ ./gradlew assembleRelease -PdisableCryFS=true
```
If you want to build DroidFS with support for both Gocryptfs and CryFS, just run:
```
$ ./gradlew assembleRelease
```

# Sign APKs
If the build succeeds, you will find the unsigned APKs in `app/build/outputs/apk/release/`. These APKs need to be signed in order to be installed on an Android device.

If you don't already have a keystore, you can create a new one by running:
```
$ keytool -genkey -keystore <output file> -alias <key alias> -keyalg EC -validity 10000
```
Then, sign the APK with:
```
$ apksigner sign --out droidfs.apk -v --ks <keystore> app/build/outputs/apk/release/<unsigned apk file>
```
Now you can install `droidfs.apk` on your device.
