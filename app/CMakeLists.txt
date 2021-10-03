cmake_minimum_required(VERSION 3.10)

add_library(
        gocryptfs
        SHARED
        IMPORTED
)

set_target_properties(
        gocryptfs
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/libgocryptfs/build/${ANDROID_ABI}/libgocryptfs.so
)

add_library(
        gocryptfs_jni
        SHARED
        src/main/native/gocryptfs_jni.c
)

target_link_libraries(
        gocryptfs_jni
        gocryptfs
)

add_library(
        avformat
        STATIC
        IMPORTED
)

set_target_properties(
        avformat
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavformat/libavformat.a
)

add_library(
        avcodec
        STATIC
        IMPORTED
)

set_target_properties(
        avcodec
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavcodec/libavcodec.a
)

add_library(
        avutil
        STATIC
        IMPORTED
)

set_target_properties(
        avutil
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavutil/libavutil.a
)

add_library(
        mux
        SHARED
        src/main/native/libmux.c
)

target_link_libraries(
        mux
        avformat
        avcodec
        avutil
)

include_directories(
        ${PROJECT_SOURCE_DIR}/libgocryptfs/build/${ANDROID_ABI}
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}
)