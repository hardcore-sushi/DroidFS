#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <jni.h>

extern "C"
JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_MemFile_00024Companion_createMemFile(JNIEnv *env, jobject thiz, jstring jname,
                                                  jlong size) {
    const char* name = env->GetStringUTFChars(jname, nullptr);
    int fd = syscall(SYS_memfd_create, name, MFD_CLOEXEC);
    if (fd < 0) return fd;
    if (ftruncate64(fd, size) == -1) {
        close(fd);
        return -1;
    }
    return fd;
}

extern "C"
JNIEXPORT void JNICALL
Java_sushi_hardcore_droidfs_MemFile_close(JNIEnv *env, jobject thiz, jint fd) {
    close(fd);
}