//
// Created by pc on 2019/1/8.
//

#ifndef AUDIOANDVIDEOLEARN_DEMOMASTER_ANDROIDLOG_H
#define AUDIOANDVIDEOLEARN_DEMOMASTER_ANDROIDLOG_H

#include <android/log.h>
#define LOGD(FORMAT,...) __android_log_print(ANDROID_LOG_DEBUG,"ycl",FORMAT,##__VA_ARGS__);
#define LOGE(FORMAT,...) __android_log_print(ANDROID_LOG_ERROR,"ycl",FORMAT,##__VA_ARGS__);



#endif //AUDIOANDVIDEOLEARN_DEMOMASTER_ANDROIDLOG_H
