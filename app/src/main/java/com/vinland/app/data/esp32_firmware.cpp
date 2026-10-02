#include <HardwareSerial.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// Wi-Fi Credentials
const char* ssid = "Vinland";
const char* password = "Vinland123";

// Your Firestore Live Sensor Device Endpoint
const char* firestoreLiveEndpoint = "https://firestore.googleapis.com/v1/projects/vinland-b6e0b/databases/(default)/documents/sensor_devices/VINLAND-ESP32-01";

// RS485 / MAX485 pin definitions for ESP32
const int RXD2 = 16;
const int TXD2 = 17;
const int DE_RE_PIN = 4; // Set to -1 if your MAX485 module has auto-flow control

// Modbus request frame to read NPK and soil parameters (Registers 0x0000 to 0x0006)
const byte nitroQuery[] = {0x01, 0x03, 0x00, 0x00, 0x00, 0x07, 0x04, 0x08};

void setup() {
  Serial.begin(115200);
  Serial2.begin(9600, SERIAL_8N1, RXD2, TXD2);

  if (DE_RE_PIN != -1) {
    pinMode(DE_RE_PIN, OUTPUT);
    digitalWrite(DE_RE_PIN, LOW); // Set to Receive mode
  }

  // Connect to Wi-Fi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected!");
}

void loop() {
  byte values[19];

  if (sendModbusRequest(nitroQuery, sizeof(nitroQuery), values, 19)) {
    // Parse sensor response (Standard 7-in-1 NPK / Soil Sensor Modbus mapping)
    float moisture = ((values[3] << 8) | values[4]) / 10.0;
    float ph = ((values[9] << 8) | values[10]) / 10.0;
    float nitrogen = (values[11] << 8) | values[12];
    float phosphorus = (values[13] << 8) | values[14];
    float potassium = (values[15] << 8) | values[16];

    Serial.printf("Soil Readings -> Moisture: %.1f%% | pH: %.1f | N: %.0f mg/kg | P: %.0f mg/kg | K: %.0f mg/kg\n",
                  moisture, ph, nitrogen, phosphorus, potassium);

    // Send data to Vinland Android App via Firestore Live Stream
    uploadLiveSensorToVinland(moisture, ph, nitrogen, phosphorus, potassium);
  } else {
    Serial.println("Failed to read from NPK sensor. Check wiring (A/B polarity & 12V power).");
  }

  delay(10000); // Read every 10 seconds
}

bool sendModbusRequest(const byte* query, size_t queryLen, byte* response, size_t expectedLen) {
  while (Serial2.available()) {
    Serial2.read();
  }

  if (DE_RE_PIN != -1) digitalWrite(DE_RE_PIN, HIGH); // Transmit mode
  Serial2.write(query, queryLen);
  Serial2.flush();
  if (DE_RE_PIN != -1) digitalWrite(DE_RE_PIN, LOW);   // Receive mode

  unsigned long startTime = millis();
  size_t bytesRead = 0;

  while (millis() - startTime < 1000) { // 1 second timeout
    while (Serial2.available() && bytesRead < expectedLen) {
      response[bytesRead++] = Serial2.read();
    }
    if (bytesRead >= expectedLen) break;
  }

  return bytesRead >= expectedLen;
}

void uploadLiveSensorToVinland(float moisture, float ph, float n, float p, float k) {
  if (WiFi.status() == WL_CONNECTED) {
    HTTPClient http;
    http.begin(firestoreLiveEndpoint);
    http.addHeader("Content-Type", "application/json");

    // ArduinoJson v7 syntax
    JsonDocument doc;
    JsonObject fields = doc["fields"].to<JsonObject>();

    fields["moisture"].to<JsonObject>()["doubleValue"] = moisture;
    fields["ph"].to<JsonObject>()["doubleValue"] = ph;
    fields["nitrogen"].to<JsonObject>()["doubleValue"] = n;
    fields["phosphorus"].to<JsonObject>()["doubleValue"] = p;
    fields["potassium"].to<JsonObject>()["doubleValue"] = k;
    fields["timestamp"].to<JsonObject>()["integerValue"] = String(millis());

    String jsonBody;
    serializeJson(doc, jsonBody);

    // Send PATCH request to update 'sensor_devices/VINLAND-ESP32-01' document
    int httpResponseCode = http.sendRequest("PATCH", jsonBody);
    if (httpResponseCode > 0) {
      Serial.println("Successfully synced live sensor data to Vinland App!");
    } else {
      Serial.printf("Error syncing to Vinland: %s\n", http.errorToString(httpResponseCode).c_str());
    }
    http.end();
  }
}
