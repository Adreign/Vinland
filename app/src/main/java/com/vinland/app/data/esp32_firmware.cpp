#include <HardwareSerial.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>

// Wi-Fi Credentials
const char* ssid = "Vinland";
const char* password = "Vinland123";

// Your Firestore Live Sensor Device Endpoint
const char* firestoreLiveEndpoint = "https://firestore.googleapis.com/v1/projects/vinland-b6e0b/databases/(default)/documents/sensor_devices/VINLAND-ESP32-01";

const int RXD2 = 16;
const int TXD2 = 17;
const int DE_RE_PIN = 4; // Set to -1 if your MAX485 module has auto-flow control

// Read 7 registers starting at 0x0000 from address 1
const byte nitroQuery[] = {0x01, 0x03, 0x00, 0x00, 0x00, 0x07, 0x04, 0x08};

const long bauds[] = {9600, 4800, 2400, 19200};
const int numBauds = sizeof(bauds) / sizeof(bauds[0]);
long lockedBaud = 0; // set once a valid reply is received

void setup() {
  Serial.begin(115200);
  if (DE_RE_PIN != -1) {
    pinMode(DE_RE_PIN, OUTPUT);
    digitalWrite(DE_RE_PIN, LOW); // Receive mode
  }

  // Connect to Wi-Fi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi Connected!");
  Serial.println("NPK sensor debug started");
}

void loop() {
  if (lockedBaud != 0) {
    readOnce(lockedBaud);
  } else {
    for (int i = 0; i < numBauds; i++) {
      if (readOnce(bauds[i])) break;
    }
  }
  delay(10000); // 10 seconds between readings/uploads
}

// Returns true if a valid reply was received at this baud rate
bool readOnce(long baud) {
  Serial2.end();
  Serial2.begin(baud, SERIAL_8N1, RXD2, TXD2);
  delay(50);

  byte buf[32];
  size_t n = sendModbusRequest(nitroQuery, sizeof(nitroQuery), buf, 19);

  Serial.printf("[%ld baud] received %u bytes: ", baud, (unsigned)n);
  for (size_t i = 0; i < n; i++) Serial.printf("%02X ", buf[i]);
  Serial.println();

  if (n >= 19 && buf[0] == 0x01 && buf[1] == 0x03) {
    lockedBaud = baud;

    float moisture    = ((buf[3] << 8) | buf[4]) / 10.0;
    float temperature = (int16_t)((buf[5] << 8) | buf[6]) / 10.0;
    float ec          = (buf[7] << 8) | buf[8];
    float ph          = ((buf[9] << 8) | buf[10]) / 10.0;
    float nitrogen    = (buf[11] << 8) | buf[12];
    float phosphorus  = (buf[13] << 8) | buf[14];
    float potassium   = (buf[15] << 8) | buf[16];

    Serial.println("------ Soil Readings ------");
    Serial.printf("Moisture   : %.1f %%\n", moisture);
    Serial.printf("Temperature: %.1f C\n", temperature);
    Serial.printf("EC         : %.0f uS/cm\n", ec);
    Serial.printf("pH         : %.1f\n", ph);
    Serial.printf("Nitrogen   : %.0f mg/kg\n", nitrogen);
    Serial.printf("Phosphorus : %.0f mg/kg\n", phosphorus);
    Serial.printf("Potassium  : %.0f mg/kg\n", potassium);
    Serial.println("---------------------------");

    // Upload to Vinland Cloud (Firestore)
    uploadLiveSensorToVinland(moisture, ph, nitrogen, phosphorus, potassium);

    return true;
  }

  if (n == 0) Serial.println("  -> nothing received (check power, A/B, RO/DI, DE/RE)");
  else        Serial.println("  -> got bytes but not a valid reply (noise, wrong baud, or wrong address)");
  return false;
}

size_t sendModbusRequest(const byte* query, size_t queryLen, byte* response, size_t expectedLen) {
  while (Serial2.available()) Serial2.read();

  if (DE_RE_PIN != -1) digitalWrite(DE_RE_PIN, HIGH); // Transmit mode
  delay(2);
  Serial2.write(query, queryLen);
  Serial2.flush();
  delay(2);
  if (DE_RE_PIN != -1) digitalWrite(DE_RE_PIN, LOW);  // Receive mode

  unsigned long startTime = millis();
  size_t bytesRead = 0;
  while (millis() - startTime < 1000) {
    while (Serial2.available() && bytesRead < 32) {
      response[bytesRead++] = Serial2.read();
    }
    if (bytesRead >= expectedLen) break;
  }
  return bytesRead;
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

    int httpResponseCode = http.sendRequest("PATCH", jsonBody);
    if (httpResponseCode > 0) {
      Serial.println("Successfully synced live sensor data to Vinland Android App!");
    } else {
      Serial.printf("Error syncing to Vinland: %s\n", http.errorToString(httpResponseCode).c_str());
    }
    http.end();
  }
}
