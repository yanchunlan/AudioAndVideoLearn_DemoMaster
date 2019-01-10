//
// Created by pc on 2019/1/10.
//

#include "WlQueue.h"

WlQueue::WlQueue() {
    pthread_mutex_init(&mutexPacket, NULL);
    pthread_cond_init(&condPacket, NULL);
}

WlQueue::~WlQueue() {
    pthread_mutex_destroy(&mutexPacket);
    pthread_cond_destroy(&condPacket);
}

int WlQueue::putPcmData(pcmData *data) {
    pthread_mutex_lock(&mutexPacket);
    queuePacket.push(data);
    pthread_cond_signal(&condPacket);
    pthread_mutex_unlock(&mutexPacket);
    return 0;
}

pcmData *WlQueue::getPcmData() {
    pthread_mutex_lock(&mutexPacket);
    pcmData *pkt = NULL;
    if (queuePacket.size() > 0) {
        pkt = queuePacket.front();
        queuePacket.pop();
    } else { // 没有数据一直等待
        pthread_cond_wait(&condPacket, &mutexPacket);
    }
    pthread_mutex_unlock(&mutexPacket);
    return pkt;
}

int WlQueue::clearPcmData() {
    pthread_cond_signal(&condPacket);
    pthread_mutex_lock(&mutexPacket);
    // 循环释放资源
    while (!queuePacket.empty()) {
        pcmData *pkt = queuePacket.front();
        queuePacket.pop();
        free(pkt->getData());
        pkt = NULL;
    }
    pthread_mutex_unlock(&mutexPacket);
    return 0;
}

void WlQueue::release() {
    noticeThread(); // 释放线程
    clearPcmData(); // 清除资源
}

int WlQueue::getPcmDataSize() {
    int size = 0;
    pthread_mutex_lock(&mutexPacket);
    size = queuePacket.size();
    pthread_mutex_unlock(&mutexPacket);
    return size;
}

int WlQueue::noticeThread() {
    pthread_cond_signal(&condPacket);
    return 0;
}
