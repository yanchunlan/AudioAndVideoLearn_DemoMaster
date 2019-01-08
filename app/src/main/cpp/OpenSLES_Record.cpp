//
// Created by pc on 2019/1/8.
//

#include <jni.h>
#include <string>

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include "RecordBuffer.h"
#include "AndroidLog.h"

//  -------------------  openSLES 处理音频  start ----------------------------

#define RECORDER_FRAMES (2048)
unsigned recorderSize = RECORDER_FRAMES * 2;

SLObjectItf slObjectEngine = NULL; // 引擎接口
SLEngineItf engineItf = NULL;  // 引擎对象

SLObjectItf recordObj = NULL; // 录音器接口
SLRecordItf recordItf = NULL; // 录音器对象

SLAndroidSimpleBufferQueueItf recorderBufferQueue = NULL; // 缓冲队列

RecordBuffer *recordBuffer = NULL; // 录音buffer， 数组在c++中是二级指针

FILE *pcmFile = NULL; // pcm文件

bool finish = false;

// 录制状态监听
void bqRecorderCallback(SLAndroidSimpleBufferQueueItf bg, void *context) {
    fwrite(recordBuffer->getNowBuffer(), 1, recorderSize, pcmFile); // 边录制边写入数据
    if (finish) {
        LOGD("录制完成")
        (*recordItf)->SetRecordState(recordItf, SL_RECORDSTATE_STOPPED);// 录制状态
        fclose(pcmFile);

        (*recordObj)->Destroy(recordObj);
        recordObj = NULL;
        recordItf = NULL;

        (*slObjectEngine)->Destroy(slObjectEngine);
        slObjectEngine = NULL;
        engineItf = NULL;

        delete (recordBuffer);  // 删除指针需要加括号
    } else {
        LOGD("正在录制")  // 调用 Enqueue 入队操作， 不断的取数据
        (*recorderBufferQueue)->Enqueue(recorderBufferQueue, recordBuffer->getRecordBuffer(), recorderSize);
    }
};


extern "C"
JNIEXPORT void JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_opensles_OpenSLESActivity_startRecord(
        JNIEnv *env, jobject instance, jstring path_) {

    if (finish) {
        return;
    }

    const char *path = env->GetStringUTFChars(path_, 0);
    finish = false;
    pcmFile = fopen(path, "w"); // write
    recordBuffer = new RecordBuffer(recorderSize); // 创建缓存对象

    SLresult result;

    // 1. 创建引擎
    slCreateEngine(&slObjectEngine, 0, NULL, 0, NULL, NULL);
    (*slObjectEngine)->Realize(slObjectEngine, SL_BOOLEAN_FALSE);
    (*slObjectEngine)->GetInterface(slObjectEngine, SL_IID_ENGINE, &engineItf);



    //      设置IO设备（麦克风）
    SLDataLocator_IODevice loc_dev = {SL_DATALOCATOR_IODEVICE,
                                      SL_IODEVICE_AUDIOINPUT,
                                      SL_DEFAULTDEVICEID_AUDIOINPUT,
                                      NULL};
    SLDataSource audioSrc = {&loc_dev, NULL};

    //      设置buffer队列
    SLDataLocator_AndroidSimpleBufferQueue loc_bq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
            2
    };
    //      设置录制规格：PCM、2声道、44100HZ、16bit
    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_44_1,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN
    };
    SLDataSink audioSnk = {&loc_bq, &format_pcm};

    const SLInterfaceID id[1] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE};
    const SLboolean req[1] = {SL_BOOLEAN_FALSE};


    // 2. 创建录音器
    result = (*engineItf)->CreateAudioRecorder(engineItf, &recordObj, &audioSrc, &audioSnk,
                                               1, id, req);
    if (SL_RESULT_SUCCESS != result) {
        return;
    }

    result = (*recordObj)->Realize(recordObj, SL_BOOLEAN_FALSE);
    if (SL_RESULT_SUCCESS != result) {
        return;
    }

    (*recordObj)->GetInterface(recordObj, SL_IID_ENGINE, &recordItf);
    // 获取录音队列
    (*recordObj)->GetInterface(recordObj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE,
                               &recorderBufferQueue);



    // 上面录音的数据存在队列中，下面队列中放置recordBuffer存储数据
    // 初始化bugger大小最大4096
    (*recorderBufferQueue)->Enqueue(recorderBufferQueue, recordBuffer->getRecordBuffer(), recorderSize);

    (*recorderBufferQueue)->RegisterCallback(recorderBufferQueue, bqRecorderCallback, NULL);

    (*recordItf)->SetRecordState(recordItf, SL_RECORDSTATE_RECORDING);// 录制状态

    env->ReleaseStringUTFChars(path_, path);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_advd_audioandvideolearn_1demo_1master_opensles_OpenSLESActivity_stopRecord(
        JNIEnv *env, jobject instance) {
    finish = true;
}

//  -------------------  openSLES 处理音频  end ----------------------------
