//
// Created by pc on 2019/1/8.
//

#ifndef AUDIOANDVIDEOLEARN_DEMOMASTER_RECORDBUFFER_H
#define AUDIOANDVIDEOLEARN_DEMOMASTER_RECORDBUFFER_H

/**
 * 流式录音，2个buffer,每次录音取出一个数据存储pcm数据
 */
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
