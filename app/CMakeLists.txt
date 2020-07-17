cmake_minimum_required(VERSION 3.4.1)

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

include_directories(
        ${PROJECT_SOURCE_DIR}/libgocryptfs/build/${ANDROID_ABI}/
)
