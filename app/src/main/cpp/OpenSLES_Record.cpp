//
// Created by pc on 2019/1/8.
//

#include <jni.h>
#include <string>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>


//  -------------------  openSLES 处理音频  start ----------------------------

extern "C"
JNIEXPORT void JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_opensles_OpenSLESActivity_startRecord(
        JNIEnv *env, jobject instance, jstring path_) {
    const char *path = env->GetStringUTFChars(path_, 0);


    env->ReleaseStringUTFChars(path_, path);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_opensles_OpenSLESActivity_stopRecord(
        JNIEnv *env, jobject instance) {


}

//  -------------------  openSLES 处理音频  end ----------------------------
