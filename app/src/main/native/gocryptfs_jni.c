#include <jni.h>
#include <stdio.h>
#include <malloc.h>
#include <string.h>
#include <sys/stat.h>
#include "libgocryptfs.h"

void wipe(char* data, const unsigned int len){
    for (unsigned int i=0; i<len;++i){
        data[i] = '\0';
    }
}

void jcharArray_to_charArray(const jchar* src, char* dst, const unsigned int len){
    for (unsigned int i=0; i<len; ++i){
        dst[i] = src[i];
    }
}

void unsignedCharArray_to_jbyteArray(const unsigned char* src, jbyte* dst, const unsigned int len){
    for (unsigned int i=0; i<len; ++i){
        dst[i] = src[i];
    }
}

void jbyteArray_to_unsignedCharArray(const jbyte* src, unsigned char* dst, const unsigned int len){
    for (unsigned int i=0; i<len; ++i){
        dst[i] = src[i];
    }
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_00024Companion_createVolume(JNIEnv *env, jclass clazz,
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

    const size_t password_len = (*env)->GetArrayLength(env, jpassword);
    char* password = (char*)(*env)->GetByteArrayElements(env, jpassword, NULL);
    GoSlice go_password = {password, password_len, password_len};

    size_t returned_hash_len;
    jbyte* jbyte_returned_hash;
    unsigned char* returned_hash;
    GoSlice go_returned_hash = {NULL, 0, 0};
    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        returned_hash_len = (*env)->GetArrayLength(env, jreturned_hash);
        jbyte_returned_hash = (*env)->GetByteArrayElements(env, jreturned_hash, NULL);
        returned_hash = malloc(returned_hash_len);
        go_returned_hash.data = returned_hash;
        go_returned_hash.len = returned_hash_len;
        go_returned_hash.cap = returned_hash_len;
    }

    GoUint8 result = gcf_create_volume(gofilename, go_password, plainTextNames, xchacha, logN, gocreator, go_returned_hash);

    (*env)->ReleaseStringUTFChars(env, jroot_cipher_dir, root_cipher_dir);
    (*env)->ReleaseStringUTFChars(env, jcreator, creator);
    wipe(password, password_len);
    (*env)->ReleaseByteArrayElements(env, jpassword, (jbyte*)password, 0);

    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        unsignedCharArray_to_jbyteArray(returned_hash, jbyte_returned_hash, returned_hash_len);
        wipe(returned_hash, returned_hash_len);
        free(returned_hash);
        (*env)->ReleaseByteArrayElements(env, jreturned_hash, jbyte_returned_hash, 0);
    }

    return result;
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
    char* password;
    GoSlice go_password = {NULL, 0, 0};
    size_t given_hash_len;
    jbyte* jbyte_given_hash;
    unsigned char* given_hash;
    GoSlice go_given_hash = {NULL, 0, 0};
    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        password_len = (*env)->GetArrayLength(env, jpassword);
        password = (char*)(*env)->GetByteArrayElements(env, jpassword, NULL);
        go_password.data = password;
        go_password.len = password_len;
        go_password.cap = password_len;
    } else {
        given_hash_len = (*env)->GetArrayLength(env, jgiven_hash);
        jbyte_given_hash = (*env)->GetByteArrayElements(env, jgiven_hash, NULL);
        given_hash = malloc(given_hash_len);
        jbyteArray_to_unsignedCharArray(jbyte_given_hash, given_hash, given_hash_len);
        go_given_hash.data = given_hash;
        go_given_hash.len = given_hash_len;
        go_given_hash.cap = given_hash_len;
    }

    size_t returned_hash_len;
    jbyte* jbyte_returned_hash;
    unsigned char* returned_hash;
    GoSlice go_returned_hash = {NULL, 0, 0};
    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)){
        returned_hash_len = (*env)->GetArrayLength(env, jreturned_hash);
        jbyte_returned_hash = (*env)->GetByteArrayElements(env, jreturned_hash, NULL);
        returned_hash = malloc(returned_hash_len);
        go_returned_hash.data = returned_hash;
        go_returned_hash.len = returned_hash_len;
        go_returned_hash.cap = returned_hash_len;
    }

    GoInt sessionID = gcf_init(go_root_cipher_dir, go_password, go_given_hash, go_returned_hash);

    (*env)->ReleaseStringUTFChars(env, jroot_cipher_dir, root_cipher_dir);

    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        wipe(password, password_len);
        (*env)->ReleaseByteArrayElements(env, jpassword, (jbyte*)password, 0);
    } else {
        wipe(given_hash, given_hash_len);
        free(given_hash);
        (*env)->ReleaseByteArrayElements(env, jgiven_hash, jbyte_given_hash, 0);
    }

    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)){
        unsignedCharArray_to_jbyteArray(returned_hash, jbyte_returned_hash, returned_hash_len);
        wipe(returned_hash, returned_hash_len);
        free(returned_hash);
        (*env)->ReleaseByteArrayElements(env, jreturned_hash, jbyte_returned_hash, 0);
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
                                                                               jcharArray jold_password,
                                                                               jbyteArray jgiven_hash,
                                                                               jcharArray jnew_password,
                                                                               jbyteArray jreturned_hash) {
    const char* root_cipher_dir = (*env)->GetStringUTFChars(env, jroot_cipher_dir, NULL);
    GoString go_root_cipher_dir = {root_cipher_dir, strlen(root_cipher_dir)};

    size_t old_password_len;
    jchar* jchar_old_password;
    char* old_password;
    GoSlice go_old_password = {NULL, 0, 0};
    size_t given_hash_len;
    jbyte* jbyte_given_hash;
    unsigned char* given_hash;
    GoSlice go_given_hash = {NULL, 0, 0};
    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        old_password_len = (*env)->GetArrayLength(env, jold_password);
        jchar_old_password = (*env)->GetCharArrayElements(env, jold_password, NULL);
        old_password = malloc(old_password_len);
        jcharArray_to_charArray(jchar_old_password, old_password, old_password_len);
        go_old_password.data = old_password;
        go_old_password.len = old_password_len;
        go_old_password.cap = old_password_len;
    } else {
        given_hash_len = (*env)->GetArrayLength(env, jgiven_hash);
        jbyte_given_hash = (*env)->GetByteArrayElements(env, jgiven_hash, NULL);
        given_hash = malloc(given_hash_len);
        jbyteArray_to_unsignedCharArray(jbyte_given_hash, given_hash, given_hash_len);
        go_given_hash.data = given_hash;
        go_given_hash.len = given_hash_len;
        go_given_hash.cap = given_hash_len;
    }

    size_t new_password_len = (*env)->GetArrayLength(env, jnew_password);
    jchar* jchar_new_password = (*env)->GetCharArrayElements(env, jnew_password, NULL);
    char* new_password = malloc(new_password_len);
    jcharArray_to_charArray(jchar_new_password, new_password, new_password_len);
    GoSlice go_new_password = {new_password, new_password_len, new_password_len};

    size_t returned_hash_len;
    jbyte* jbyte_returned_hash;
    unsigned char* returned_hash;
    GoSlice go_returned_hash = {NULL, 0, 0};
    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        returned_hash_len = (*env)->GetArrayLength(env, jreturned_hash);
        jbyte_returned_hash = (*env)->GetByteArrayElements(env, jreturned_hash, NULL);
        returned_hash = malloc(returned_hash_len);
        go_returned_hash.data = returned_hash;
        go_returned_hash.len = returned_hash_len;
        go_returned_hash.cap = returned_hash_len;
    }

    GoUint8 result = gcf_change_password(go_root_cipher_dir, go_old_password, go_given_hash, go_new_password, go_returned_hash);

    (*env)->ReleaseStringUTFChars(env, jroot_cipher_dir, root_cipher_dir);

    if ((*env)->IsSameObject(env, jgiven_hash, NULL)){
        wipe(old_password, old_password_len);
        free(old_password);
        (*env)->ReleaseCharArrayElements(env, jold_password, jchar_old_password, 0);
    } else {
        wipe(given_hash, given_hash_len);
        free(given_hash);
        (*env)->ReleaseByteArrayElements(env, jgiven_hash, jbyte_given_hash, 0);
    }

    wipe(new_password, new_password_len);
    free(new_password);
    (*env)->ReleaseCharArrayElements(env, jnew_password, jchar_new_password, 0);

    if (!(*env)->IsSameObject(env, jreturned_hash, NULL)) {
        unsignedCharArray_to_jbyteArray(returned_hash, jbyte_returned_hash, returned_hash_len);
        wipe(returned_hash, returned_hash_len);
        free(returned_hash);
        (*env)->ReleaseByteArrayElements(env, jreturned_hash, jbyte_returned_hash, 0);
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
            if (plain_dir[-2] != '/') {
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

    GoInt handleID = gcf_open_write_mode(sessionID, go_file_path, mode);

    (*env)->ReleaseStringUTFChars(env, jfile_path, file_path);

    return handleID;
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1write_1file(JNIEnv *env, jobject thiz,
                                                            jint sessionID, jint handleID, jlong offset,
                                                            jbyteArray jbuff, jint buff_size) {
    jbyte* buff = (*env)->GetByteArrayElements(env, jbuff, NULL);
    GoSlice go_buff = {buff, buff_size, buff_size};

    int written = gcf_write_file(sessionID, handleID, offset, go_buff);

    (*env)->ReleaseByteArrayElements(env, jbuff, buff, 0);

    return written;
}

JNIEXPORT jint JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1read_1file(JNIEnv *env, jobject thiz,
                                                           jint sessionID, jint handleID, jlong offset,
                                                            jbyteArray jbuff) {
    const size_t buff_size = (*env)->GetArrayLength(env, jbuff);
    unsigned char* buff = malloc(sizeof(char)*buff_size);
    GoSlice go_buff = {buff, buff_size, buff_size};

    int read = gcf_read_file(sessionID, handleID, offset, go_buff);

    if (read > 0){
        (*env)->SetByteArrayRegion(env, jbuff, 0, read, (const jbyte*)buff);
    }
    free(buff);
    return read;
}

JNIEXPORT jboolean JNICALL
Java_sushi_hardcore_droidfs_filesystems_GocryptfsVolume_native_1truncate(JNIEnv *env, jobject thiz,
                                                                  jint sessionID,
                                                                  jstring jpath,
                                                                  jlong offset) {
    const char* path = (*env)->GetStringUTFChars(env, jpath, NULL);
    GoString go_path = {path, strlen(path)};

    GoUint8 result = gcf_truncate(sessionID, go_path, offset);

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

    GoUint8 result = gcf_mkdir(sessionID, go_dir_path, mode);

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