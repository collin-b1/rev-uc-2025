#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <ESP8266HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <array>

// ESP8266    Arduino
// GPIO0      D3        NOT ALLOWED
// GPIO1      D10
// GPIO2      D4        NOT ALLOWED
// GPIO3      RX
// GPIO4      D2
// GPIO5      D1
// GPIO12     D6
// GPIO13     D7
// GPIO14     D5
// GPIO15     D8
// GPIO16     D0

constexpr int MOTOR_PIN_1 = 12;
constexpr int MOTOR_PIN_2 = 4;

// Buffer rate of playback in ms
constexpr unsigned long PLAYBACK_BUFFER_RATE = 100;

// Network SSID
const char* ssid = "1819_Guest";

// Network Password
const char* password = "";

// Last loop system time
unsigned long lastMotorTime = 0;

// Buffer
bool buffer[256];
uint8 size = 0;
size_t frame = 0;

// bool buffer[1024] = { 0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1,1,1,1,1,0,0,0,1,1,1,1,1,1,1,1,1,0,0,0,0,1,1,1,1 };
// size_t sampleBufferSize = sizeof(sampleBuffer) / sizeof(sampleBuffer[0]);

// JsonDocument doc();
ESP8266WebServer server(80);

void setup() {
  // Board Setup
  Serial.begin(9600);
  delay(100);
  Serial.print("Connecting to ");
  Serial.println(ssid);

  // Motor Setup
  pinMode(MOTOR_PIN_1, OUTPUT);
  pinMode(MOTOR_PIN_2, OUTPUT);

  // WiFi Setup
  WiFi.begin(ssid);

  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    Serial.print(".");
  }

  Serial.println("\nWiFi connected..!");
  Serial.println(WiFi.localIP());

  // Webserver Setup
  server.on("/", handleOnConnect);
  server.on("/query", HTTP_POST, handleQuery);
  server.begin();
  Serial.println("HTTP server started");
}

void loop() {
  // Listen for requests
  server.handleClient();

  // Handle buffer
  if (frame < size) {
    if (frame == 0) {
      setTorso(true);
    }
    unsigned long currentMillis = millis();

    if (currentMillis - lastMotorTime >= PLAYBACK_BUFFER_RATE) {
      lastMotorTime = currentMillis;
      
      // playBackArray(sampleBuffer, sizeof(sampleBuffer) / sizeof(sampleBuffer[0]));
      setMouth(buffer[frame]);
      ++frame;
    }
  } else if (frame == size && size != 0) {
    setTorso(false);
    frame = size = 0;
  }
}

void setMouth(bool state) {
  // Serial.print("Setting mouth to ");
  Serial.print(state ? "M" : "m");
  digitalWrite(MOTOR_PIN_1, state ? LOW : HIGH);
}

void setTorso(bool state) {
  // Serial.print("Setting torso to ");
  Serial.print(state ? "T" : "t");
  digitalWrite(MOTOR_PIN_2, state ? HIGH : LOW);
}

void handleOnConnect() {
  Serial.println("Welcome.");
  // This function sends a 200 on successful connection, and then runs the SendHTML() function.
  server.send(200, "application/json", "{ \"message\": \"Success.\" }");
}

void handleQuery() {
  if (server.hasArg("plain")) {
    String body = server.arg("plain");
    // Serial.println("Received POST data: " + body);

    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, body);

    // Handle error
    if (err) {
      // Serial.println("Deserialize Error");
      server.send(500, "application/json", "{\"message:\": \"Server failed to parse JSON object.\"}");
      return;
    }

    JsonArray jsonArray = doc["buffer"].as<JsonArray>();

    if (jsonArray.isNull()) {
      server.send(400, "application/json", "{\"message\":\"Bad request: Missing array.\"}");
      return;
    }

    frame = 0;
    size = min((size_t)256, jsonArray.size());

    // Serial.print("Parsed array: ");
    for (int i = 0; i < size; ++i) {
      buffer[i] = jsonArray[i].as<bool>();
      // Serial.print(buffer[i]);
      // Serial.print(",");
    }
    // Serial.println();

    server.send(200, "application/json", "{\"message\":\"Success.\"}");
  } else {
    server.send(400, "application/json", "{\"message\":\"Bad request: No JSON object received.\"}");
  }
}
