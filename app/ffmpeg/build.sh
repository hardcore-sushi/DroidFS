#!/bin/bash

if [ -z ${ANDROID_NDK_HOME+x} ]; then
	echo "Error: \$ANDROID_NDK_HOME is not defined."
else
	compile_for_arch() {
		case $1 in
			"x86_64")
				CFN="x86_64-linux-android21-clang"
				ARCH="x86_64"
				;;
			"x86")
				CFN="i686-linux-android21-clang"
				ARCH="i686"
				;;
			"arm64-v8a")
				CFN="aarch64-linux-android21-clang"
				ARCH="aarch64"
				;;
			"armeabi-v7a")
				CFN="armv7a-linux-androideabi19-clang"
				ARCH="arm"
				;;
		esac
		cd ffmpeg && make clean &&
		./configure \
			--cc="$CFN" \
			--cxx="$CFN++" \
			--arch="$ARCH" \
			--target-os=android \
			--enable-cross-compile \
			--enable-version3 \
			--disable-programs \
			--disable-bsfs \
			--disable-parsers \
			--disable-demuxers \
			--disable-decoders \
			--disable-avdevice \
			--disable-swresample \
			--disable-swscale \
			--disable-postproc \
			--disable-avfilter \
			--disable-network \
			--disable-doc \
			--disable-htmlpages \
			--disable-manpages \
			--disable-podpages \
			--disable-txtpages \
			--disable-sndio \
			--disable-schannel \
			--disable-securetransport \
			--disable-xlib \
			--disable-zlib \
			--disable-cuvid \
			--disable-nvenc \
			--disable-vdpau \
			--disable-videotoolbox \
			--disable-audiotoolbox \
			--disable-appkit \
			--disable-alsa \
			--disable-debug \
			>/dev/null &&
			make -j 8 >/dev/null &&
			mkdir -p ../build/$1/libavformat ../build/$1/libavcodec ../build/$1/libavutil &&
			cp libavformat/*.h libavformat/libavformat.a ../build/$1/libavformat &&
			cp libavcodec/*.h libavcodec/libavcodec.a ../build/$1/libavcodec &&
			cp libavutil/*.h libavutil/libavutil.a ../build/$1/libavutil &&
			cd ..
	}

	export PATH=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
	if [ "$#" -eq 1 ]; then
		compile_for_arch $1
	else
		declare -a ABIs=("x86_64" "x86" "arm64-v8a" "armeabi-v7a")
		for abi in ${ABIs[@]}; do
			echo "Compiling for $abi..."
			compile_for_arch $abi
		done
	fi
fi
