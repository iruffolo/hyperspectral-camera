#include <Adafruit_NeoPixel.h>
#include <stdio.h>

#include "BluetoothSerial.h"
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

#include "seq.h"

Adafruit_NeoPixel onePixel = Adafruit_NeoPixel(1, 
                                               PIN_NEOPIXEL, 
                                               NEO_GRB + NEO_KHZ800);
BluetoothSerial SerialBT;

// Frequency to cycle LEDs 
int freq = 500;

bool msg_recv = false;


//Violet to Far red
int const leds[] = {12, 27, 15, 32, 14, SCL, SDA, SCK, A5, 33, RX, TX, 19};
int const num_leds = 13;
int const WHITE_LED = SDA;

enum Mode {
    PASSIVE,
    RS_CAPTURE,
    GT_CAPTURE
};

enum Mode currMode;


void setup() {
    Serial.begin(115200); // Start serial connection for debugging

    // Say hi!
    Serial.println("Hello!");

    SerialBT.begin("ESP32test"); //Bluetooth device name

    // Neopixel
    onePixel.begin();             // Start the NeoPixel object
    onePixel.clear();             // Set NeoPixel color to black (0,0,0)
    onePixel.setBrightness(20);   // Affects all subsequent settings
    onePixel.show();              // Update the pixel state

    // Setup external LEDs
    for (int i =0; i<num_leds; i++) {
        pinMode(leds[i], OUTPUT);
        digitalWrite(leds[i], LOW);
    }
}


void loop() {
    if (SerialBT.available()) {
        read_bluetooth();
    }

    switch (currMode) {
        // Do nothing, continue to listen on Bluetooth
        case Mode::PASSIVE:
            break;

        // Capture rolling shutter images with LEDs cycling
        case Mode::RS_CAPTURE:
            
            break;

        // Capture sequence of GT images with LEDs static 
        case Mode::GT_CAPTURE:
            
            break;
    }

    // cycle_colors_neopixel(freq);

    // cycle_led_sequence(0, 100000);
}

void cycle_led_sequence(int seq_num, int delay_us) {

    // Start sequence with white LED
    digitalWrite(WHITE_LED, HIGH);
    delayMicroseconds(delay_us);
    digitalWrite(WHITE_LED, LOW);

    // Rotate through random LED sequence
    for (int i=0; i<NUM_ROW; i++) {
        int led = leds[seq[seq_num][i]];
        digitalWrite(led, HIGH);
        delayMicroseconds(delay_us);
        digitalWrite(led, LOW);

        // sprintf(msg, "Idx: %d\tLED: %d\n\r", seq[seq_num][i], led);
        // Serial.write(msg);
    }
}


void cycle_colors_neopixel(int freq)
{
    onePixel.setPixelColor(0, 255, 0, 0);   //  Set pixel 0 to (r,g,b) color value
    onePixel.show();            // Update pixel state
    delay(freq);                // wait for a half second

    onePixel.setPixelColor(0, 0, 0, 255);  
    onePixel.show();
    delay(freq);

    onePixel.setPixelColor(0, 0, 255, 0);
    onePixel.show();
    delay(freq);
}

void read_bluetooth() {
    int count = 0;
    int new_freq = 0;

    char bt_msg[1024];
    char serial_msg[1024];

    // Recv bluetooth msg until receiving a newline char (indicating end of msg)
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

        Serial.write(mode);
        Serial.write("\n\r");

        if (value != NULL) {
            int v = atoi(value);

            // Rolling shutter mode
            if (strcmp(mode, "RS") == 0) {
                Serial.write("Bloop");
            }
            // Ground truth mode
            else if (strcmp(mode, "GT") == 0) {

            }
            // Setting param modes
            // Frequency
            else if (strcmp(mode, "FREQ") == 0) {

            }
        }

        // Reset message 
        memset(bt_msg, 0, 1024);
        msg_recv = false;

        Serial.write("\n\r"); 
    }
}
