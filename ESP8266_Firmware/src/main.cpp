#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>

#define HTTP_SERVER_PORT 80

#define INPUT_PIN 14

#define OUTPUT_PIN_A  13
#define OUTPUT_PIN_B  15
#define OUTPUT_PIN_EN 4

#define TIMER_ACTION_SWITCH_AB 0
#define TIMER_ACTION_EN_TO_LOW 1

ESP8266WebServer server(HTTP_SERVER_PORT);

volatile uint32_t inputRiseTimeMs = 0;
volatile uint8_t timerAction;
volatile uint8_t outputAState;

ICACHE_RAM_ATTR void startSwitchAB() {
  digitalWrite(OUTPUT_PIN_EN, HIGH);
  timerAction = TIMER_ACTION_SWITCH_AB;
  timer1_write(1562);  // 5 ms
}

ICACHE_RAM_ATTR void onInputRise() {
  // защита от многократных срабатываний
  const uint32_t now = millis();
  if (now - inputRiseTimeMs < 3000) return;
  inputRiseTimeMs = now;

  startSwitchAB();
}

ICACHE_RAM_ATTR void onTimerISR() {
  switch (timerAction) {
    case TIMER_ACTION_SWITCH_AB:
      outputAState = !outputAState;
      digitalWrite(OUTPUT_PIN_A, outputAState);
      digitalWrite(OUTPUT_PIN_B, !outputAState);
      timerAction = TIMER_ACTION_EN_TO_LOW;
      timer1_write(312500);  // 1 s
      break;
    case TIMER_ACTION_EN_TO_LOW:
      digitalWrite(OUTPUT_PIN_EN, LOW);
      break;
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
  Serial.printf("Connected, IP: %s\n", ip.c_str());
  digitalWrite(LED_BUILTIN, HIGH);
  return true;
}

void sendBadRequest() {
  server.send(400, "text/plain", "Bad Request");
}

void handlePing() {
  server.send(200, "text/plain", "OK");
}

void handleSetupWiFi() {
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

void handleSwitch() {
  startSwitchAB();
  server.send(200, "text/plain", "Accepted\n");
}

void handleNotFound() {
  server.send(404, "text/plain", "Not Found");
}

void setup() {
  pinMode(OUTPUT_PIN_A, OUTPUT);
  pinMode(OUTPUT_PIN_B, OUTPUT);
  pinMode(OUTPUT_PIN_EN, OUTPUT);

  outputAState = LOW;
  digitalWrite(OUTPUT_PIN_A, LOW);
  digitalWrite(OUTPUT_PIN_B, HIGH);
  digitalWrite(OUTPUT_PIN_EN, LOW);

  timer1_attachInterrupt(onTimerISR);
  timer1_enable(TIM_DIV256, TIM_EDGE, TIM_SINGLE);

  pinMode(INPUT_PIN, INPUT);
  attachInterrupt(digitalPinToInterrupt(INPUT_PIN), onInputRise, RISING);

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  // Just to be sure
  WiFi.setAutoConnect(true);
  WiFi.setAutoReconnect(true);

  WiFi.softAPdisconnect(true);

  station_config stationCfg;
  if (WiFi.getPersistent()) {
    wifi_station_get_config_default(&stationCfg);
  } else {
    wifi_station_get_config(&stationCfg);
  }

  Serial.begin(115200);
  Serial.print("\n");

  if (strlen(reinterpret_cast<char*>(stationCfg.ssid)) > 0) {
    Serial.printf("Found wi-fi credentials for SSID '%s'\n", stationCfg.ssid);
    connectToWiFiOrEnableAP();
  } else {
    Serial.println("No wi-fi credentials found");
    enableAccessPoint();
  }

  server.on("/ping", HTTP_GET, handlePing);
  server.on("/setup_wifi", HTTP_POST, handleSetupWiFi);
  server.on("/switch", HTTP_GET, handleSwitch);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.printf("Started HTTP server on port %d\n", HTTP_SERVER_PORT);
}

void loop() {
  server.handleClient();
}
