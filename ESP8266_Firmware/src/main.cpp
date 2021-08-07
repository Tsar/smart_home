#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>

#include "configuration.hpp"

#define SIGN(val) (val > 0 ? 1 : (val < 0 ? -1 : 0))

//#define RESET_CONFIGURATION  // Usage: uncomment; build & upload & run; comment; build & upload & run

#define HTTP_SERVER_PORT 80

#define INPUT_PIN 14  // for 50 Hz meander

volatile uint32_t inputFallTimeMs = 0;

const uint8_t DIMMER_PINS[DIMMER_OUTPUTS_COUNT] = {4, 5, 12};

#define MICROS_CHANGE_STEP 40

volatile int32_t offsetMicros[DIMMER_OUTPUTS_COUNT] = {};
volatile int32_t targetOffsetMicros[DIMMER_OUTPUTS_COUNT] = {};

struct Event {
  uint32_t ticks;  // число тиков таймера, начиная от input fall
  uint8_t pin;
  uint8_t value;
};

#define DIMMER_EVENTS_COUNT 4  // число событий для одного выхода диммера

// настройки событий
const uint32_t EVENT_ADDITIONAL_OFFSETS[DIMMER_EVENTS_COUNT] = {0, 500, 50000, 50500};
const uint8_t  EVENT_VALUES            [DIMMER_EVENTS_COUNT] = {HIGH, LOW, HIGH, LOW};

#define EVENTS_COUNT (DIMMER_OUTPUTS_COUNT * DIMMER_EVENTS_COUNT)

Event eventsQueue[EVENTS_COUNT];
volatile uint8_t nextEventId = EVENTS_COUNT;

ESP8266WebServer server(HTTP_SERVER_PORT);
smart_home::Configuration homeCfg;

ICACHE_RAM_ATTR void onInputFall() {
  // защита от многократных срабатываний на одном FALLING
  // и от срабатываний на RISING, которые тоже иногда случались
  const uint32_t now = millis();
  if (now - inputFallTimeMs < 15) return;
  inputFallTimeMs = now;

  // плавное изменение яркости
  for (uint8_t i = 0; i < DIMMER_OUTPUTS_COUNT; ++i) {
    const int32_t delta = targetOffsetMicros[i] - offsetMicros[i];
    if (delta != 0) {
      offsetMicros[i] += std::abs(delta) > MICROS_CHANGE_STEP ? SIGN(delta) * MICROS_CHANGE_STEP : delta;
    }
  }

  // формируем очередь событий, актуальную до следующего input fall;
  // нужно из-за того, что у нас всего 1 hardware timer в распоряжении
  uint8_t ev = 0;
  for (uint8_t i = 0; i < DIMMER_OUTPUTS_COUNT; ++i) {
    const uint32_t offsetTicks = 5 * offsetMicros[i];
    for (uint8_t j = 0; j < DIMMER_EVENTS_COUNT; ++j) {
      eventsQueue[ev].pin = DIMMER_PINS[i];
      eventsQueue[ev].ticks = offsetTicks + EVENT_ADDITIONAL_OFFSETS[j];
      eventsQueue[ev].value = EVENT_VALUES[j];
      ++ev;
    }
  }
  std::sort(eventsQueue, eventsQueue + EVENTS_COUNT, [](const Event& ev1, const Event& ev2) {
    return ev1.ticks < ev2.ticks;
  });

  nextEventId = 0;
  timer1_write(eventsQueue[0].ticks);
}

ICACHE_RAM_ATTR void onTimerISR() {
  if (nextEventId >= EVENTS_COUNT) return;
  const auto& event = eventsQueue[nextEventId++];
  digitalWrite(event.pin, event.value);

  if (nextEventId < EVENTS_COUNT) {
    timer1_write(eventsQueue[nextEventId].ticks - event.ticks);
  }
}

void fillOffsetMicros(bool fillCurrent = false) {
  for (uint8_t i = 0; i < DIMMER_OUTPUTS_COUNT; ++i) {
    targetOffsetMicros[i] = homeCfg.getValue(i);
    if (fillCurrent) {
      offsetMicros[i] = targetOffsetMicros[i];
    }
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
  for (uint8_t i = 0; i < DIMMER_OUTPUTS_COUNT; ++i) {
    response += " " + String(homeCfg.getValue(i));
  }
  server.send(200, "text/plain", response + "\n");
}

void handleSetValues() {
  if (!checkPassword()) return;

  bool anythingChanged = false;
  for (uint8_t i = 0; i < DIMMER_OUTPUTS_COUNT; ++i) {
    const String argName = "v" + String(i);
    if (server.hasArg(argName)) {
      const int32_t value = server.arg(argName).toInt();
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
  for (uint8_t i = 0; i < DIMMER_OUTPUTS_COUNT; ++i) {
    pinMode(DIMMER_PINS[i], OUTPUT);
    digitalWrite(DIMMER_PINS[i], LOW);
  }

#ifdef RESET_CONFIGURATION
  homeCfg.resetAndSave();
#endif
  homeCfg.load();
  fillOffsetMicros(true);
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
