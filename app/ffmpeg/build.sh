#!/bin/bash

set -e

if [ -z ${ANDROID_NDK_HOME+x} ]; then
	echo "Error: \$ANDROID_NDK_HOME is not defined." >&2
	exit 1
else
	cd "$(dirname "$0")"
	FFMPEG_DIR="ffmpeg"
	compile_for_arch() {
		echo "Compiling for $1..."
		case $1 in
			"x86_64")
				CFN="x86_64-linux-android21-clang"
				ARCH="x86_64"
				;;
			"x86")
				CFN="i686-linux-android21-clang"
				ARCH="i686"
				EXTRA_FLAGS="--disable-asm"
				;;
			"arm64-v8a")
				CFN="aarch64-linux-android21-clang"
				ARCH="aarch64"
				;;
			"armeabi-v7a")
				CFN="armv7a-linux-androideabi21-clang"
				ARCH="arm"
				;;
		esac
		(cd $FFMPEG_DIR
		make clean || true
		./configure \
			--cc="$CFN" \
			--cxx="$CFN++" \
			--arch="$ARCH" \
			$EXTRA_FLAGS \
			--target-os=android \
			--enable-cross-compile \
			--enable-version3 \
			--disable-programs \
			--disable-static \
			--enable-shared \
			--disable-bsfs \
			--disable-parsers \
			--disable-demuxers \
			--disable-muxers \
			--enable-muxer="mp4" \
			--disable-decoders \
			--disable-encoders \
			--enable-encoder="aac" \
			--disable-avdevice \
			--disable-swresample \
			--disable-swscale \
			--disable-avfilter \
			--disable-network \
			--disable-protocols \
			--disable-autodetect \
			--disable-doc \
			--disable-debug
		make -j "$(nproc --all)" >/dev/null)
		mkdir -p "build/$1/libavformat" "build/$1/libavcodec" "build/$1/libavutil"
		cp $FFMPEG_DIR/libavformat/*.h $FFMPEG_DIR/libavformat/libavformat.so "build/$1/libavformat"
		cp $FFMPEG_DIR/libavcodec/*.h $FFMPEG_DIR/libavcodec/libavcodec.so "build/$1/libavcodec"
		cp $FFMPEG_DIR/libavutil/*.h $FFMPEG_DIR/libavutil/libavutil.so "build/$1/libavutil"
	}

	export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
	if [ $# -eq 1 ]; then
		compile_for_arch "$1"
	else
		for abi in "x86_64" "x86" "arm64-v8a" "armeabi-v7a"; do
			compile_for_arch $abi
		done
	fi
fi
