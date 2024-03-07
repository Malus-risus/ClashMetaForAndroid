#include "jni_helper.h"

#include <malloc.h>
#include <string.h>

// Maintains a reference to the JavaVM.
static JavaVM *global_vm;

// Cache the String class and Method IDs for reuse.
static jclass c_string;
static jmethodID m_new_string;
static jmethodID m_get_bytes;

// Initializes global references on library load.
void initialize_jni(JavaVM *vm, JNIEnv *env) {
    global_vm = vm;
    c_string = (jclass)(*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/String"));
    m_new_string = (*env)->GetMethodID(env, c_string, "<init>", "([B)V");
    m_get_bytes = (*env)->GetMethodID(env, c_string, "getBytes", "()[B");
}

// Provides access to the JavaVM.
JavaVM *global_java_vm() {
    return global_vm;
}

// Get a C string from a Java string.
char *jni_get_string(JNIEnv *env, jstring str) {
    jbyteArray array = (*env)->CallObjectMethod(env, str, m_get_bytes);
    int length = (*env)->GetArrayLength(env, array);

    char *content = (char *) malloc(length + 1);
    (*env)->GetByteArrayRegion(env, array, 0, length, (jbyte *) content);
    content[length] = 0;

    return content;
}

// Create a new Java string from a C string.
jstring jni_new_string(JNIEnv *env, const char *str) {
    int length = strlen(str);
    jbyteArray array = (*env)->NewByteArray(env, length);

    (*env)->SetByteArrayRegion(env, array, 0, length, (const jbyte *) str);
    return (jstring) (*env)->NewObject(env, c_string, m_new_string, array);
}

// Catch exceptions thrown by JNI operations.
int jni_catch_exception(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return 1;
    }
    return 0;
}

// Attach the current thread to the Java VM.
void jni_attach_thread(struct _scoped_jni *jni) {
    if ((*global_vm)->GetEnv(global_vm, (void **) &jni->env, JNI_VERSION_1_6) != JNI_OK) {
        (*global_vm)->AttachCurrentThread(global_vm, (void **) &jni->env, NULL);
        jni->require_release = 1;
    } else {
        jni->require_release = 0;
    }
}

// Detach the current thread from the Java VM if necessary.
void jni_detach_thread(struct _scoped_jni *jni) {
    if (jni->require_release) {
        (*global_vm)->DetachCurrentThread(global_vm);
    }
}

// Free a C string.
void release_string(char **str) {
    if (str != NULL) {
        free(*str);
        *str = NULL;
    }
}
