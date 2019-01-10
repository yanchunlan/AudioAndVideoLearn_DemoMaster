//
// Created by pc on 2019/1/10.
//

#ifndef AUDIOANDVIDEOLEARN_DEMOMASTER_PCMDATA_H
#define AUDIOANDVIDEOLEARN_DEMOMASTER_PCMDATA_H

class pcmData {

public:
    char *data;
    int size;

    pcmData(char *data, int size);

    ~pcmData();

    int getSize();

    char *getData();
};


#endif //AUDIOANDVIDEOLEARN_DEMOMASTER_PCMDATA_H
