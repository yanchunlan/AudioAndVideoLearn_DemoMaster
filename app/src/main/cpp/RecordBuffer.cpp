//
// Created by pc on 2019/1/8.
//
#include "RecordBuffer.h"


RecordBuffer::RecordBuffer(int bufferSize) {
    buffer = new short *[2]; // 初始化一个指针，因为是2级指针
    for (int i = 0; i < 2; i++) {
        buffer[i] = new short[bufferSize];
    }
}

// 释放资源
RecordBuffer::~RecordBuffer() {
    for (int i = 0; i < 2; ++i) {
        delete buffer[i];
    }
    delete buffer;
}

short *RecordBuffer::getRecordBuffer() {
    index++;
    if (index > 1) {
        index = 1;
    }
    return buffer[index];
}

short *RecordBuffer::getNowBuffer() {
    return buffer[index];
}
