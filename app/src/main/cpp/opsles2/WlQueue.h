//
// Created by pc on 2019/1/10.
//

#ifndef AUDIOANDVIDEOLEARN_DEMOMASTER_WLQUEUE_H
#define AUDIOANDVIDEOLEARN_DEMOMASTER_WLQUEUE_H

#include "queue"
#include "pcmData.h"
#include "pthread.h"

/**
 * 线程管理数据的类
 */
class WlQueue {

public:
    std::queue<pcmData *> queuePacket;
    pthread_mutex_t mutexPacket;  // 互斥锁
    pthread_cond_t condPacket;  // 条件变量

public:
    WlQueue();

    ~WlQueue();

    // pcmData
    int putPcmData(pcmData *data);

    pcmData *getPcmData();

    int clearPcmData();



    void release();

    int getPcmDataSize();

    int noticeThread();
};


#endif //AUDIOANDVIDEOLEARN_DEMOMASTER_WLQUEUE_H
