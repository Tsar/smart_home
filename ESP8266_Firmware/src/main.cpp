#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>

#include "configuration.hpp"

//#define RESET_CONFIGURATION  // Usage: uncomment; build & upload & run; comment; build & upload & run

#define HTTP_SERVER_PORT 80

#define INPUT_PIN 14  // for 50 Hz meander

#define OUTPUT_PINS_COUNT VALUES_COUNT
const uint8_t outputPins[OUTPUT_PINS_COUNT] = {4, 5, 12, 13};

volatile uint8_t outputPinStates[OUTPUT_PINS_COUNT] = {};
volatile uint32_t offsetMicros[OUTPUT_PINS_COUNT] = {};

ESP8266WebServer server(HTTP_SERVER_PORT);
smart_home::Configuration homeCfg;

ICACHE_RAM_ATTR void onTimerISR() {
  const uint8_t i = 0;  // working only for pin 4 currently
  switch (outputPinStates[i]) {
    case 0:
      timer1_write(500);
      digitalWrite(outputPins[i], HIGH);
      outputPinStates[i] = 1;
      break;
    case 1:
      timer1_write(49500);
      digitalWrite(outputPins[i], LOW);
      outputPinStates[i] = 2;
      break;
    case 2:
      timer1_write(500);
      digitalWrite(outputPins[i], HIGH);
      outputPinStates[i] = 3;
      break;
    case 3:
      digitalWrite(outputPins[i], LOW);
      outputPinStates[i] = 4;
      break;
  }
}

ICACHE_RAM_ATTR void onInputFall() {
  timer1_write(5 * offsetMicros[0]);
  outputPinStates[0] = 0;
}

void fillOffsetMicros() {
  for (uint8_t i = 0; i < OUTPUT_PINS_COUNT; ++i) {
    offsetMicros[i] = homeCfg.getValue(i);
  }
}

void fastBlinkForever() {
  while (true) {
    digitalWrite(LED_BUILTIN, LOW);
    delay(30);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(30);
  }
}

void enableAccessPoint() {
  uint8_t mac[WL_MAC_ADDR_LENGTH];
  WiFi.macAddress(mac);
  char softAPName[32] = {};
  sprintf(softAPName, "SmartHomeDevice_%02X%02X%02X", mac[3], mac[4], mac[5]);

  Serial.printf("Enabling access point '%s' ... ", softAPName);
  const bool result = WiFi.softAP(softAPName, "setup12345");
  if (!result) {
    Serial.println("Failed");
    fastBlinkForever();
    return;
  }

  const String ip = WiFi.softAPIP().toString();
  homeCfg.updateIP(ip);
  Serial.printf("Access point enabled, IP: %s\n", ip.c_str());
}

bool connectToWiFiOrEnableAP(const char* ssid = 0, const char* passphrase = 0) {
  digitalWrite(LED_BUILTIN, LOW);

  if (ssid != 0) {
    Serial.printf("Connecting to wi-fi: SSID [%s], passphrase [%s]\n", ssid, passphrase);
    WiFi.disconnect(true);
    WiFi.begin(ssid, passphrase);
  } else {
    Serial.println("Waiting for connect to wi-fi");
    WiFi.begin();  // also works without this line
  }

  const int8_t result = WiFi.waitForConnectResult(30000);
  if (result != WL_CONNECTED) {
    Serial.printf("Failed to connect, status: %d\n", result);
    enableAccessPoint();
    return false;
  }

  const String ip = WiFi.localIP().toString();
  homeCfg.updateIP(ip);
  Serial.printf("Connected, IP: %s\n", ip.c_str());
  digitalWrite(LED_BUILTIN, HIGH);
  return true;
}

bool checkPassword() {
  if (server.hasHeader("Password") && server.header("Password") == homeCfg.getPassword()) {
    return true;
  }
  server.send(403, "text/plain", "Forbidden");
  return false;
}

void sendBadRequest() {
  server.send(400, "text/plain", "Bad Request");
}

void handlePing() {
  if (!checkPassword()) return;

  server.send(200, "text/plain", "OK");
}

void handleSetupWiFi() {
  if (!checkPassword()) return;

  if (!server.hasArg("ssid") || !server.hasArg("passphrase")) {
    sendBadRequest();
    return;
  }

  if (connectToWiFiOrEnableAP(server.arg("ssid").c_str(), server.arg("passphrase").c_str())) {
    server.send(200, "text/plain", "WIFI_CONNECT_OK");
    delay(10);
    WiFi.softAPdisconnect(true);
    Serial.println("Disabled access point because connected to wi-fi");
  } else {
    server.send(200, "text/plain", "WIFI_CONNECT_FAILED");
  }
}

void handleSetBuiltinLED() {
  if (!checkPassword()) return;

  if (!server.hasArg("state")) {
    sendBadRequest();
    return;
  }

  const auto state = server.arg("state");
  if (state == "on") {
    digitalWrite(LED_BUILTIN, LOW);
    server.send(200, "text/plain", "LED_IS_ON");
  } else if (state == "off") {
    digitalWrite(LED_BUILTIN, HIGH);
    server.send(200, "text/plain", "LED_IS_OFF");
  } else {
    sendBadRequest();
  }
}

void handleGetValues() {
  if (!checkPassword()) return;

  String response = "Values:";
  for (uint8_t i = 0; i < VALUES_COUNT; ++i) {
    response += " " + String(homeCfg.getValue(i));
  }
  server.send(200, "text/plain", response + "\n");
}

void handleSetValues() {
  if (!checkPassword()) return;

  bool anythingChanged = false;
  for (uint8_t i = 0; i < VALUES_COUNT; ++i) {
    const String argName = "v" + String(i);
    if (server.hasArg(argName)) {
      const uint32_t value = server.arg(argName).toInt();
      if (value != homeCfg.getValue(i)) {
        homeCfg.setValue(i, value);
        anythingChanged = true;
      }
    }
  }

  if (anythingChanged) {
    fillOffsetMicros();
    homeCfg.save();
    server.send(200, "text/plain", "Accepted\n");
  } else {
    server.send(200, "text/plain", "Nothing changed\n");
  }
}

void handleNotFound() {
  server.send(404, "text/plain", "Not Found");
}

void setup() {
  for (uint8_t i = 0; i < OUTPUT_PINS_COUNT; ++i) {
    pinMode(outputPins[i], OUTPUT);
    digitalWrite(outputPins[i], LOW);
  }

#ifdef RESET_CONFIGURATION
  homeCfg.resetAndSave();
#endif
  homeCfg.load();
  fillOffsetMicros();
  Serial.printf("Device name: %s, HTTP password: '%s'\n", homeCfg.getName().c_str(), homeCfg.getPassword().c_str());

  timer1_attachInterrupt(onTimerISR);
  timer1_enable(TIM_DIV16, TIM_EDGE, TIM_SINGLE);

  pinMode(INPUT_PIN, INPUT);
  attachInterrupt(digitalPinToInterrupt(INPUT_PIN), onInputFall, FALLING);

  Serial.begin(115200);
  Serial.println();

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  // Just to be sure
  WiFi.setAutoConnect(true);
  WiFi.setAutoReconnect(true);

  station_config stationCfg;
  if (WiFi.getPersistent()) {
    wifi_station_get_config_default(&stationCfg);
  } else {
    wifi_station_get_config(&stationCfg);
  }

  if (strlen(reinterpret_cast<char*>(stationCfg.ssid)) > 0) {
    Serial.printf("Found wi-fi credentials for SSID '%s'\n", stationCfg.ssid);
    connectToWiFiOrEnableAP();
  } else {
    Serial.println("No wi-fi credentials found");
    enableAccessPoint();
  }

  server.on("/ping", HTTP_GET, handlePing);
  server.on("/setup_wifi", HTTP_POST, handleSetupWiFi);
  server.on("/set_builtin_led", HTTP_POST, handleSetBuiltinLED);
  server.on("/get_values", HTTP_GET, handleGetValues);
  server.on("/set_values", HTTP_ANY, handleSetValues);
  server.onNotFound(handleNotFound);

  const char* headerKeys[] = {"Password"};
  server.collectHeaders(headerKeys, 1);

  server.begin();
  Serial.printf("Started HTTP server on port %d\n", HTTP_SERVER_PORT);
}

void loop() {
  server.handleClient();
}
