#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

tag="${MPV_ANDROID_TAG:-2026-04-25}"
arch="${1:-arm64}"

case "$arch" in
    arm64|arm64-v8a)
        arch="arm64"
        abi="arm64-v8a"
        prefix_name="arm64"
        llvm_target="aarch64-linux-android"
        ;;
    armv7l|armeabi-v7a)
        arch="armv7l"
        abi="armeabi-v7a"
        prefix_name="armv7l"
        llvm_target="arm-linux-androideabi"
        ;;
    x86)
        abi="x86"
        prefix_name="x86"
        llvm_target="i686-linux-android"
        ;;
    x86_64)
        abi="x86_64"
        prefix_name="x86_64"
        llvm_target="x86_64-linux-android"
        ;;
    *)
        echo "Usage: $0 [arm64|arm64-v8a|armv7l|armeabi-v7a|x86|x86_64]" >&2
        exit 2
        ;;
esac

work_dir="${MPV_ANDROID_WORK_DIR:-${HOME:-$(pwd)/build}/.cache/droidfs-mpv/mpv-android-$tag}"
mkdir -p "$(dirname "$work_dir")"

if command -v apt-get >/dev/null 2>&1; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
        autoconf pkg-config libtool ninja-build unzip wget meson gperf python3-venv
fi

tools_dir="$(dirname "$work_dir")/tools"
meson_venv="$tools_dir/meson-venv"
if [ ! -x "$meson_venv/bin/meson" ]; then
    python3 -m venv "$meson_venv"
    "$meson_venv/bin/python" -m pip install --upgrade pip
    "$meson_venv/bin/python" -m pip install "meson>=1.6.1,<2"
fi
export PATH="$meson_venv/bin:$PATH"

if [ ! -d "$work_dir/.git" ]; then
    git clone https://github.com/mpv-android/mpv-android.git "$work_dir"
fi

git -C "$work_dir" fetch --tags --depth 1 origin "$tag"
git -C "$work_dir" checkout --detach "$tag"

pushd "$work_dir/buildscripts" >/dev/null
./download.sh
./buildall.sh --arch "$arch" mpv
popd >/dev/null

prefix="$work_dir/buildscripts/prefix/$prefix_name"
if [ ! -f "$prefix/lib/libmpv.so" ]; then
    echo "libmpv.so was not produced at $prefix/lib/libmpv.so" >&2
    exit 1
fi

mkdir -p "jniLibs/$abi" include
cp "$prefix/lib/"*.so "jniLibs/$abi/"

ndk_root="$(find "$work_dir/buildscripts/sdk" -maxdepth 1 -type d -name "android-ndk-*" | sort -V | tail -n 1)"
if [ -z "$ndk_root" ]; then
    ndk_root="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
fi
if [ -z "$ndk_root" ] && [ -d "/opt/android-sdk/ndk" ]; then
    ndk_root="$(find /opt/android-sdk/ndk -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
fi

cxx_shared="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$llvm_target/libc++_shared.so"
if [ ! -f "$cxx_shared" ]; then
    echo "libc++_shared.so was not found at $cxx_shared" >&2
    exit 1
fi
cp "$cxx_shared" "jniLibs/$abi/libc++_shared.so"

rm -rf include/mpv
cp -R "$prefix/include/mpv" include/mpv

echo "Installed libmpv and native dependencies for $abi"
