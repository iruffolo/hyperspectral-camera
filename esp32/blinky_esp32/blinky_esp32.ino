#include <stdio.h>
#include "BluetoothSerial.h"
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif
#include "seq.h"


/********** GLOBALS **********/

// Bluetooth serial object
BluetoothSerial SerialBT;

TaskHandle_t btThread;

// Frequency to cycle LEDs 
int freq = 500;

// Flag indicating msg has been received on Bluetooth
bool msg_recv = false;

int const WHITE_LED = 19;
int const BLUE_LED = 15;
int const RED_LED = 33;

// Violet to Far red
int const leds[] = {
    12,     // VIOLET (0)
    27,     // ROYAL BLUE (1)
    15,     // BLUE (2)
    32,     // CYAN (3)
    14,     // GREEN (4)
    SCL,    // LIME (5)
    SCK,    // AMBER (6)
    A5,     // RED ORANGE (7)
    33,     // RED (8)
    TX,     // DEEP RED (9)
    RX,     // FAR RED (10)
    19,     // WHITE (11)
    SDA,    // MINT (12)
};
int const num_leds = 13;

// Current mode for main loop
enum Mode {
    PASSIVE,
    RS_CAPTURE,
    GT_CAPTURE,
    W_CAPTURE,
    RESET,
    LIGHT,
    DEBUG_LED
};
enum Mode curr_mode = Mode::PASSIVE;

// Index for LED in ground truth mode
int curr_gt_led = 0;

// Index for random LED sequence pattern in rolling shutter mode
int delay_on_rs_us = 10;
int delay_off_rs_us = 0;
int delay_white_multiple = 1;
int num_leds_mplx = 1;
int curr_rs_seq = 0;

// Delay for debug
int delay_debug_ms = 5000;
int curr_light = 0;

// Sequence length 
int num_rows = 500;

//!
//! Setup function to initialize peripherals etc.
//! 
void setup() {
    // Start serial connection for debugging
    Serial.begin(115200); 
    Serial.println("Hello!");

    //Bluetooth device name
    SerialBT.begin("ESP32 LED Board"); 

    // Setup external LEDs
    for (int i = 0; i < num_leds; i++) {
        pinMode(leds[i], OUTPUT);
        digitalWrite(leds[i], LOW);
    }

    xTaskCreatePinnedToCore(
        read_bluetooth,     // Task function 
        "btThread",
        10000,
        NULL,
        tskIDLE_PRIORITY,   // Priority
        &btThread,          // Task handle
        0);                 // Pin task to core 0
}


//!
//! LED control loop - loop runs through different modes depending on commands 
//! received on bluetooth. (loop runs on core 1)
//!
void loop () { 

    switch (curr_mode) {
        // Capture rolling shutter images with LEDs cycling
        case Mode::RS_CAPTURE:
            // Repeat sequence until RESET command is received
            cycle_led_sequence(curr_rs_seq);
            break;

        // Capture GT image with LED static 
        case Mode::GT_CAPTURE:
            digitalWrite(leds[curr_gt_led], HIGH);
            curr_mode = Mode::PASSIVE;
            break;

        // Capture W image with LED static 
        case Mode::W_CAPTURE:
            digitalWrite(WHITE_LED, HIGH);
            curr_mode = Mode::PASSIVE;
            break;

        // Turn off all LEDs
        case Mode::RESET:
            for (int i = 0; i < num_leds; i++) {
                digitalWrite(leds[i], LOW);
            }
            curr_mode = Mode::PASSIVE;
            break;

        // Slowly turn on each LED
        case Mode::DEBUG_LED:
            debug_leds();
            curr_mode = Mode::PASSIVE;
            break;

        case Mode::LIGHT:
            digitalWrite(leds[curr_light], HIGH);
            curr_mode = Mode::PASSIVE;
            break;

        case Mode::PASSIVE:
        default:
            break;
    }
}


//!
//! Cycles through the LEDs at some frequency
//!
void cycle_led_sequence(int seq_num) {

    // Start sequence with BLUE BLACK RED 
    // BLUE
    digitalWrite(BLUE_LED, HIGH);
    delayMicroseconds(delay_on_rs_us);
    digitalWrite(BLUE_LED, LOW);
    delayMicroseconds(delay_off_rs_us);
    // Everything off (BLACK)
    delayMicroseconds(delay_on_rs_us * delay_white_multiple);
    delayMicroseconds(delay_off_rs_us);
    // RED
    digitalWrite(RED_LED, HIGH);
    delayMicroseconds(delay_on_rs_us);
    digitalWrite(RED_LED, LOW);
    delayMicroseconds(delay_off_rs_us);

    switch (num_leds_mplx) {
        case 1: 
            toggle_leds(seq_0, seq_num);
            break;
        case 2:
            // toggle_leds(seq_1, seq_num);
            // break;
        case 3:
            // toggle_leds(seq_2, seq_num);
            // break;
        // case 4:
        //     toggle_leds(seq_3, seq_num);
        // case 5:
        //     toggle_leds(seq_4, seq_num);
        // case 6:
        //     toggle_leds(seq_5, seq_num);
        // case 7:
        //     toggle_leds(seq_6, seq_num);
        // case 8:
        //     toggle_leds(seq_7, seq_num);
        // case 9:
        //     toggle_leds(seq_9, seq_num);
        default:
            toggle_leds(seq_0, seq_num);
    }
}

template<typename T> 
void toggle_leds(T seq, int seq_num)
{
    num_rows = num_rows > NUM_ROW[seq_num] ? NUM_ROW[seq_num] : num_rows;

    // Rotate through random LED sequence
    for (int i = 0; i < num_rows; i++) {

        // Turn all LEDs ON 
        for (int j = 0; j < num_leds_mplx; j++) {
            int led = leds[seq[seq_num][i][j]];
            digitalWrite(led, HIGH);
        }

        delayMicroseconds(delay_on_rs_us);

        // Turn all LEDs OFF
        for (int j = 0; j < num_leds_mplx; j++) {
            int led = leds[seq[seq_num][i][j]];
            digitalWrite(led, LOW);
        }

        delayMicroseconds(delay_off_rs_us);
    }
}

//!
//! Seperate thread for reading messages from bluetooth 
//!
//! Protocal is as follows:
//! Every message is expected to contain the mode, a value, and be terimated by
//! a newline character, e.g. MODE:VALUE\n
//!     
//! A sample message could be - GT:3\n
//! This message indicates the current mode should be in ground truth, and 
//! be capturing the 3rd image in the sequence.
//!
void read_bluetooth(void* pvParameters) {

    // Buffer for receiving messages
    char bt_msg[1024] = {};

    for (;;) {

        if (SerialBT.available()) {

            // Recv bluetooth msg
            while (SerialBT.available()) {
                const char c = SerialBT.read();

                if (c == '\n') {
                    msg_recv = true;
                    break;
                }

                strncat(bt_msg, &c, 1);
            }

            if (msg_recv) {
                Serial.write(bt_msg);
                Serial.write("\n\r");

                // Protocol is expecting a message of format MODE:VALUE 
                char *mode;
                char *value;

                // Parse message on ':' delimiter
                mode = strtok(bt_msg, ":");
                value = strtok(NULL, ":");

                if (value != NULL) {
                    int v = atoi(value);

                    if (strcmp(mode, "RS") == 0) {
                        Serial.write("Rolling shutter mode");
                        curr_mode = Mode::RS_CAPTURE;
                        curr_rs_seq = v;
                    }
                    else if (strcmp(mode, "GT") == 0) {
                        Serial.write("Ground truth mode");
                        curr_mode = Mode::GT_CAPTURE;
                        curr_gt_led = v < num_leds ? v : 0;
                    }
                    else if (strcmp(mode, "W") == 0) {
                        Serial.write("White LED mode");
                        curr_mode = Mode::W_CAPTURE;
                    }
                    else if (strcmp(mode, "LEDON") == 0) {
                        delay_on_rs_us = v;
                    }
                    else if (strcmp(mode, "LEDOFF") == 0) {
                        delay_off_rs_us = v;
                    }
                    else if (strcmp(mode, "WHITEON") == 0) {
                        delay_white_multiple = v;
                    }
                    else if (strcmp(mode, "LEDMPLX") == 0) {
                        num_leds_mplx = v < 10 ? v : 1;
                    }
                    else if (strcmp(mode, "NROWS") == 0) {
                        num_rows = v;
                    }
                    else if (strcmp(mode, "RESET") == 0) {
                        curr_mode = Mode::RESET;
                    }
                    else if (strcmp(mode, "DEBUG") == 0) {
                        curr_mode = Mode::DEBUG_LED;
                        delay_debug_ms = v;
                    }
                    else if (strcmp(mode, "LIGHT") == 0) {
                        curr_mode = Mode::LIGHT;
                        curr_light = v < num_leds ? v : 0;
                    }
                    else {
                        curr_mode = Mode::PASSIVE;
                    }
                }

                // Reset message 
                memset(bt_msg, 0, 1024);
                Serial.write("\n\r"); 

                msg_recv = false;
            }
        }
    }
}

void debug_leds() {

    char debug_msg[1024];

    for (int i = 0; i < num_leds; i++) {

        sprintf(debug_msg, "Turning on LED: %d\n\r", i);
        Serial.write(debug_msg);

        digitalWrite(leds[i], HIGH);
        delay(delay_debug_ms);
        digitalWrite(leds[i], LOW);
    }
}
