#ifndef LED_SEQUENCE
#define LED_SEQUENCE


class LedSequence 
{
public:
    LedSequence();

private:

    int onTime = 500; // Time LEDs stay on, in miliseconds

    int seq[1028];
};

#endif
