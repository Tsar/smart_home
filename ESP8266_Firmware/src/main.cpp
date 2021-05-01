#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>

#define HTTP_SERVER_PORT 80

ESP8266WebServer server(HTTP_SERVER_PORT);

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

  Serial.println("Ready");
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

  int8_t result = WiFi.waitForConnectResult(30000);
  if (result != WL_CONNECTED) {
    Serial.printf("Failed to connect, status: %d\n", result);
    enableAccessPoint();
    return false;
  }

  Serial.printf("Connected, IP address: %s\n", WiFi.localIP().toString().c_str());
  digitalWrite(LED_BUILTIN, HIGH);
  return true;
}

void sendBadRequest() {
  server.send(400, "text/plain", "Bad Request");
}

void handleSetupWiFi() {
  if (!server.hasArg("ssid") || !server.hasArg("passphrase")) {
    sendBadRequest();
    return;
  }

  if (connectToWiFiOrEnableAP(server.arg("ssid").c_str(), server.arg("passphrase").c_str())) {
    server.send(200, "text/plain", "WIFI_CONNECT_OK");
    delay(50);
    WiFi.softAPdisconnect(true);
    Serial.println("Disabled access point because connected to wi-fi");
  } else {
    server.send(200, "text/plain", "WIFI_CONNECT_FAILED");
  }
}

void handleSetBuiltinLED() {
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

  struct station_config cfg;
  if (WiFi.getPersistent()) {
    wifi_station_get_config_default(&cfg);
  } else {
    wifi_station_get_config(&cfg);
  }

  if (strlen(reinterpret_cast<char*>(cfg.ssid)) > 0) {
    Serial.printf("Found wi-fi credentials for SSID '%s'\n", cfg.ssid);
    connectToWiFiOrEnableAP();
  } else {
    Serial.println("No wi-fi credentials found");
    enableAccessPoint();
  }

  server.on("/setup_wifi", HTTP_POST, handleSetupWiFi);
  server.on("/set_builtin_led", HTTP_POST, handleSetBuiltinLED);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.printf(
    "HTTP server started, local IP: %s, access point IP: %s, port: %d\n",
    WiFi.localIP().toString().c_str(),
    WiFi.softAPIP().toString().c_str(),
    HTTP_SERVER_PORT
  );
}

void loop() {
  server.handleClient();
}
