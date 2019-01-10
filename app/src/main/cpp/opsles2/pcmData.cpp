//
// Created by pc on 2019/1/10.
//

#include "pcmData.h"
#include "malloc.h"
#include "string.h"

pcmData::pcmData(char *data, int size) {
    this->data = static_cast<char *>(malloc(size));
    this->size = size;
    memcpy(this->data, data, size);
}

pcmData::~pcmData() {
    free(data);
    size = -1;
}

int pcmData::getSize() {
    return this->size;
}

char *pcmData::getData() {
    return this->data;
}
