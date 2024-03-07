#include "jni_helper.h"

#include <stdlib.h>
#include <string.h>

static JavaVM *global_vm;

static jclass c_string;
static jmethodID m_new_string;
static jmethodID m_get_bytes;

void initialize_jni(JavaVM *vm, JNIEnv *env) {
    global_vm = vm;

    jclass localStringCls = (*env)->FindClass(env, "java/lang/String");
    c_string = (jclass)(*env)->NewGlobalRef(env, localStringCls);
    (*env)->DeleteLocalRef(env, localStringCls);

    m_new_string = (*env)->GetMethodID(env, c_string, "<init>", "([B)V");
    m_get_bytes = (*env)->GetMethodID(env, c_string, "getBytes", "()[B");
}

JavaVM *global_java_vm() {
    return global_vm;
}

char *jni_get_string(JNIEnv *env, jstring javaString) {
    if (javaString == NULL) {
        return NULL;
    }

    const char *strChars = (*env)->GetStringUTFChars(env, javaString, NULL);
    if (strChars == NULL) {
        return NULL;
    }

    char *newStr = strdup(strChars);
    (*env)->ReleaseStringUTFChars(env, javaString, strChars);

    return newStr;
}

jstring jni_new_string(JNIEnv *env, const char *str) {
    if (str == NULL) {
        return NULL;
    }

    jbyteArray bytes = (*env)->NewByteArray(env, strlen(str));
    (*env)->SetByteArrayRegion(env, bytes, 0, strlen(str), (const jbyte *)str);
    
    jstring strObj = (jstring)(*env)->NewObject(env, c_string, m_new_string, bytes);
    (*env)->DeleteLocalRef(env, bytes);
    
    return strObj;
}

void release_string(char **str) {
    if (str != NULL) {
        free(*str);
        *str = NULL;
    }
}

int jni_catch_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 1;
    }
    return 0;
}

void jni_attach_thread(struct _scoped_jni *scopedJni) {
    JavaVM *vm = global_java_vm();

    jint getEnvResult = (*vm)->GetEnv(vm, (void **)&scopedJni->env, JNI_VERSION_1_6);
    if (getEnvResult == JNI_OK) {
        scopedJni->require_release = 0;
        return;
    }

    jint attachResult = (*vm)->AttachCurrentThread(vm, &scopedJni->env, NULL);
    if (attachResult != JNI_OK) {
        abort();
    }

    scopedJni->require_release = 1;
}

void jni_detach_thread(struct _scoped_jni *scopedJni) {
    if (scopedJni->require_release) {
        JavaVM *vm = global_java_vm();
        (*vm)->DetachCurrentThread(vm);
    }
}
