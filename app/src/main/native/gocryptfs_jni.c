#include <jni.h>
#include <malloc.h>
#include <string.h>
#include "libgocryptfs.h"

const int KeyLen = 32;

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_00024Companion_nativeCreateVolume(JNIEnv *env, jclass clazz,
                                                                             jstring jroot_cipher_dir,
                                                                             jbyteArray jpassword,
                                                                             jboolean plainTextNames,
                                                                             jint xchacha,
                                                                             jint logN,
                                                                             jstring jcreator,
                                                                             jbyteArray jreturned_hash) {
    const char* root_cipher_dir = (*env)->GetStringUTFChars(env, jroot_cipher_dir, NULL);
    const char* creator = (*env)->GetStringUTFChars(env, jcreator, NULL);
    GoString gofilename = {root_cipher_dir, strlen(root_cipher_dir)}, gocreator = {creator, strlen(creator)};

    const size_t password_len = (const size_t) (*env)->GetArrayLength(env, jpassword);
    jbyte* password = (*env)->GetByteArrayElements(env, jpassword, NULL);
    GoSlice go_password = {password, password_len, password_len};

    size_t returned_hash_len;
    GoSlice go_returned_hash;
    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        returned_hash_len = (size_t) (*env)->GetArrayLength(env, jreturned_hash);
        go_returned_hash.data = (*env)->GetByteArrayElements(env, jreturned_hash, NULL);
    } else {
        returned_hash_len = KeyLen;
        go_returned_hash.data = malloc(KeyLen);
    }
    go_returned_hash.len = returned_hash_len;
    go_returned_hash.cap = returned_hash_len;

    GoUint8 result = gcf_create_volume(gofilename, go_password, plainTextNames, (GoInt8) xchacha, logN, gocreator, go_returned_hash);

    (*env)->ReleaseByteArrayElements(env, jpassword, password, 0);
    (*env)->ReleaseStringUTFChars(env, jcreator, creator);

    GoInt sessionID = -2;
    if (result) {
        GoSlice null_slice = {NULL, 0, 0};
        sessionID = gcf_init(gofilename, null_slice, go_returned_hash, null_slice);
    }

    (*env)->ReleaseStringUTFChars(env, jroot_cipher_dir, root_cipher_dir);

    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        (*env)->ReleaseByteArrayElements(env, jreturned_hash, go_returned_hash.data, 0);
    } else {
        for (unsigned int i=0; i<returned_hash_len; ++i) {
            ((unsigned char*) go_returned_hash.data)[i] = 0;
        }
        free(go_returned_hash.data);
    }

    return sessionID;
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_00024Companion_nativeInit(JNIEnv *env, jobject clazz,
                                                      jstring jroot_cipher_dir,
                                                      jbyteArray jpassword,
                                                      jbyteArray jgiven_hash,
                                                      jbyteArray jreturned_hash) {
    const char* root_cipher_dir = (*env)->GetStringUTFChars(env, jroot_cipher_dir, NULL);
    GoString go_root_cipher_dir = {root_cipher_dir, strlen(root_cipher_dir)};

    size_t password_len;
    jbyte* password;
    GoSlice go_password = {NULL, 0, 0};
    size_t given_hash_len;
    jbyte* given_hash;
    GoSlice go_given_hash = {NULL, 0, 0};
    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        password_len = (size_t) (*env)->GetArrayLength(env, jpassword);
        password = (*env)->GetByteArrayElements(env, jpassword, NULL);
        go_password.data = password;
        go_password.len = password_len;
        go_password.cap = password_len;
    } else {
        given_hash_len = (size_t) (*env)->GetArrayLength(env, jgiven_hash);
        given_hash = (*env)->GetByteArrayElements(env, jgiven_hash, NULL);
        go_given_hash.data = given_hash;
        go_given_hash.len = given_hash_len;
        go_given_hash.cap = given_hash_len;
    }

    size_t returned_hash_len;
    jbyte* returned_hash;
    GoSlice go_returned_hash = {NULL, 0, 0};
    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)){
        returned_hash_len = (size_t) (*env)->GetArrayLength(env, jreturned_hash);
        returned_hash = (*env)->GetByteArrayElements(env, jreturned_hash, NULL);
        go_returned_hash.data = returned_hash;
        go_returned_hash.len = returned_hash_len;
        go_returned_hash.cap = returned_hash_len;
    }

    GoInt sessionID = gcf_init(go_root_cipher_dir, go_password, go_given_hash, go_returned_hash);

    (*env)->ReleaseStringUTFChars(env, jroot_cipher_dir, root_cipher_dir);

    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        (*env)->ReleaseByteArrayElements(env, jpassword, password, 0);
    } else {
        (*env)->ReleaseByteArrayElements(env, jgiven_hash, given_hash, 0);
    }

    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)){
        (*env)->ReleaseByteArrayElements(env, jreturned_hash, returned_hash, 0);
    }

    return sessionID;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1is_1closed(JNIEnv *env, jobject thiz,
                                                                    jint sessionID) {
    return gcf_is_closed(sessionID);
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_00024Companion_changePassword(JNIEnv *env, jclass clazz,
                                                                               jstring jroot_cipher_dir,
                                                                               jbyteArray jold_password,
                                                                               jbyteArray jgiven_hash,
                                                                               jbyteArray jnew_password,
                                                                               jbyteArray jreturned_hash) {
    const char* root_cipher_dir = (*env)->GetStringUTFChars(env, jroot_cipher_dir, NULL);
    GoString go_root_cipher_dir = {root_cipher_dir, strlen(root_cipher_dir)};

    size_t old_password_len;
    jbyte* old_password;
    GoSlice go_old_password = {NULL, 0, 0};
    size_t given_hash_len;
    jbyte* given_hash;
    GoSlice go_given_hash = {NULL, 0, 0};
    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        old_password_len = (size_t) (*env)->GetArrayLength(env, jold_password);
        old_password = (*env)->GetByteArrayElements(env, jold_password, NULL);
        go_old_password.data = old_password;
        go_old_password.len = old_password_len;
        go_old_password.cap = old_password_len;
    } else {
        given_hash_len = (size_t) (*env)->GetArrayLength(env, jgiven_hash);
        given_hash = (*env)->GetByteArrayElements(env, jgiven_hash, NULL);
        go_given_hash.data = given_hash;
        go_given_hash.len = given_hash_len;
        go_given_hash.cap = given_hash_len;
    }

    size_t new_password_len = (size_t) (*env)->GetArrayLength(env, jnew_password);
    jbyte* new_password = (*env)->GetByteArrayElements(env, jnew_password, NULL);
    GoSlice go_new_password = {new_password, new_password_len, new_password_len};

    size_t returned_hash_len;
    jbyte* returned_hash;
    GoSlice go_returned_hash = {NULL, 0, 0};
    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        returned_hash_len = (size_t) (*env)->GetArrayLength(env, jreturned_hash);
        returned_hash = (*env)->GetByteArrayElements(env, jreturned_hash, NULL);
        go_returned_hash.data = returned_hash;
        go_returned_hash.len = returned_hash_len;
        go_returned_hash.cap = returned_hash_len;
    }

    GoUint8 result = gcf_change_password(go_root_cipher_dir, go_old_password, go_given_hash, go_new_password, go_returned_hash);

    (*env)->ReleaseStringUTFChars(env, jroot_cipher_dir, root_cipher_dir);

    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        (*env)->ReleaseByteArrayElements(env, jold_password, old_password, 0);
    } else {
        (*env)->ReleaseByteArrayElements(env, jgiven_hash, given_hash, 0);
    }

    (*env)->ReleaseByteArrayElements(env, jnew_password, new_password, 0);

    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        (*env)->ReleaseByteArrayElements(env, jreturned_hash, returned_hash, 0);
    }

    return result;
}

JNIEXPORT void JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1close(JNIEnv *env, jobject thiz, jint sessionID) {
    gcf_close(sessionID);
}

JNIEXPORT jobject JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1list_1dir(JNIEnv *env, jobject thiz,
                                                          jint sessionID, jstring jplain_dir) {
    const char* plain_dir = (*env)->GetStringUTFChars(env, jplain_dir, NULL);
    const size_t plain_dir_len = strlen(plain_dir);
    const char append_slash = plain_dir[plain_dir_len-1] != '/';
    GoString go_plain_dir = {plain_dir, plain_dir_len};

    struct gcf_list_dir_return elements = gcf_list_dir(sessionID, go_plain_dir);

    jobject elementList = NULL;
    if (elements.r0 != NULL) {
        jclass arrayList = (*env)->NewLocalRef(env, (*env)->FindClass(env, "java/util/ArrayList"));
        jmethodID arrayListInit = (*env)->GetMethodID(env, arrayList, "<init>", "(I)V");
        jmethodID arrayListAdd = (*env)->GetMethodID(env, arrayList, "add", "(Ljava/lang/Object;)Z");
        jclass classExplorerElement = (*env)->NewLocalRef(env, (*env)->FindClass(env, "sushi/hardcore/droidfs/explorers/ExplorerElement"));
        jmethodID explorerElementNew = (*env)->GetStaticMethodID(env, classExplorerElement, "new", "(Ljava/lang/String;IJJLjava/lang/String;)Lsushi/hardcore/droidfs/explorers/ExplorerElement;");
        elementList = (*env)->NewObject(env, arrayList, arrayListInit, elements.r2);

        unsigned int c = 0;
        for (unsigned int i=0; i<elements.r2; ++i) {
            const char* name = &(elements.r0[c]);
            size_t nameLen = strlen(name);

            char* fullPath = malloc(sizeof(char) * (plain_dir_len + nameLen + 2));
            strcpy(fullPath, plain_dir);
            if (append_slash) {
                strcat(fullPath, "/");
            }
            strcat(fullPath, name);

            GoString go_name = {fullPath, strlen(fullPath)};
            struct gcf_get_attrs_return attrs = gcf_get_attrs(sessionID, go_name);
            free(fullPath);

            jstring jname = (*env)->NewStringUTF(env, name);
            jobject explorerElement = (*env)->CallStaticObjectMethod(
                    env,
                    classExplorerElement,
                    explorerElementNew,
                    jname,
                    elements.r1[i],
                    (long long) attrs.r1,
                    (long long) attrs.r2,
                    jplain_dir
            );
            (*env)->CallBooleanMethod(env, elementList, arrayListAdd, explorerElement);
            (*env)->DeleteLocalRef(env, explorerElement);
            (*env)->DeleteLocalRef(env, jname);
            c += nameLen + 1;
        }

        free(elements.r0);
        free(elements.r1);
    }

    (*env)->ReleaseStringUTFChars(env, jplain_dir, plain_dir);

    return elementList;
}

JNIEXPORT jobject JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1get_1attr(JNIEnv *env, jobject thiz,
                                                              jint sessionID, jstring jfile_path) {
    const char* file_path = (*env)->GetStringUTFChars(env, jfile_path, NULL);
    GoString go_file_path = {file_path, strlen(file_path)};

    struct gcf_get_attrs_return attrs = gcf_get_attrs(sessionID, go_file_path);

    (*env)->ReleaseStringUTFChars(env, jfile_path, file_path);

    if (attrs.r3 == 1) {
        jclass stat = (*env)->FindClass(env, "sushi/hardcore/droidfs/filesystems/Stat");
        jmethodID statInit = (*env)->GetMethodID(env, stat, "<init>", "(IJJ)V");
        return (*env)->NewObject(
                env, stat, statInit,
                (jint)attrs.r0,
                (jlong)attrs.r1,
                (jlong)attrs.r2
        );
    } else {
        return NULL;
    }
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1open_1read_1mode(JNIEnv *env, jobject thiz,
                                                                         jint sessionID,
                                                                         jstring jfile_path) {
    const char* file_path = (*env)->GetStringUTFChars(env, jfile_path, NULL);
    GoString go_file_path = {file_path, strlen(file_path)};

    GoInt handleID = gcf_open_read_mode(sessionID, go_file_path);

    (*env)->ReleaseStringUTFChars(env, jfile_path, file_path);

    return handleID;
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1open_1write_1mode(JNIEnv *env, jobject thiz,
                                                                          jint sessionID,
                                                                          jstring jfile_path,
                                                                          jint mode) {
    const char* file_path = (*env)->GetStringUTFChars(env, jfile_path, NULL);
    GoString go_file_path = {file_path, strlen(file_path)};

    GoInt handleID = gcf_open_write_mode(sessionID, go_file_path, (GoUint32) mode);

    (*env)->ReleaseStringUTFChars(env, jfile_path, file_path);

    return handleID;
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1write_1file(JNIEnv *env, jobject thiz,
                                                            jint sessionID, jint handleID, jlong file_offset,
                                                            jbyteArray jbuff, jlong src_offset, jint length) {
    jbyte* buff = (*env)->GetByteArrayElements(env, jbuff, NULL);
    GoSlice go_buff = {buff+src_offset, length, length};

    int written = gcf_write_file(sessionID, handleID, (GoUint64) file_offset, go_buff);

    (*env)->ReleaseByteArrayElements(env, jbuff, buff, 0);

    return written;
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1read_1file(JNIEnv *env, jobject thiz,
                                                           jint sessionID, jint handleID, jlong file_offset,
                                                            jbyteArray jbuff, jlong dst_offset, jint length) {
    jbyte* buff = (*env)->GetByteArrayElements(env, jbuff, NULL);
    GoSlice go_buff = {buff+dst_offset, length, length};

    int read = gcf_read_file(sessionID, handleID, (GoUint64) file_offset, go_buff);

    (*env)->ReleaseByteArrayElements(env, jbuff, buff, 0);
    return read;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1truncate(JNIEnv *env, jobject thiz,
                                                                  jint sessionID,
                                                                  jstring jpath,
                                                                  jlong offset) {
    const char* path = (*env)->GetStringUTFChars(env, jpath, NULL);
    GoString go_path = {path, strlen(path)};

    GoUint8 result = gcf_truncate(sessionID, go_path, (GoUint64) offset);

    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return result;
}

JNIEXPORT void JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1close_1file(JNIEnv *env, jobject thiz,
                                                                         jint sessionID,
                                                                         jint handleID) {
    gcf_close_file(sessionID, handleID);
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1remove_1file(JNIEnv *env, jobject thiz,
                                                                     jint sessionID, jstring jfile_path) {
    const char* file_path = (*env)->GetStringUTFChars(env, jfile_path, NULL);
    GoString go_file_path = {file_path, strlen(file_path)};

    GoUint8 result = gcf_remove_file(sessionID, go_file_path);

    (*env)->ReleaseStringUTFChars(env, jfile_path, file_path);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1mkdir(JNIEnv *env, jobject thiz,
                                                      jint sessionID, jstring jdir_path, jint mode) {
    const char* dir_path = (*env)->GetStringUTFChars(env, jdir_path, NULL);
    GoString go_dir_path = {dir_path, strlen(dir_path)};

    GoUint8 result = gcf_mkdir(sessionID, go_dir_path, (GoUint32) mode);

    (*env)->ReleaseStringUTFChars(env, jdir_path, dir_path);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1rmdir(JNIEnv *env, jobject thiz,
                                                      jint sessionID, jstring jdir_path) {
    const char* dir_path = (*env)->GetStringUTFChars(env, jdir_path, NULL);
    GoString go_dir_path = {dir_path, strlen(dir_path)};

    GoUint8 result = gcf_rmdir(sessionID, go_dir_path);

    (*env)->ReleaseStringUTFChars(env, jdir_path, dir_path);

    return result;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1rename(JNIEnv *env, jobject thiz,
                                                                jint sessionID, jstring jold_path,
                                                                jstring jnew_path) {
    const char* old_path = (*env)->GetStringUTFChars(env, jold_path, NULL);
    GoString go_old_path = {old_path, strlen(old_path)};
    const char* new_path = (*env)->GetStringUTFChars(env, jnew_path, NULL);
    GoString go_new_path = {new_path, strlen(new_path)};

    GoUint8 result = gcf_rename(sessionID, go_old_path, go_new_path);

    (*env)->ReleaseStringUTFChars(env, jold_path, old_path);
    (*env)->ReleaseStringUTFChars(env, jnew_path, new_path);

    return result;
}