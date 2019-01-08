//
// Created by pc on 2019/1/8.
//

#ifndef AUDIOANDVIDEOLEARN_DEMOMASTER_RECORDBUFFER_H
#define AUDIOANDVIDEOLEARN_DEMOMASTER_RECORDBUFFER_H

class RecordBuffer {
public:
    short **buffer; // 16位  二级指针就是一维数组
    int index = -1;

public:
    RecordBuffer(int bufferSize);

    ~RecordBuffer(); // 析构函数 ,在函数执行完之后执行

    short *getRecordBuffer();

    short *getNowBuffer();
};

#endif //AUDIOANDVIDEOLEARN_DEMOMASTER_RECORDBUFFER_H
