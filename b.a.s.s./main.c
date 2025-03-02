const int buttonPin = 2;     // Connect the button here
const int keySwitchPin = 10; // Connect the key switch here
const int ledPin = A0;       // Connect LED to Analog Pin A0

bool buttonPressed = false;      // Tracks if the button has been pressed
bool previousKeyState = HIGH;    // To track previous state of the key switch
bool blinking = true;           // Starts with blinking active
unsigned long lastBlinkTime = 0; // Tracks the last blink time
const unsigned long blinkInterval = 100; // Blink interval in milliseconds

void setup() {
  pinMode(buttonPin, INPUT_PULLUP);
  pinMode(keySwitchPin, INPUT_PULLUP);
  pinMode(ledPin, OUTPUT); // Use A0 as a digital output
  Serial.begin(9600);
}

void loop() {
  int keyState = digitalRead(keySwitchPin);
  int buttonState = digitalRead(buttonPin);

  // Turn LED ON or blinking when the key is turned
  if (keyState == LOW) {
    if (previousKeyState == HIGH) { // Only send when the key has been turned ON
      Serial.println("KEY_TURNED_ON");
      previousKeyState = LOW; // Update the previous state to LOW (key ON)
    }
  } else {
    digitalWrite(ledPin, LOW); // Turn off LED
    if (previousKeyState == LOW) { // Only send when the key has been turned OFF
      Serial.println("KEY_TURNED_OFF");
      previousKeyState = HIGH; // Update the previous state to HIGH (key OFF)
      blinking = true; //reset to blinking when key is turned off.
    }
  }

  // Button press detection
  if (keyState == LOW && buttonState == HIGH) {
    if (!buttonPressed) {
      Serial.println("BUTTON_PRESSED");
      buttonPressed = true;
      blinking = !blinking; // Toggle blinking state
    }
  } else {
    buttonPressed = false;
  }

  // Blinking logic
  if (keyState == LOW && blinking) {
    unsigned long currentTime = millis();
    if (currentTime - lastBlinkTime >= blinkInterval) {
      digitalWrite(ledPin, !digitalRead(ledPin)); // Toggle LED state
      lastBlinkTime = currentTime;
    }
  } else if (keyState == LOW && !blinking) {
    digitalWrite(ledPin, HIGH); //ensure led is on when not blinking and key is on.
  }

  delay(50); // Small debounce delay
}