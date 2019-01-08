#include <jni.h>
#include <string>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>


extern "C" JNIEXPORT jstring
JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "音视频(基础)";
    return env->NewStringUTF(hello.c_str());
}

//  -------------------  openSLES 处理音频  start ----------------------------

/*
SLObjectItf engineObject = NULL; // 声明引擎接口对象
SLEngineItf engineEngine = NULL; // 具体的引擎对象
// 1. 创建引擎对象
void createEngine() {
    SLresult sLresult; // 返回结果
    sLresult = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL); //第一步创建引擎
    sLresult = (*engineObject)->Realize(engineObject,
                                        SL_BOOLEAN_FALSE); //实现（Realize）engineObject接口对象
    sLresult = (*engineObject)->GetInterface(engineObject, SL_IID_NULL,
                                             &engineEngine);//通过engineObject的GetInterface方法初始化engineEngine
};

// 2. 创建其他接口对象
//    2.1  混音器
SLObjectItf outputMixObject = NULL;
SLEnvironmentalReverbItf outputMixEnvironmentalReverb = NULL; // 具体的混音器对象
void createMixer() {
    SLresult sLresult; // 返回结果
    sLresult = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, NULL,
                                                NULL); //利用引擎接口对象创建混音器接口
    sLresult = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);//实现（Realize）混音器接口对象
    sLresult = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                                &outputMixEnvironmentalReverb);//利用混音器接口对象初始化具体混音器实例
};
//    2.2  播放器
SLObjectItf playerObject = NULL;
SLPlayItf playerPlay = NULL; // 具体的播放器对象
void createPlayer() {
    SLresult sLresult; // 返回结果
    sLresult = (*engineEngine)->CreateAudioPlayer(engineEngine, &playerObject, NULL, NULL, 0, NULL,
                                                  NULL); //利用引擎接口对象创建播放器接口对象
    sLresult = (*playerObject)->Realize(playerObject, SL_BOOLEAN_FALSE);//实现（Realize）播放器接口对象
    sLresult = (*playerObject)->GetInterface(playerObject, SL_IID_PLAY,
                                             &playerPlay);//初始化具体的播放器对象实例
};
 */

//  -------------------  openSLES 处理音频  start ----------------------------



/*extern "C" JNIEXPORT void
JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_MainActivity_startRecord(
        JNIEnv *env,
        jobject instance, jstring path_) {
    const char *path = env->GetStringUTFChars(path_, 0);





    env->ReleaseStringUTFChars(path_, path);
}

extern "C" JNIEXPORT void
JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_MainActivity_stopRecord(
        JNIEnv *env,
        jobject instance) {
}*/
//  -------------------  openSLES 处理音频  end ----------------------------
