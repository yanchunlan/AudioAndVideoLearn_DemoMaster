//
// Created by pc on 2019/1/9.
//
#include <jni.h>
#include <string>
#include "../AndroidLog.h"


// opensles
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

// asset manager
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <sys/types.h>

#include <stdio.h>
#include <malloc.h> // 内存分配
#include "WlQueue.h"
#include "pcmdata.h"

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

//  -------------------  openSLES 处理音频  end ----------------------------
// 引擎接口
SLObjectItf engineObject = NULL;
SLEngineItf engineEngine = NULL;

// 混音器接口
SLObjectItf outputMixObject = NULL;
SLEnvironmentalReverbItf outputMixEnvironmentalReverb = NULL;
SLEnvironmentalReverbSettings reverbSettings = SL_I3DL2_ENVIRONMENT_PRESET_STONECORRIDOR;

// assets 播放器
SLObjectItf fdPlayerObject = NULL;
SLPlayItf fdPlayerPlay = NULL;
SLVolumeItf fdPlayerVolume = NULL;

// uri 播放器
SLObjectItf uriPlayerObject = NULL;
SLPlayItf uriPlayerPlay = NULL;
SLVolumeItf uriPlayerVolume = NULL;

//pcm
SLObjectItf pcmPlayerObject = NULL;
SLPlayItf pcmPlayerPlay = NULL;
SLVolumeItf pcmPlayerVolume = NULL;

//缓冲器队列接口
SLAndroidSimpleBufferQueueItf pcmBufferQueue = NULL;

FILE *pcmFile;
void *buffer;

uint8_t *out_buffer;


void release();

void createEngine();

void pcmBufferCallBack(SLAndroidSimpleBufferQueueItf pItf_, void *pVoid);

void pcmBufferCallBack2(SLAndroidSimpleBufferQueueItf pItf_, void *pVoid);

void getPcmData(void **pVoid);

extern "C"
JNIEXPORT void JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_opensles2_OpenSLES2Activity_playAudioByOpenSL_11assets(
        JNIEnv *env, jobject instance, jobject assetManager, jstring fileName_) {

    release();
    const char *fileName = env->GetStringUTFChars(fileName_, 0);

    // use asset manager to open asset by filename
    AAssetManager *manager = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = AAssetManager_open(manager, fileName, AASSET_MODE_UNKNOWN);
    env->ReleaseStringUTFChars(fileName_, fileName);

    // open asset as file descriptor
    off_t start, length;
    int fd = AAsset_openFileDescriptor(asset, &start, &length);
    AAsset_close(asset);


    SLresult result;

    // 1.创建引擎
    createEngine();


    // 2.创建混音器
    const SLInterfaceID mids[1] = {SL_IID_ENVIRONMENTALREVERB};
    const SLboolean mreq[1] = {SL_BOOLEAN_FALSE};
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, mids, mreq);
    (void) result;
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    (void) result;
    result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                              &outputMixEnvironmentalReverb);
    if (SL_RESULT_SUCCESS == result) {
        result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
                outputMixEnvironmentalReverb, &reverbSettings);
        (void) result;
    }


    // 3. 设置播放器参数和创建播放器
    //    3.1. 配置audioSource
    SLDataLocator_AndroidFD loc_fd = {SL_DATALOCATOR_ANDROIDFD, fd, start, length};
    SLDataFormat_MIME format_mime = {SL_DATAFORMAT_MIME, NULL, SL_CONTAINERTYPE_UNSPECIFIED};
    SLDataSource audioSrc = {&loc_fd, &format_mime};
    //    3.2. 配置 audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};

    // 创建播放器
    const SLInterfaceID ids[3] = {SL_IID_SEEK, SL_IID_MUTESOLO, SL_IID_VOLUME};
    const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &fdPlayerObject, &audioSrc, &audioSnk,
                                                3, ids, req);
    (void) result;
    result = (*fdPlayerObject)->Realize(fdPlayerObject, SL_BOOLEAN_FALSE); // 实现播放器
    (void) result;
    result = (*fdPlayerObject)->GetInterface(fdPlayerObject, SL_IID_PLAY, &fdPlayerPlay); // 得到播放器接口
    (void) result;
    result = (*fdPlayerObject)->GetInterface(fdPlayerObject, SL_IID_VOLUME,
                                             &fdPlayerVolume); // 得到声音控制接口
    (void) result;

    // 设置播放状态  播放中
    if (NULL != fdPlayerPlay) {
        result = (*fdPlayerPlay)->SetPlayState(fdPlayerPlay, SL_PLAYSTATE_PLAYING);
        (void) result;
    }

    //设置播放音量 （100 * -50：静音 ）
    (*fdPlayerVolume)->SetVolumeLevel(fdPlayerVolume, 20 * -50);
}

void createEngine() {
    SLresult sLresult;
    sLresult = slCreateEngine(&engineObject, 0, NULL, 0, NULL, NULL);
    sLresult = (*engineObject)->Realize(engineObject, SL_BOOLEAN_FALSE);
    sLresult = (*engineObject)->GetInterface(engineObject, SL_IID_ENGINE, &engineEngine);
}

void release() {

    if (pcmPlayerObject != NULL) {
        (*pcmPlayerObject)->Destroy(pcmPlayerObject);
        pcmPlayerObject = NULL;
        pcmPlayerPlay = NULL;
        pcmPlayerVolume = NULL;
        pcmBufferQueue = NULL;
        pcmFile = NULL;
        buffer = NULL;
        out_buffer = NULL;
    }

    if (uriPlayerObject != NULL) {
        (*uriPlayerObject)->Destroy(uriPlayerObject);
        uriPlayerObject = NULL;
        uriPlayerPlay = NULL;
        uriPlayerVolume = NULL;
    }

    if (NULL != fdPlayerObject) {
        (*fdPlayerObject)->Destroy(fdPlayerObject);
        fdPlayerObject = NULL;
        fdPlayerPlay = NULL;
        fdPlayerVolume = NULL;
    }
    if (NULL != outputMixObject) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = NULL;
        outputMixEnvironmentalReverb = NULL;
    }
    if (NULL != engineObject) {
        (*engineObject)->Destroy(engineObject);
        engineObject = NULL;
        engineEngine = NULL;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_opensles2_OpenSLES2Activity_playAudioByOpenSL_1pcm(
        JNIEnv *env, jobject instance, jstring pcmPath_) {

    release();
    const char *pcmPath = env->GetStringUTFChars(pcmPath_, 0);
    pcmFile = fopen(pcmPath, "r");
    if (pcmFile == NULL) {
        LOGE("fopen pcmPath error");
        return;
    }
    //44100 * 2 * 2 表示：44100是频率HZ，2是立体声双通道，2是采用的16位采样即2个字节，所以总的字节数就是：44100 * 2 * 2
    out_buffer = static_cast<uint8_t *>(malloc(44100 * 2 * 2));
    SLresult result;

    // 1.创建引擎
    createEngine();


    // 2.创建混音器
    const SLInterfaceID mids[1] = {SL_IID_ENVIRONMENTALREVERB};
    const SLboolean mreq[1] = {SL_BOOLEAN_FALSE};
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, mids, mreq);
    (void) result;
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    (void) result;
    result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                              &outputMixEnvironmentalReverb);
    if (SL_RESULT_SUCCESS == result) {
        result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
                outputMixEnvironmentalReverb, &reverbSettings);
        (void) result;
    }

    // 3. 设置播放器参数和创建播放器
    //    3.1. 配置audioSource
    SLDataLocator_AndroidSimpleBufferQueue android_queue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                            2};
    //    设置录制规格：PCM、2声道、44100HZ、16bit
    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_44_1,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, //立体声 前左前右
            SL_BYTEORDER_LITTLEENDIAN // 结束标识
    };
    SLDataSource audioSrc = {&android_queue, &format_pcm};
    //    3.2. 配置 audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};

    // 创建播放器
    const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_EFFECTSEND, SL_IID_VOLUME};
    const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &pcmPlayerObject, &audioSrc,
                                                &audioSnk, 3, ids, req);

    (*pcmPlayerObject)->Realize(pcmPlayerObject, SL_BOOLEAN_FALSE); // 实现播放器
    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_PLAY, &pcmPlayerPlay); // 得到播放器接口

    // 缓冲
    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_BUFFERQUEUE,
                                     &pcmBufferQueue); // 得到缓冲区接口
    (*pcmBufferQueue)->RegisterCallback(pcmBufferQueue, pcmBufferCallBack, NULL); // 缓冲区回调


    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_VOLUME, &pcmPlayerVolume); // 得到声音控制接口

    (*pcmPlayerPlay)->SetPlayState(pcmPlayerPlay, SL_PLAYSTATE_PLAYING);// 录制状态


    //  主动调用回调函数开始工作
    pcmBufferCallBack(pcmBufferQueue, NULL);


    env->ReleaseStringUTFChars(pcmPath_, pcmPath);
}

void pcmBufferCallBack(SLAndroidSimpleBufferQueueItf bf, void *context) {
    getPcmData(&buffer);
    if (NULL != buffer) {
        (*pcmBufferQueue)->Enqueue(pcmBufferQueue, buffer, 44100 * 2 * 2);
    }
}

void getPcmData(void **pcm) {
    while (!feof(pcmFile)) {
        fread(out_buffer, 44100 * 2 * 2, 1, pcmFile);
        if (out_buffer == NULL) {
            LOGD("%s", "read end");
            break;
        } else {
            LOGD("%s", "reading");
        }
        *pcm = out_buffer;
        break;
    }
}


// ----------------------------------------------------------------------------------------

WlQueue *wlQueue = NULL;
pthread_t playpcm;


void pcmBufferCallBack2(SLAndroidSimpleBufferQueueItf bf, void *context) {
    pcmData *data = wlQueue->getPcmData();
    if (NULL != data) {
        (*pcmBufferQueue)->Enqueue(pcmBufferQueue, data->getData(), data->getSize());
    }
}

void *createOpenSLES(void *data) {
    SLresult result;

    // 1.创建引擎
    createEngine();


    // 2.创建混音器
    const SLInterfaceID mids[1] = {SL_IID_ENVIRONMENTALREVERB};
    const SLboolean mreq[1] = {SL_BOOLEAN_FALSE};
    result = (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 1, mids, mreq);
    (void) result;
    result = (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    (void) result;
    result = (*outputMixObject)->GetInterface(outputMixObject, SL_IID_ENVIRONMENTALREVERB,
                                              &outputMixEnvironmentalReverb);
    if (SL_RESULT_SUCCESS == result) {
        result = (*outputMixEnvironmentalReverb)->SetEnvironmentalReverbProperties(
                outputMixEnvironmentalReverb, &reverbSettings);
        (void) result;
    }

    // 3. 设置播放器参数和创建播放器
    //    3.1. 配置audioSource
    SLDataLocator_AndroidSimpleBufferQueue android_queue = {SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
                                                            2};
    //    设置录制规格：PCM、2声道、44100HZ、16bit
    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_44_1,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, //立体声 前左前右
            SL_BYTEORDER_LITTLEENDIAN // 结束标识
    };
    SLDataSource audioSrc = {&android_queue, &format_pcm};
    //    3.2. 配置 audio sink
    SLDataLocator_OutputMix loc_outmix = {SL_DATALOCATOR_OUTPUTMIX, outputMixObject};
    SLDataSink audioSnk = {&loc_outmix, NULL};


    // 创建播放器
    const SLInterfaceID ids[3] = {SL_IID_BUFFERQUEUE, SL_IID_EFFECTSEND, SL_IID_VOLUME};
    const SLboolean req[3] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
    result = (*engineEngine)->CreateAudioPlayer(engineEngine, &fdPlayerObject, &audioSrc, &audioSnk,
                                                3, ids, req);

    (*pcmPlayerObject)->Realize(pcmPlayerObject, SL_BOOLEAN_FALSE); // 实现播放器
    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_PLAY, &pcmPlayerPlay); // 得到播放器接口

    // 缓冲
    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_BUFFERQUEUE,
                                     &pcmBufferQueue); // 得到缓冲区接口
    (*pcmBufferQueue)->RegisterCallback(pcmBufferQueue, pcmBufferCallBack2, NULL); // 缓冲区回调


    (*pcmPlayerObject)->GetInterface(pcmPlayerObject, SL_IID_VOLUME, &pcmPlayerVolume); // 得到声音控制接口

    (*pcmPlayerPlay)->SetPlayState(pcmPlayerPlay, SL_PLAYSTATE_PLAYING);// 录制状态


    //  主动调用回调函数开始工作
    pcmBufferCallBack(pcmBufferQueue, NULL);

    pthread_exit(&playpcm); // 退出线程
}


extern "C"
JNIEXPORT void JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_opensles2_OpenSLES2Activity_sendPcmData(
        JNIEnv *env, jobject instance, jbyteArray data_, jint size) {
    jbyte *data = env->GetByteArrayElements(data_, NULL);
    if (wlQueue == NULL) {
        wlQueue = new WlQueue();
        pthread_create(&playpcm, NULL, createOpenSLES, NULL);
    }
    pcmData *pData = new pcmData(reinterpret_cast<char *>(data), size);
    wlQueue->putPcmData(pData);

    LOGD("size is %d queue size is %d", size, wlQueue->getPcmDataSize());

    env->ReleaseByteArrayElements(data_, data, 0);  // jni释放内存方法一般是release ,c++是delete ,or  ，free
}
