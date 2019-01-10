#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring
JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "音视频(基础)";
    return env->NewStringUTF(hello.c_str());
}