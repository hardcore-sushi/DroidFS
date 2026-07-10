# DroidFS libmpv integration

This directory is the native dependency boundary for the DroidFS mpv player.

DroidFS does not use mpv-android as an app dependency or extract libraries from
release APKs. Instead, `build.sh` builds a pinned mpv-android native tree and
copies the resulting `libmpv.so` plus mpv public headers into this directory.

The Android app then builds its own `libdroidfs_mpv.so` JNI bridge against those
headers. The bridge registers a `droidfs://` read-only stream protocol so mpv
can seek and read directly from `EncryptedVolume` without exporting plaintext
media to temporary files.

## Build native mpv

Run this from a supported Linux or macOS environment:

```sh
cd app/mpv
./build.sh arm64
```

The upstream mpv-android build scripts explicitly do not support WSL. The
script is still kept here so the dependency is reproducible and pinned, but
native artifacts should be generated on a normal Linux/macOS host or CI runner.
By default the upstream checkout and SDK/NDK cache live outside the repo at
`$HOME/.cache/droidfs-mpv`; set `MPV_ANDROID_WORK_DIR` to override that.

Outputs used by Gradle:

- `jniLibs/arm64-v8a/libmpv.so`
- `include/mpv/*.h`

Set `MPV_ANDROID_TAG` to test another upstream release tag.
