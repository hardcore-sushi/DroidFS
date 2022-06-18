cmake_minimum_required(VERSION 3.10)

add_subdirectory(${PROJECT_SOURCE_DIR}/libcryfs/)

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

add_library(cryfs_jni SHARED src/main/native/libcryfs.c)
#file(GLOB CRYFS_STATIC_LIBRARIES ${PROJECT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/lib*.a)
target_link_libraries(cryfs_jni fspp-fuse)#${CRYFS_STATIC_LIBRARIES})

add_library(
        avformat
        SHARED
        IMPORTED
)

set_target_properties(
        avformat
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavformat/libavformat.so
)

add_library(
        avcodec
        SHARED
        IMPORTED
)

set_target_properties(
        avcodec
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavcodec/libavcodec.so
)

add_library(
        avutil
        SHARED
        IMPORTED
)

set_target_properties(
        avutil
        PROPERTIES IMPORTED_LOCATION
        ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI}/libavutil/libavutil.so
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