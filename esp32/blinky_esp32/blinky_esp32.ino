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

// LEDs
int const WHITE_LED = SDA;
// Violet to Far red
int const leds[] = {12, 27, 15, 32, 14, SCL, SDA, SCK, A5, 33, RX, TX, 19};
int const num_leds = 13;

// Current mode for main loop
enum Mode {
    PASSIVE,
    RS_CAPTURE,
    GT_CAPTURE,
    RESET
};
enum Mode curr_mode = Mode::PASSIVE;

// Index for LED in ground truth mode
int curr_gt_led = 0;

// Index for random LED sequence pattern in rolling shutter mode
int delay_rs_us = 10;
int curr_rs_seq = 0;

//!
//! Setup function to initialize peripherals etc.
//! 
void setup() {
    // Start serial connection for debugging
    Serial.begin(115200); 
    Serial.println("Hello!");

    //Bluetooth device name
    SerialBT.begin("ESP32test"); 

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

    delay(500);
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
            cycle_led_sequence(curr_rs_seq, delay_rs_us);
            break;

        // Capture GT image with LED static 
        case Mode::GT_CAPTURE:
            digitalWrite(leds[curr_gt_led], HIGH);
            curr_mode = Mode::PASSIVE;
            break;

        // Turn off all LEDs
        case Mode::RESET:
            for (int i = 0; i < num_leds; i++) {
                digitalWrite(leds[i], LOW);
            }
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
void cycle_led_sequence(int seq_num, int delay_us) {

    // Start sequence with white LED
    digitalWrite(WHITE_LED, HIGH);
    delayMicroseconds(delay_us);
    digitalWrite(WHITE_LED, LOW);

    // Rotate through random LED sequence
    for (int i = 0; i < NUM_ROW; i++) {
        int led = leds[seq[seq_num][i]];

        digitalWrite(led, HIGH);
        delayMicroseconds(delay_us);
        digitalWrite(led, LOW);
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
    char bt_msg[1024];

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

                    // Rolling shutter mode
                    if (strcmp(mode, "RS") == 0) {
                        Serial.write("Rolling shutter mode");
                        curr_mode = Mode::RS_CAPTURE;
                        curr_rs_seq = v;
                    }
                    // Ground truth mode
                    else if (strcmp(mode, "GT") == 0) {
                        Serial.write("Ground truth mode");
                        curr_mode = Mode::GT_CAPTURE;
                        curr_gt_led = v < num_leds ? v : 0;
                    }
                    // Reset all LEDs to off 
                    else if (strcmp(mode, "RESET") == 0) {
                        curr_mode = Mode::RESET;
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
