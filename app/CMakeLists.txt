cmake_minimum_required(VERSION 3.10)

project(DroidFS)

option(GOCRYPTFS "build libgocryptfs" ON)
option(CRYFS "build libcryfs" ON)

if (GOCRYPTFS)
	add_library(gocryptfs SHARED IMPORTED)
	set_target_properties(
		gocryptfs
		PROPERTIES IMPORTED_LOCATION
		${PROJECT_SOURCE_DIR}/libgocryptfs/build/${ANDROID_ABI}/libgocryptfs.so
	)
	add_library(gocryptfs_jni SHARED src/main/native/gocryptfs_jni.c)
	target_include_directories(gocryptfs_jni PRIVATE ${PROJECT_SOURCE_DIR}/libgocryptfs/build/${ANDROID_ABI})
	target_link_libraries(gocryptfs_jni gocryptfs)
endif()

if (CRYFS)
	add_subdirectory(${PROJECT_SOURCE_DIR}/libcryfs)
	add_library(cryfs_jni SHARED src/main/native/libcryfs.c)
	target_link_libraries(cryfs_jni libcryfs-jni)
endif()

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

target_include_directories(mux PRIVATE ${PROJECT_SOURCE_DIR}/ffmpeg/build/${ANDROID_ABI})

target_link_libraries(
        mux
        avformat
        avcodec
        avutil
)