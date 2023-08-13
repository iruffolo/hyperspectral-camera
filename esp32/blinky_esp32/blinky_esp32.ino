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

char msg[1024];

//Violet to Far red
int const leds[] = {12, 27, 15, 32, 14, SCL, SDA, SCK, A5, 33, RX, TX, 19};
int const num_leds = 13;
int const WHITE_LED = SDA;

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

    // cycle_colors_neopixel(freq);

    cycle_led_sequence(0, 1000000);
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

    // Recv bluetooth, print to serial
    while (SerialBT.available()) {
        char c = SerialBT.read();

        Serial.write(c);

        if (isdigit(c))
        {
            int x = atoi(&c);
            new_freq = new_freq*10^count + x;
        }
    }

    freq = new_freq = 0 ? freq : new_freq;

    sprintf(msg, "Setting freq: %d", freq);
    Serial.write(msg);

    Serial.write("\n\r")
}
