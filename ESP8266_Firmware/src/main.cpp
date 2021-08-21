#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>

#include "configuration.hpp"

#define SIGN(val) (val > 0 ? 1 : (val < 0 ? -1 : 0))

//#define RESET_CONFIGURATION  // Usage: uncomment; build & upload & run; comment; build & upload & run

#define HTTP_SERVER_PORT 80

#define DIMMER_PREFIX   "dim"
#define SWITCHER_PREFIX "sw"

#define INPUT_PIN 14  // for 50 Hz meander

volatile uint32_t inputFallTimeMs = 0;

const uint8_t DIMMER_PINS[DIMMER_PINS_COUNT] = {4, 5, 12};
const uint8_t SWITCHER_PINS[SWITCHER_PINS_COUNT] = {13, 15, 9, 10};

#define MICROS_CHANGE_STEP 40

volatile int32_t dimmerMicros[DIMMER_PINS_COUNT] = {};
volatile int32_t targetDimmerMicros[DIMMER_PINS_COUNT] = {};

struct Event {
  uint32_t ticks;  // число тиков таймера, начиная от input fall
  uint8_t pin;
  uint8_t value;
};

#define DIMMER_EVENTS_COUNT 4  // число событий для одного выхода диммера

// настройки событий
const uint32_t EVENT_ADDITIONAL_OFFSETS[DIMMER_EVENTS_COUNT] = {0, 500, 50000, 50500};
const uint8_t  EVENT_VALUES            [DIMMER_EVENTS_COUNT] = {HIGH, LOW, HIGH, LOW};

#define EVENTS_COUNT (DIMMER_PINS_COUNT * DIMMER_EVENTS_COUNT)

volatile Event eventsQueue[EVENTS_COUNT];
volatile uint8_t nextEventId = EVENTS_COUNT;

ESP8266WebServer server(HTTP_SERVER_PORT);
smart_home::Configuration homeCfg;

enum class WiFiSetupState {
  NONE,
  IN_PROGRESS,
  FAIL,
  SUCCESS
};

WiFiSetupState wifiSetupState = WiFiSetupState::NONE;
int8_t wifiConnectResult = 0;
bool isAccessPointEnabled = false;

// Плавное изменение яркости
ICACHE_RAM_ATTR void smoothLightnessChange() {
  for (uint8_t i = 0; i < DIMMER_PINS_COUNT; ++i) {
    const int32_t delta = targetDimmerMicros[i] - dimmerMicros[i];
    if (delta != 0) {
      dimmerMicros[i] += std::abs(delta) > MICROS_CHANGE_STEP ? SIGN(delta) * MICROS_CHANGE_STEP : delta;
    }
  }
}

// Формируем новую очередь событий, начнутся со следующего input fall
ICACHE_RAM_ATTR void createEventsQueue() {
  uint8_t ev = 0;
  for (uint8_t i = 0; i < DIMMER_PINS_COUNT; ++i) {
    const uint32_t offsetTicks = 5 * dimmerMicros[i];
    for (uint8_t j = 0; j < DIMMER_EVENTS_COUNT; ++j) {
      eventsQueue[ev].pin = DIMMER_PINS[i];
      eventsQueue[ev].ticks = offsetTicks + EVENT_ADDITIONAL_OFFSETS[j];
      eventsQueue[ev].value = EVENT_VALUES[j];
      ++ev;
    }
  }

  // bubble sort is good enough for 12 elements
  for (uint8_t i = 0; i < EVENTS_COUNT - 1; ++i) {
    for (uint8_t j = 0; j < EVENTS_COUNT - i - 1; ++j) {
      if (eventsQueue[j].ticks > eventsQueue[j + 1].ticks) {
        std::swap(eventsQueue[j].ticks, eventsQueue[j + 1].ticks);
        std::swap(eventsQueue[j].pin, eventsQueue[j + 1].pin);
        std::swap(eventsQueue[j].value, eventsQueue[j + 1].value);
      }
    }
  }
}

ICACHE_RAM_ATTR void onInputFall() {
  // защита от многократных срабатываний на одном FALLING
  // и от срабатываний на RISING, которые тоже иногда случались
  const uint32_t now = millis();
  if (now - inputFallTimeMs < 15) return;
  inputFallTimeMs = now;

  nextEventId = 0;
  timer1_write(eventsQueue[0].ticks);
}

ICACHE_RAM_ATTR void onTimerISR() {
  if (nextEventId >= EVENTS_COUNT) return;

  // выполнить event
  const auto& event = eventsQueue[nextEventId++];
  digitalWrite(event.pin, event.value);

  // выполнить все следующие event'ы с такой же меткой времени
  while (nextEventId < EVENTS_COUNT && eventsQueue[nextEventId].ticks == event.ticks) {
    const auto& nextEvent = eventsQueue[nextEventId];
    digitalWrite(nextEvent.pin, nextEvent.value);
    ++nextEventId;
  }

  if (nextEventId < EVENTS_COUNT) {
    timer1_write(eventsQueue[nextEventId].ticks - event.ticks);  // аргумент будет >= 5
  } else {
    // после выполнения всех event'ов
    smoothLightnessChange();
    createEventsQueue();
  }
}

void fillDimmerMicros(bool fillCurrent = false) {
  for (uint8_t i = 0; i < DIMMER_PINS_COUNT; ++i) {
    targetDimmerMicros[i] = homeCfg.getDimmerValue(i);
    if (fillCurrent) {
      dimmerMicros[i] = targetDimmerMicros[i];
    }
  }
}

void applySwitcherValues() {
  for (uint8_t i = 0; i < SWITCHER_PINS_COUNT; ++i) {
    digitalWrite(SWITCHER_PINS[i], homeCfg.getSwitcherValue(i) ? HIGH : LOW);
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
  if (isAccessPointEnabled) return;

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
  digitalWrite(LED_BUILTIN, LOW);
  isAccessPointEnabled = true;
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

  wifiConnectResult = WiFi.waitForConnectResult(30000);
  if (wifiConnectResult != WL_CONNECTED) {
    Serial.printf("Failed to connect, status: %d\n", wifiConnectResult);
    enableAccessPoint();
    return false;
  }

  const String ip = WiFi.localIP().toString();
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

void handleGetInfo() {
  if (!checkPassword()) return;

  server.send(200, "text/plain", "MAC=" + WiFi.macAddress() + ";NAME=" + homeCfg.getName());
}

void handleSetupWiFi() {
  if (!checkPassword()) return;

  if (!server.hasArg("ssid") || !server.hasArg("passphrase")) {
    sendBadRequest();
    return;
  }

  wifiSetupState = WiFiSetupState::IN_PROGRESS;
  server.send(200, "text/plain", "TRYING_TO_CONNECT");
  delay(100);  // задержка нужна, чтобы успеть отправить ответ до попытки
  wifiSetupState = connectToWiFiOrEnableAP(server.arg("ssid").c_str(), server.arg("passphrase").c_str())
                      ? WiFiSetupState::SUCCESS
                      : WiFiSetupState::FAIL;
  // Если пытаться отправить ответ сервера здесь, то клиент получает ошибку:
  // curl скажет "empty reply from server", android скажет "unexpected end of stream on com.android.okhttp.Address"
}

void handleGetSetupWiFiState() {
  if (!checkPassword()) return;

  String result;
  switch (wifiSetupState) {
    case WiFiSetupState::NONE:
      result = "NONE";
      break;
    case WiFiSetupState::IN_PROGRESS:
      result = "IN_PROGRESS";
      break;
    case WiFiSetupState::FAIL:
      result = "FAIL:" + String(wifiConnectResult);
      break;
    case WiFiSetupState::SUCCESS:
      result = "SUCCESS:" + WiFi.localIP().toString();
      break;
  }
  server.send(200, "text/plain", result);
}

void handleResetWiFi() {
  if (!checkPassword()) return;

  server.send(200, "text/plain", "OK");
  delay(100);
  Serial.println("Reset wi-fi by request");
  WiFi.disconnect(true);
  enableAccessPoint();
}

void handleTurnOffAccessPoint() {
  if (!checkPassword()) return;

  server.send(200, "text/plain", "OK");
  delay(100);
  WiFi.softAPdisconnect(true);
  isAccessPointEnabled = false;
  Serial.println("Access point disabled by request");
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

String generateValuesString() {
  String result;
  for (uint8_t i = 0; i < DIMMER_PINS_COUNT; ++i) {
    result += DIMMER_PREFIX + String(i) + ": " + String(homeCfg.getDimmerValue(i)) + "\n";
  }
  for (uint8_t i = 0; i < SWITCHER_PINS_COUNT; ++i) {
    result += SWITCHER_PREFIX + String(i) + ": " + (homeCfg.getSwitcherValue(i) ? "on" : "off") + "\n";
  }
  return result;
}

void handleGetValues() {
  if (!checkPassword()) return;

  server.send(200, "text/plain", generateValuesString());
}

void handleSetValues() {
  if (!checkPassword()) return;

  bool dimmersChanged = false;
  for (uint8_t i = 0; i < DIMMER_PINS_COUNT; ++i) {
    const String argName = DIMMER_PREFIX + String(i);
    if (server.hasArg(argName)) {
      const int32_t value = server.arg(argName).toInt();
      if (value != homeCfg.getDimmerValue(i)) {
        homeCfg.setDimmerValue(i, value);
        dimmersChanged = true;
      }
    }
  }

  bool switchersChanged = false;
  for (uint8_t i = 0; i < SWITCHER_PINS_COUNT; ++i) {
    const String argName = SWITCHER_PREFIX + String(i);
    if (server.hasArg(argName)) {
      const bool value = server.arg(argName).toInt();
      if (value != homeCfg.getSwitcherValue(i)) {
        homeCfg.setSwitcherValue(i, value);
        switchersChanged = true;
      }
    }
  }

  if (dimmersChanged || switchersChanged) {
    if (dimmersChanged) {
      fillDimmerMicros();
    }
    if (switchersChanged) {
      applySwitcherValues();
    }
    homeCfg.save();
    server.send(200, "text/plain", "Accepted\n" + generateValuesString());
  } else {
    server.send(200, "text/plain", "Nothing changed\n");
  }
}

void handleNotFound() {
  server.send(404, "text/plain", "Not Found");
}

void setup() {
  for (uint8_t i = 0; i < DIMMER_PINS_COUNT; ++i) {
    pinMode(DIMMER_PINS[i], OUTPUT);
    digitalWrite(DIMMER_PINS[i], LOW);
  }
  for (uint8_t i = 0; i < SWITCHER_PINS_COUNT; ++i) {
    pinMode(SWITCHER_PINS[i], OUTPUT);
  }

#ifdef RESET_CONFIGURATION
  homeCfg.resetAndSave();
#endif
  homeCfg.load();

  fillDimmerMicros(true);
  createEventsQueue();
  applySwitcherValues();

  timer1_attachInterrupt(onTimerISR);
  timer1_enable(TIM_DIV16, TIM_EDGE, TIM_SINGLE);

  pinMode(INPUT_PIN, INPUT);
  attachInterrupt(digitalPinToInterrupt(INPUT_PIN), onInputFall, FALLING);

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  Serial.begin(115200);
  Serial.printf("\nDevice name: %s, HTTP password: '%s'\n", homeCfg.getName().c_str(), homeCfg.getPassword().c_str());

  // Just to be sure
  WiFi.setAutoConnect(true);
  WiFi.setAutoReconnect(true);

  WiFi.softAPdisconnect(true);
  isAccessPointEnabled = false;

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

  server.on("/get_info", HTTP_GET, handleGetInfo);
  server.on("/setup_wifi", HTTP_POST, handleSetupWiFi);
  server.on("/get_setup_wifi_state", HTTP_GET, handleGetSetupWiFiState);
  server.on("/reset_wifi", HTTP_GET, handleResetWiFi);
  server.on("/turn_off_ap", HTTP_GET, handleTurnOffAccessPoint);
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
