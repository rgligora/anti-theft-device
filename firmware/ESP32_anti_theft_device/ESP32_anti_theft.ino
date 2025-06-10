/*
  ESP32-C6 “Antitheft” Device Firmware for ThingsBoard MVP
  ---------------------------------------------------------
  Components:
   - ESP32-C6 DevKitC-1
   - SEN-MPU6050 (I2C accelerometer + gyro)
   - Red LED (GPIO5 via 1 kΩ resistor)
   - KY-012 Active Buzzer (GPIO18)
  
    Behavior:
   1. On boot, connect to WiFi.
   2. MQTT → ThingsBoard at 161.53.133.253:1883, auth via TB_DEVICE_TOKEN.
   3. Subscribe to RPC: "v1/devices/me/rpc/request/+#".
      • RPC method "armDevice": set `armed = true`, send the current location back to TB (attributes + telemetry).
      • RPC method "disarmDevice": set `armed = false`, stop alarm.
   4. When `armed == true`
      • poll MPU6050 accel data every loop:
        if (sqrt(af² + bf² + cf²) > ACCEL_THRESHOLD && !inAlert) → trigger alert.
   5. On alert:
      • Turn buzzer ON + start LED blinking.
      • Immediately publish full telemetry JSON with:
          `{ "motion_detected": true, "gyro_x":…, "gyro_y":…, "gyro_z":…, "armed": true, "latitude":…, "longitude":… }`
      • Until disarmed, every 5 s publish full telemetry including
          `"motion_detected": true`, gyro readings, armed flag, and mocked GPS movement.
   6. When disarmed, telemetry continues every 5 s but with `"motion_detected": false` and no GPS fields.
*/

#include <WiFi.h>
#include <Wire.h>
#include <MPU6050.h>
#include <PubSubClient.h>
#include <math.h>

// ─── USER CONFIG ─────────────────────────────────────────────────────────────
// 1) Wi-Fi credentials:
const char* WIFI_SSID     = "RokoiPhone";
const char* WIFI_PASSWORD = "nesjecamse";

// 2) ThingsBoard Device Access Token:
const char* TB_DEVICE_TOKEN = "jjl0jhykAJF1miCTHxMB";

// 3) ThingsBoard server:
const char* TB_SERVER = "161.53.133.253";
const int   TB_PORT   = 1883;

// 4) Pins:
static const int LED_PIN    = 5;   // Red LED
static const int BUZZER_PIN = 18;  // KY-012 active buzzer

// 5) Motion threshold in g:
constexpr float ACCEL_THRESHOLD = 1.85f;

// 6) Intervals:
constexpr unsigned long TELEMETRY_INTERVAL_MS = 5'000UL;  // 5 s

// 7) GPS step distance: 30 km/h → 0.042 km per 5 s
constexpr float DIST_PER_STEP_KM = 0.042f;
// ────────────────────────────────────────────────────────────────────────────────

WiFiClient    wifiClient;
PubSubClient  mqttClient(wifiClient);
MPU6050       mpu;

// State:
bool    armed       = false;
bool    inAlert     = false;
float   currentLat  = 0.0f;
float   currentLon  = 0.0f;

// Cardinal‐direction mock‐GPS
enum Direction { NONE = -1, NORTH = 0, EAST = 1, SOUTH = 2, WEST = 3 };
Direction lastDirection = NONE;

// A simple mock “road”
static const float mockPath[][2] = {
  {45.8150f, 15.9819f},
  {45.8280f, 15.9870f},
  {45.8400f, 15.9900f},
  {45.8520f, 15.9950f},
  {45.8650f, 15.9980f},
  {45.8800f, 16.0020f}
};
static const size_t mockPathLen = sizeof(mockPath)/sizeof(mockPath[0]);

// Forward declarations
void setupWiFi();
void connectMQTT();
void onMqttMessage(char* topic, byte* payload, unsigned int length);
void publishTelemetry();
void checkMotionAndAlert();
void updateGPSMock();

void setup() {
  Serial.begin(115200);
  delay(500);
  randomSeed(esp_random());
  Serial.println("\n--- ESP32 Antitheft Device Starting ---");

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  // I²C for MPU6050
  Wire.begin(21, 22);
  mpu.initialize();
  if (!mpu.testConnection()) {
    Serial.println("ERROR: MPU6050 failed");
    while (1) { delay(1000); }
  }
  Serial.println("MPU6050 OK");

  setupWiFi();

  mqttClient.setServer(TB_SERVER, TB_PORT);
  mqttClient.setCallback(onMqttMessage);
  connectMQTT();
}

void loop() {
  if (!mqttClient.connected()) {
    connectMQTT();
  }
  mqttClient.loop();

  static unsigned long lastTx = 0;
  unsigned long now = millis();

  if (armed) {
    checkMotionAndAlert();
  }

  if (now - lastTx >= TELEMETRY_INTERVAL_MS) {
    lastTx = now;
    publishTelemetry();
  }

  // Blink LED if in alert
  if (inAlert) {
    digitalWrite(LED_PIN, ((now / 500) % 2) ? HIGH : LOW);
  }
}

void setupWiFi() {
  Serial.printf(">> WiFi %s …", WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    delay(250);
    Serial.print(".");
    if (millis() - start > 10000) {
      Serial.println("\n!! WiFi failed, rebooting");
      ESP.restart();
    }
  }
  Serial.printf("\n>> WiFi up, IP=%s\n", WiFi.localIP().toString().c_str());
}

void connectMQTT() {
  while (!mqttClient.connected()) {
    Serial.print("→ MQTT connecting … ");
    if (mqttClient.connect("ESP32Antitheft", TB_DEVICE_TOKEN, nullptr)) {
      Serial.println("OK");
      mqttClient.subscribe("v1/devices/me/rpc/request/+");
      Serial.println("Subscribed to RPC");
    } else {
      Serial.printf("failed rc=%d, retry 5 s\n", mqttClient.state());
      delay(5000);
    }
  }
}

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
  String t = topic;
  String msg;
  for (unsigned i = 0; i < length; i++) msg += (char)payload[i];
  Serial.printf("RPC %s → %s\n", topic, msg.c_str());

  String reqId = t.substring(t.lastIndexOf('/') + 1);

  // armDevice
  if (msg.indexOf("\"method\":\"armDevice\"") >= 0) {
    armed   = true;
    inAlert = false;
    lastDirection = NONE;
    digitalWrite(BUZZER_PIN, LOW);
    digitalWrite(LED_PIN, LOW);
    Serial.println("→ ARMED");

    // start position: pick random point on mockPath
    size_t idx = random(0, mockPathLen);
    currentLat  = mockPath[idx][0];
    currentLon  = mockPath[idx][1];

    String attr = String("{\"armed\":") + String("true") +
                   String(",\"latitude\":")  + String(currentLat, 6) +
                   String(",\"longitude\":") + String(currentLon, 6) +
                   String("}");

    // 2) send as telemetry (so map shows it immediately)
    mqttClient.publish("v1/devices/me/telemetry", attr.c_str());

    // RPC response
    String respT = "v1/devices/me/rpc/response/" + reqId;
    mqttClient.publish(respT.c_str(), "{\"armed\":true}");
  }
  // disarmDevice
  else if (msg.indexOf("\"method\":\"disarmDevice\"") >= 0) {
    armed   = false;
    inAlert = false;
    digitalWrite(BUZZER_PIN, LOW);
    digitalWrite(LED_PIN, LOW);
    Serial.println("→ DISARMED");

    String attr = String("{\"armed\":") + String("false") +
                   String(",\"latitude\":")  + String(currentLat, 6) +
                   String(",\"longitude\":") + String(currentLon, 6) +
                   String("}");

    // 2) send as telemetry (so map shows it immediately)
    mqttClient.publish("v1/devices/me/telemetry", attr.c_str());

    String respT = "v1/devices/me/rpc/response/" + reqId;
    mqttClient.publish(respT.c_str(), "{\"armed\":false}");
  }
}

void publishTelemetry() {
  // read raw motion
  int16_t ax, ay, az, gx, gy, gz;
  mpu.getMotion6(&ax,&ay,&az,&gx,&gy,&gz);

  // compute gyro (°/s)
  float gf = gx/131.0f;
  float hf = gy/131.0f;
  float jf = gz/131.0f;

  String p = "{";
  p += "\"motion_detected\":" + String(inAlert ? "true" : "false") + ",";
  p += "\"gyro_x\":" + String(gf,2) + ",";
  p += "\"gyro_y\":" + String(hf,2) + ",";
  p += "\"gyro_z\":" + String(jf,2) + ",";
  p += "\"armed\":"  + String(armed ? "true" : "false");

  if (inAlert) {
    // move GPS one cardinal step
    updateGPSMock();
    p += ",\"latitude\":"  + String(currentLat,6);
    p += ",\"longitude\":" + String(currentLon,6);
  }

  p += "}";
  mqttClient.publish("v1/devices/me/telemetry", p.c_str());
  Serial.println("Telemetry: " + p);
}

void checkMotionAndAlert() {
  int16_t ax, ay, az, gx, gy, gz;
  mpu.getMotion6(&ax,&ay,&az,&gx,&gy,&gz);

  // accel in g (Z gravity-corrected)
  float af = ax/16384.0f;
  float bf = ay/16384.0f;
  float cf = az/16384.0f - 1.0f;
  float mag = sqrt(af*af + bf*bf + cf*cf);

  if (mag > ACCEL_THRESHOLD && !inAlert) {
    inAlert = true;
    digitalWrite(BUZZER_PIN, HIGH);
    Serial.printf("!!! ALERT mag=%.3f\n", mag);

    // immediate alert telemetry
    publishTelemetry();
    Serial.println("Alert telemetry sent");
  }
}

// Pick a random cardinal direction not opposite of last, then step currentLat/Lon
void updateGPSMock() {
  Direction dir;
  do {
    dir = static_cast<Direction>(random(0,4));  // 0=N,1=E,2=S,3=W
  } while (
    (lastDirection==NORTH && dir==SOUTH) ||
    (lastDirection==SOUTH && dir==NORTH) ||
    (lastDirection==EAST  && dir==WEST)  ||
    (lastDirection==WEST  && dir==EAST)
  );
  lastDirection = dir;

  float dLat = DIST_PER_STEP_KM/111.0f;
  float dLon = DIST_PER_STEP_KM/(111.0f * cos(currentLat*PI/180.0f));

  switch(dir) {
    case NORTH: currentLat += dLat; break;
    case SOUTH: currentLat -= dLat; break;
    case EAST:  currentLon += dLon; break;
    case WEST:  currentLon -= dLon; break;
    default: break;
  }
}
