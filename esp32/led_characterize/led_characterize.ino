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

//!
//! Setup function to initialize peripherals etc.
//! 
void setup() {
    // Setup external LEDs
    for (int i = 0; i < num_leds; i++) {
        pinMode(leds[i], OUTPUT);
        digitalWrite(leds[i], LOW);
    }
}

int led = 11;
void loop () {

    for (int i=0; i < num_leds; i++)
    {
        digitalWrite(leds[i], HIGH);
        delayMicroseconds(200); 
        digitalWrite(leds[i], LOW);
        delayMicroseconds(100); 
    }
    delayMicroseconds(1000); 
}

