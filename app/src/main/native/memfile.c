#include <errno.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <jni.h>
#include <android/log.h>

const char* LOG_TAG = "MemFile";

void log_err(const char* function) {
   __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s(): %s", function, strerror(errno));
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_MemFile_00024Companion_createMemFile(JNIEnv *env, jobject thiz, jstring jname,
                                                  jlong size) {
    const char* name = (*env)->GetStringUTFChars(env, jname, NULL);
    int fd = syscall(SYS_memfd_create, name, MFD_CLOEXEC);
    if (fd < 0) {
        log_err("memfd_create");
        return fd;
    }
    if (ftruncate64(fd, size) == -1) {
        log_err("ftruncate64");
        close(fd);
        return -1;
    }
    return fd;
}