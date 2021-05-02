#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>

#include "configuration.hpp"

#define HTTP_SERVER_PORT 80

//#define RESET_CONFIGURATION

ESP8266WebServer server(HTTP_SERVER_PORT);
smart_home::Configuration homeCfg;

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

void handleNotFound() {
  server.send(404, "text/plain", "Not Found");
}

void setup() {
  Serial.begin(115200);
  Serial.println();

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  // Just to be sure
  WiFi.setAutoConnect(true);
  WiFi.setAutoReconnect(true);

  struct station_config stationCfg;
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

#ifdef RESET_CONFIGURATION
  homeCfg.setName("new-device");
  homeCfg.setPassword("12345");
  homeCfg.save();
#endif

  homeCfg.load();

  server.on("/ping", HTTP_GET, handlePing);
  server.on("/setup_wifi", HTTP_POST, handleSetupWiFi);
  server.on("/set_builtin_led", HTTP_POST, handleSetBuiltinLED);
  server.onNotFound(handleNotFound);

  const char* headerKeys[] = {"Password"};
  server.collectHeaders(headerKeys, 1);

  server.begin();
  Serial.printf("Started HTTP server on port %d\n", HTTP_SERVER_PORT);
}

void loop() {
  server.handleClient();
}
