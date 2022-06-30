#include <sys/stat.h>
#include <jni.h>
#include <libcryfs-jni.h>

JNIEXPORT jlong JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeInit(JNIEnv *env, jobject thiz,
                                                               jstring base_dir, jstring jlocalStateDir,
                                                               jbyteArray password, jbyteArray givenHash,
                                                               jobject returnedHash,
                                                               jboolean createBaseDir,
                                                               jstring cipher) {
    return cryfs_init(env, base_dir, jlocalStateDir, password, givenHash, returnedHash, createBaseDir, cipher);
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeChangeEncryptionKey(
        JNIEnv *env, jobject thiz, jstring base_dir, jstring local_state_dir,
        jbyteArray current_password, jbyteArray given_hash, jbyteArray new_password,
        jobject returned_hash) {
    return cryfs_change_encryption_key(env, base_dir, local_state_dir, current_password, given_hash, new_password, returned_hash);
}

JNIEXPORT jlong JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeCreate(JNIEnv *env, jobject thiz,
                                                                              jlong fuse_ptr, jstring path,
                                                                              jint mode) {
    return cryfs_create(env, fuse_ptr, path, mode);
}

JNIEXPORT jlong JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeOpen(JNIEnv *env, jobject thiz,
                                                               jlong fuse_ptr, jstring path,
                                                               jint flags) {
    return cryfs_open(env, fuse_ptr, path, flags);
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeRead(JNIEnv *env, jobject thiz,
                                                               jlong fuse_ptr, jlong file_handle,
                                                               jbyteArray buffer, jlong offset) {
    return cryfs_read(env, fuse_ptr, file_handle, buffer, offset);
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeWrite(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong fuse_ptr,
                                                                               jlong file_handle,
                                                                               jlong offset,
                                                                               jbyteArray buffer,
                                                                               jint size) {
    return cryfs_write(env, fuse_ptr, file_handle, offset, buffer, size);
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeTruncate(JNIEnv *env,
                                                                                  jobject thiz,
                                                                                  jlong fuse_ptr,
                                                                                  jstring path,
                                                                                  jlong size) {
    return cryfs_truncate(env, fuse_ptr, path, size) == 0;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeDeleteFile(JNIEnv *env,
                                                                                    jobject thiz,
                                                                                    jlong fuse_ptr,
                                                                                    jstring path) {
    return cryfs_unlink(env, fuse_ptr, path) == 0;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeCloseFile(JNIEnv *env,
                                                                                   jobject thiz,
                                                                                   jlong fuse_ptr,
                                                                                   jlong file_handle) {
    return cryfs_release(fuse_ptr, file_handle) == 0;
}

struct readDirHelper {
    JNIEnv* env;
    jclass explorerElement;
    jmethodID explorerElementNew;
    jmethodID arrayListAdd;
    jobject elementList;
    jstring jparentPath;
};

int readDir(void* data, const char* name, const struct stat* stat) {
    struct readDirHelper* helper = (struct readDirHelper*)data;
    jstring jname = (*helper->env)->NewStringUTF(helper->env, name);
    jobject explorerElement = (*helper->env)->CallStaticObjectMethod(
            helper->env,
            helper->explorerElement,
            helper->explorerElementNew,
            jname,
            stat->st_mode,
            stat->st_size,
            stat->st_mtim.tv_sec,
            helper->jparentPath
    );
    (*helper->env)->CallBooleanMethod(helper->env, helper->elementList, helper->arrayListAdd, explorerElement);
    (*helper->env)->DeleteLocalRef(helper->env, explorerElement);
    (*helper->env)->DeleteLocalRef(helper->env, jname);
    return 0;
}

JNIEXPORT jobject JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeReadDir(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jlong fuse_ptr,
                                                                                 jstring path) {
    jclass arrayList = (*env)->NewLocalRef(env, (*env)->FindClass(env, "java/util/ArrayList"));
    jmethodID arrayListInit = (*env)->GetMethodID(env, arrayList, "<init>", "()V");
    struct readDirHelper helper;
    helper.env = env;
    helper.explorerElement = (*env)->NewLocalRef(env, (*env)->FindClass(env, "sushi/hardcore/droidfs/explorers/ExplorerElement"));
    helper.explorerElementNew = (*env)->GetStaticMethodID(
            env, helper.explorerElement, "new",
            "(Ljava/lang/String;IJJLjava/lang/String;)Lsushi/hardcore/droidfs/explorers/ExplorerElement;"
    );
    helper.arrayListAdd = (*env)->GetMethodID(env, arrayList, "add", "(Ljava/lang/Object;)Z");
    helper.elementList = (*env)->NewObject(env, arrayList, arrayListInit);
    helper.jparentPath = path;

    int result = cryfs_readdir(env, fuse_ptr, path, &helper, readDir);

    if (result == 0) {
        return helper.elementList;
    } else {
        return NULL;
    }
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeMkdir(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong fuse_ptr,
                                                                               jstring path,
                                                                               jint mode) {
    return cryfs_mkdir(env, fuse_ptr, path, mode) == 0;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeRmdir(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong fuse_ptr,
                                                                               jstring path) {
    return cryfs_rmdir(env, fuse_ptr, path) == 0;
}

JNIEXPORT jobject JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeGetAttr(JNIEnv *env,
                                                                                 jobject thiz,
                                                                                 jlong fuse_ptr,
                                                                                 jstring path) {
    struct stat stbuf;
    int result = cryfs_getattr(env, fuse_ptr, path, &stbuf);
    if (result == 0) {
        jclass stat = (*env)->FindClass(env, "sushi/hardcore/droidfs/filesystems/Stat");
        jmethodID statInit = (*env)->GetMethodID(env, stat, "<init>", "(IJJ)V");
        return (*env)->NewObject(
            env, stat, statInit,
            (jint)stbuf.st_mode,
            (jlong)stbuf.st_size,
            (jlong)stbuf.st_mtim.tv_sec
        );
    } else {
        return NULL;
    }
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeRename(JNIEnv *env,
                                                                                jobject thiz,
                                                                                jlong fuse_ptr,
                                                                                jstring src_path,
                                                                                jstring dst_path) {
    return cryfs_rename(env, fuse_ptr, src_path, dst_path) == 0;
}

JNIEXPORT void JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeClose(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong fuse_ptr) {
    cryfs_destroy(fuse_ptr);
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_CryfsVolume_00024Companion_nativeIsClosed(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jlong fuse_ptr) {
    return cryfs_is_closed(fuse_ptr);
}