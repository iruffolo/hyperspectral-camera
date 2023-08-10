#include <Adafruit_NeoPixel.h>
#include <stdio.h>

#include "BluetoothSerial.h"
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif


Adafruit_NeoPixel onePixel = Adafruit_NeoPixel(1, 
                                               PIN_NEOPIXEL, 
                                               NEO_GRB + NEO_KHZ800);
BluetoothSerial SerialBT;

// test

// Frequency to cycle LEDs 
int freq = 500;

char msg[1024];

//Violet to Far red, then white
int leds[] = {12, 27, 15, 32, 14, SCL, SDA, SCK, A5, 33, RX, TX, 19, 21};
int num_leds = 14;

void setup() 
{
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
    for (int i =0; i<num_leds; i++){
        pinMode(leds[i], OUTPUT);
        digitalWrite(leds[i], LOW);
    }
}

void loop() 
{
    cycle_colors(freq);


    if (SerialBT.available()) {
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
    
        sprintf(msg, "Setting freq: %d\n\r", freq);
        Serial.write(msg);
    }
}


void cycle_colors(int freq)
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

    for(int i=0; i<num_leds; i++){
        digitalWrite(leds[i], HIGH);
        // delayMicroseconds(freq);
        delay(freq);
        digitalWrite(leds[i], LOW);
    }
}
