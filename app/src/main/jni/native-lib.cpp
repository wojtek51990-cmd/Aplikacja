#include <jni.h>

extern "C"
JNIEXPORT jint JNICALL
Java_com_myscooty_android16_MainActivity_nativeAbiLevel(JNIEnv*, jclass) {
    return 64;
}
