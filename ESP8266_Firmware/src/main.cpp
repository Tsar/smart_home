#include <ESP8266WiFi.h>
#include <ESP8266WiFiGratuitous.h>
#include <ESP8266WebServer.h>
#include <WiFiUdp.h>
#include <lwip/igmp.h>
#include <Ticker.h>

#include "configuration.hpp"

#define SIGN(val) (val > 0 ? 1 : (val < 0 ? -1 : 0))

#define HTTP_SERVER_PORT 80

#define DIMMER_PREFIX   "dim"
#define SWITCHER_PREFIX "sw"

#define INPUT_PIN 14  // for 50 Hz meander

volatile uint32_t inputFallTimeMs = 0;

const uint8_t DIMMER_PINS[DIMMERS_COUNT] = {4, 5, 12};
const uint8_t SWITCHER_PINS[SWITCHERS_COUNT] = {13, 15, 9, 10};

#define DIMMER_MAX_VALUE 1000

volatile int32_t dimmerValues[DIMMERS_COUNT] = {};  // 0 - диммер выключен, 1 - минимальная яркость, 1000 - максимальная яркость
volatile int32_t targetDimmerValues[DIMMERS_COUNT] = {};

const volatile smart_home::DimmerSettings* dimmersSettings;

struct Event {
  uint32_t ticks;  // число тиков таймера, начиная от input fall
  uint8_t pin;
  uint8_t value;
};

#define DIMMER_EVENTS_COUNT 4  // число событий для одного выхода диммера

// настройки событий
const uint32_t EVENT_ADDITIONAL_OFFSETS[DIMMER_EVENTS_COUNT] = {0, 500, 50000, 50500};
const uint8_t  EVENT_VALUES            [DIMMER_EVENTS_COUNT] = {HIGH, LOW, HIGH, LOW};

#define MAX_EVENTS_COUNT (DIMMERS_COUNT * DIMMER_EVENTS_COUNT)

volatile Event eventsQueue[MAX_EVENTS_COUNT];
volatile uint8_t eventsQueueSize = 0;
volatile uint8_t nextEventId = 0;

ESP8266WebServer server(HTTP_SERVER_PORT);
smart_home::Configuration homeCfg;

std::vector<char> binInfoStorage;

enum class WiFiSetupState {
  NONE,
  IN_PROGRESS,
  FAIL,
  SUCCESS
};

WiFiSetupState wifiSetupState = WiFiSetupState::NONE;
int8_t wifiConnectResult = 0;
bool isAccessPointEnabled = false;

// Для обнаружения
const String UDP_SCAN_REQUEST = "SMART_HOME_SCAN";  // should fit udpInputBuffer
const IPAddress UDP_MULTICAST_IP(227, 16, 119, 203);
#define UDP_MULTICAST_PORT 25061
#define UDP_RESPONSE_PORT  25062

WiFiUDP udp;
char udpInputBuffer[32];
uint32_t udpScanCounter = 0;

#define REJOIN_MULTICAST_GROUP_INTERVAL 120000  // 2 minutes
Ticker rejoinMulticastGroupTicker;

// Кодовая последовательность продолжительности включений контроллера для сброса настроек wi-fi, допустимо отклонение на 20%
const std::vector<uint32_t> WIFI_RESET_SEQUENCE = {20000, 2000, 10000};
Ticker wifiResetSequenceDetectorTicker;

// Плавное изменение яркости
ICACHE_RAM_ATTR void smoothLightnessChange() {
  for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
    const int32_t delta = targetDimmerValues[i] - dimmerValues[i];
    if (delta != 0) {
      const int32_t valueChangeStep = dimmersSettings[i].valueChangeStep;
      dimmerValues[i] += std::abs(delta) > valueChangeStep ? SIGN(delta) * valueChangeStep : delta;
    }
  }
}

#define COSINE_FORMULA  // если закомментировать, то будет линейная интерполяция

#ifdef COSINE_FORMULA
// Не удаётся использовать обычный cos, прошивка начинает крешиться (видимо, потому что он не в RAM)
// Источник этой реализации: https://stackoverflow.com/a/28050328
ICACHE_RAM_ATTR double fastCos(double x) {
    constexpr double tp = 1. / (2. * PI);
    x *= tp;
    x -= 0.25 + static_cast<int>(x + 0.25);  // using static_cast instead of floor, because in our case following is always true: x + 0.25 > 0
    x *= 16. * (std::abs(x) - 0.5);
    x += 0.225 * x * (std::abs(x) - 1.);
    return x;
}
#endif

// Переводит значение со слайдера в отступ импульса в микросекундах, см. dimmer_micros_adjustment.png
ICACHE_RAM_ATTR uint32_t dimmerValueToMicros(int32_t value, uint8_t dimmerId) {
  const int32_t minLM = dimmersSettings[dimmerId].minLightnessMicros;  // дефолт = 8300
  const int32_t maxLM = dimmersSettings[dimmerId].maxLightnessMicros;  // дефолт = 4000
#ifdef COSINE_FORMULA
  return fastCos(PI * (value - 1) / 2 / (DIMMER_MAX_VALUE - 1)) * (minLM - maxLM) + maxLM + 0.5;
#else
  return static_cast<double>(value - 1) * (maxLM - minLM) / (DIMMER_MAX_VALUE - 1) + minLM;
#endif
}

// Формируем новую очередь событий, начнутся со следующего input fall
ICACHE_RAM_ATTR void createEventsQueue() {
  uint8_t ev = 0;
  for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
    if (dimmerValues[i] <= 0) {
      // для подстраховки: пин по идее и без этого должен стоять в LOW
      digitalWrite(DIMMER_PINS[i], LOW);
    } else {
      // добавляем 4 события для диммера в очередь
      const uint32_t offsetTicks = 5 * dimmerValueToMicros(dimmerValues[i], i);
      for (uint8_t j = 0; j < DIMMER_EVENTS_COUNT; ++j) {
        eventsQueue[ev].pin = DIMMER_PINS[i];
        eventsQueue[ev].ticks = offsetTicks + EVENT_ADDITIONAL_OFFSETS[j];
        eventsQueue[ev].value = EVENT_VALUES[j];
        ++ev;
      }
    }
  }
  eventsQueueSize = ev;

  // bubble sort is good enough for 12 or less elements
  for (uint8_t i = 0; i < eventsQueueSize - 1; ++i) {
    for (uint8_t j = 0; j < eventsQueueSize - i - 1; ++j) {
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
  if (eventsQueueSize > 0) {
    timer1_write(eventsQueue[0].ticks);
  } else {
    smoothLightnessChange();
    createEventsQueue();
  }
}

ICACHE_RAM_ATTR void onTimerISR() {
  if (nextEventId >= eventsQueueSize) return;

  // выполнить event
  const auto& event = eventsQueue[nextEventId++];
  digitalWrite(event.pin, event.value);

  // выполнить все следующие event'ы с такой же меткой времени
  while (nextEventId < eventsQueueSize && eventsQueue[nextEventId].ticks == event.ticks) {
    const auto& nextEvent = eventsQueue[nextEventId];
    digitalWrite(nextEvent.pin, nextEvent.value);
    ++nextEventId;
  }

  if (nextEventId < eventsQueueSize) {
    const uint32_t ticksTillNextEvent = eventsQueue[nextEventId].ticks - event.ticks;  // в случае корректной работы всегда будет >= 5
    timer1_write(ticksTillNextEvent >= 5 ? ticksTillNextEvent : 5);                    // но подстрахуемся
  } else {
    // после выполнения всех event'ов
    smoothLightnessChange();
    createEventsQueue();
  }
}

void fillDimmerValues(bool fillCurrent = false) {
  for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
    targetDimmerValues[i] = homeCfg.getDimmerValue(i);
    if (fillCurrent) {
      dimmerValues[i] = targetDimmerValues[i];
    }
  }
}

void applySwitcherValues() {
  for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
    digitalWrite(SWITCHER_PINS[i], homeCfg.getSwitcherValue(i) != homeCfg.isSwitcherInverted(i) ? HIGH : LOW);
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

void rejoinMulticastGroup() {
  const auto& ip = WiFi.localIP();
  Serial.printf("IGMP leave group %s\n", igmp_leavegroup(ip, UDP_MULTICAST_IP) == ERR_OK ? "OK" : "failed");
  Serial.printf("IGMP join group %s\n", igmp_joingroup(ip, UDP_MULTICAST_IP) == ERR_OK ? "OK" : "failed");
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

bool connectToWiFi(const char* ssid, const char* passphrase, bool connectInfinitely) {
  digitalWrite(LED_BUILTIN, LOW);
  udp.stop();

  WiFi.disconnect(true);
  Serial.printf("Connecting to wi-fi: SSID '%s', passphrase '%s'\n", ssid, passphrase);
  WiFi.begin(ssid, passphrase);

  wifiConnectResult = WiFi.waitForConnectResult(30000);
  while (wifiConnectResult != WL_CONNECTED) {
    if (!connectInfinitely) {
      Serial.printf("Failed to connect, status: %d\n", wifiConnectResult);
      return false;
    }
    Serial.printf("Not yet connected, status: %d\n", wifiConnectResult);
    delay(1000);  // making delay for part of wait, because sometimes waitForConnectResult exits immediately
    wifiConnectResult = WiFi.waitForConnectResult(9000);
  }

  const auto& ip = WiFi.localIP();
  Serial.printf("Connected, IP: %s\n", ip.toString().c_str());
  digitalWrite(LED_BUILTIN, HIGH);

  // This should partly help for quick responses, details: https://github.com/esp8266/Arduino/issues/6886
  experimental::ESP8266WiFiGratuitous::stationKeepAliveSetIntervalMs();

  const bool listeningMulticast = udp.beginMulticast(ip, UDP_MULTICAST_IP, UDP_MULTICAST_PORT);
  if (listeningMulticast) {
    Serial.printf("Started listening UDP multicast on %s:%d\n", UDP_MULTICAST_IP.toString().c_str(), UDP_MULTICAST_PORT);
    rejoinMulticastGroupTicker.detach();
    rejoinMulticastGroupTicker.attach_ms_scheduled(REJOIN_MULTICAST_GROUP_INTERVAL, rejoinMulticastGroup);
  } else {
    Serial.println("Failed to start listening UDP multicast");
  }

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

// Create JSON (inefficiently)
String generateInfoJson(bool minimal) {
  String result = "{\n  \"mac\": \"" + WiFi.macAddress() + "\",\n  \"name\": \"" + homeCfg.getName() + "\"";
  if (!minimal) {
    result += ",\n  \"values\": {\n    \"";
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
      result += DIMMER_PREFIX + String(i) + "\": " + String(homeCfg.getDimmerValue(i)) + ",\n    \"";
    }
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
      result += SWITCHER_PREFIX + String(i) + "\": " + (homeCfg.getSwitcherValue(i) ? "1" : "0") + (i + 1 == SWITCHERS_COUNT ? "" : ",\n    \"");
    }
    result += "\n  },\n  \"micros\": {\n    \"";
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
      int32_t value = homeCfg.getDimmerValue(i);
      result += DIMMER_PREFIX + String(i) + "\": " + String(value > 0 ? dimmerValueToMicros(value, i) : -1) + (i + 1 == DIMMERS_COUNT ? "" : ",\n    \"");
    }
    result += "\n  },\n  \"dimmers_settings\": {\n    \"";
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
      result += DIMMER_PREFIX + String(i) + "\": {\n      \"value_change_step\": " + String(dimmersSettings[i].valueChangeStep)
                                              + ",\n      \"min_lightness_micros\": " + String(dimmersSettings[i].minLightnessMicros)
                                              + ",\n      \"max_lightness_micros\": " + String(dimmersSettings[i].maxLightnessMicros)
                                              + "\n    }" + (i + 1 == DIMMERS_COUNT ? "" : ",\n    \"");
    }
    result += "\n  },\n  \"switchers_inverted\": {\n    \"";
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
      result += SWITCHER_PREFIX + String(i) + "\": " + (homeCfg.isSwitcherInverted(i) ? "1" : "0") + (i + 1 == SWITCHERS_COUNT ? "" : ",\n    \"");
    }
    result += "\n  },\n  \"order\": {\n    \"dimmers\": [0, 1, 2],\n    \"switchers\": [0, 1, 2, 3]\n  }";  // TODO: dynamic order
  }
  result += "\n}\n";
  return result;
}

class UnalignedBinarySerializer {
public:
  UnalignedBinarySerializer(char* buffer) : buffer_(buffer), pos_(0) {}

  void writeWiFiMacAddress() {
    WiFi.macAddress(reinterpret_cast<uint8_t*>(buffer_ + pos_));
    pos_ += WL_MAC_ADDR_LENGTH;
  }

  void writeUInt8(uint8_t value) {
    buffer_[pos_++] = value;
  }

  void writeUInt16(uint16_t value) {
    memcpy(buffer_ + pos_, &value, 2);
    pos_ += 2;
  }

  void writeString(String value) {
    writeUInt16(value.length());
    memcpy(buffer_ + pos_, value.c_str(), value.length());
    pos_ += value.length();
  }

  size_t getWrittenSize() const {
    return pos_;
  }

private:
  char* buffer_;
  size_t pos_;
};

// Fill binInfoStorage
void generateInfoBinary() {
  const String name = homeCfg.getName();
  const size_t sz = WL_MAC_ADDR_LENGTH        // MAC size
                  + 2 + name.length()         // name length and name size
                  + 1 + DIMMERS_COUNT * 8     // dimmers values size (4 x uint16_t)
                  + 1 + SWITCHERS_COUNT * 2;  // switchers values size (2 x uint8_t)
  binInfoStorage.resize(sz);

  UnalignedBinarySerializer serializer(binInfoStorage.data());
  serializer.writeWiFiMacAddress();
  serializer.writeString(name);
  serializer.writeUInt8(DIMMERS_COUNT);
  for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
    serializer.writeUInt16(homeCfg.getDimmerValue(i));
    serializer.writeUInt16(dimmersSettings[i].valueChangeStep);
    serializer.writeUInt16(dimmersSettings[i].minLightnessMicros);
    serializer.writeUInt16(dimmersSettings[i].maxLightnessMicros);
  }
  serializer.writeUInt8(SWITCHERS_COUNT);
  for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
    serializer.writeUInt8(homeCfg.getSwitcherValue(i));
    serializer.writeUInt8(homeCfg.isSwitcherInverted(i));
  }

  if (serializer.getWrittenSize() != sz) {
    Serial.printf("WARNING: Serialized: %d, expected: %d\n", serializer.getWrittenSize(), sz);
  }
}

void handleGetInfo() {
  const auto tsStart = millis();

  if (!checkPassword()) return;

  if (server.hasArg("binary")) {
    generateInfoBinary();
    server.send(200, "application/octet-stream", binInfoStorage.data(), binInfoStorage.size());
  } else {
    server.send(200, "application/json", generateInfoJson(server.hasArg("minimal")));
  }

  const auto tsEnd = millis();
  Serial.printf("Handled %s, spent %ld ms\n", server.uri().c_str(), tsEnd - tsStart);
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
  wifiSetupState = connectToWiFi(server.arg("ssid").c_str(), server.arg("passphrase").c_str(), false)
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
  Serial.println("Reset wi-fi credentials by request");
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

void handleSetValues() {
  if (!checkPassword()) return;

  bool dimmersChanged = false;
  for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
    const String argName = DIMMER_PREFIX + String(i);
    if (server.hasArg(argName)) {
      const int32_t value = server.arg(argName).toInt();
      if (value < 0 || value > DIMMER_MAX_VALUE) {
        sendBadRequest();
        return;
      }
      if (value != homeCfg.getDimmerValue(i)) {
        homeCfg.setDimmerValue(i, value);
        dimmersChanged = true;
      }
    }
  }

  bool switchersChanged = false;
  for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
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
      fillDimmerValues();
    }
    if (switchersChanged) {
      applySwitcherValues();
    }
    homeCfg.save();
    server.send(200, "text/plain", "ACCEPTED\n");
  } else {
    server.send(200, "text/plain", "NOTHING_CHANGED\n");
  }
}

void handleSetSettings() {
  if (!checkPassword()) return;

  if (server.hasArg("name")) {
    homeCfg.setName(server.arg("name"));
  }

  bool resetCfgHappened;
  for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
    const String argName = DIMMER_PREFIX + String(i);
    if (server.hasArg(argName)) {
      const String settingsStr = server.arg(argName);
      const int comma1Pos = settingsStr.indexOf(",");
      if (comma1Pos < 0) {
        homeCfg.loadOrReset(resetCfgHappened);  // used to revert cfg
        sendBadRequest();
        return;
      }
      const int comma2Pos = settingsStr.indexOf(",", comma1Pos + 1);
      if (comma2Pos < 0) {
        homeCfg.loadOrReset(resetCfgHappened);  // used to revert cfg
        sendBadRequest();
        return;
      }
      smart_home::DimmerSettings settings;
      settings.valueChangeStep = settingsStr.substring(0, comma1Pos).toInt();
      settings.minLightnessMicros = settingsStr.substring(comma1Pos + 1, comma2Pos).toInt();
      settings.maxLightnessMicros = settingsStr.substring(comma2Pos + 1).toInt();
      if (!settings.areValid()) {
        homeCfg.loadOrReset(resetCfgHappened);  // used to revert cfg
        sendBadRequest();
        return;
      }
      homeCfg.setDimmerSettings(i, settings);
    }
  }

  for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
    const String argName = SWITCHER_PREFIX + String(i);
    if (server.hasArg(argName)) {
      const bool inverted = server.arg(argName).toInt();
      homeCfg.setSwitcherInverted(i, inverted);
    }
  }

  homeCfg.save();
  server.send(200, "text/plain", "ACCEPTED\n");
}

void handleSetPassword() {
  if (!checkPassword()) return;

  if (!server.hasArg("password")) {
    sendBadRequest();
    return;
  }

  homeCfg.setPassword(server.arg("password"));
  homeCfg.save();
  server.send(200, "text/plain", "ACCEPTED\n");
}

void handleNotFound() {
  server.send(404, "text/plain", "Not Found");
}

void udpHandlePacket() {
  int packetSize = udp.parsePacket();
  if (packetSize == static_cast<int>(UDP_SCAN_REQUEST.length())) {
    int len = udp.read(udpInputBuffer, packetSize);
    udpInputBuffer[len] = 0;
    if (UDP_SCAN_REQUEST == String(udpInputBuffer)) {
      const String response = "MAC=" + WiFi.macAddress() + "\nNAME=" + homeCfg.getName();
      udp.beginPacket(udp.remoteIP(), UDP_RESPONSE_PORT);  // unicast back
      udp.write(response.c_str());
      udp.endPacket();
      Serial.printf("Handled UDP scan request #%d\n", ++udpScanCounter);
    }
  }
}

void resetWiFiResetSequence() {
  homeCfg.setWiFiResetSequenceLengthAndSave(0);
  Serial.printf("Done reset wi-fi reset sequence length to zero; current uptime = %ld ms\n", millis());
}

void incrementWiFiResetSequence() {
  const uint8_t currentSeqLength = homeCfg.getWiFiResetSequenceLength();
  homeCfg.setWiFiResetSequenceLengthAndSave(currentSeqLength + 1);

  const auto currentUptime = millis();
  Serial.printf("Done wi-fi reset sequence length incrementation to %d; current uptime = %ld ms\n", currentSeqLength + 1, currentUptime);

  const uint32_t resetSeqLengthUptime = WIFI_RESET_SEQUENCE[currentSeqLength] * 1.2;
  if (resetSeqLengthUptime >= currentUptime) {
    wifiResetSequenceDetectorTicker.once_ms(resetSeqLengthUptime - currentUptime, resetWiFiResetSequence);
  } else {
    resetWiFiResetSequence();
  }
}

// Проверка и обработка кодовой последовательности продолжительности включений контроллера для сброса настроек wi-fi
void checkWiFiResetSequence() {
  const uint8_t currentSeqLength = homeCfg.getWiFiResetSequenceLength();
  if (currentSeqLength >= WIFI_RESET_SEQUENCE.size()) {
    homeCfg.setPassword(DEFAULT_HTTP_PASSWORD);  // пароль для HTTP тоже сбрасываем, чтобы можно было добавить устройство как свежее
    homeCfg.setWiFiResetSequenceLengthAndSave(0);

    Serial.println("Reset wi-fi credentials by code sequence of uptimes");
    WiFi.disconnect(true);

    // Поморгать встроенным светодиодом 5 раз
    for (uint8_t i = 0; i < 5; ++i) {
      digitalWrite(LED_BUILTIN, HIGH);
      delay(60);
      digitalWrite(LED_BUILTIN, LOW);
      delay(60);
    }
  } else {
    const auto currentUptime = millis();
    const uint32_t incrementSeqLengthUptime = WIFI_RESET_SEQUENCE[currentSeqLength] * 0.8;
    if (incrementSeqLengthUptime >= currentUptime) {
      const uint32_t interval = incrementSeqLengthUptime - currentUptime;
      wifiResetSequenceDetectorTicker.once_ms(interval, incrementWiFiResetSequence);
    } else {
      Serial.println("Failed to schedule wi-fi reset sequence length incrementation: it should have been in the past");
    }
  }
}

void setup() {
  for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
    pinMode(DIMMER_PINS[i], OUTPUT);
    digitalWrite(DIMMER_PINS[i], LOW);
  }
  for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
    pinMode(SWITCHER_PINS[i], OUTPUT);
  }

  bool resetCfgHappened;
  homeCfg.loadOrReset(resetCfgHappened);

  dimmersSettings = homeCfg.getDimmersSettings();
  fillDimmerValues(true);
  createEventsQueue();
  applySwitcherValues();

  timer1_attachInterrupt(onTimerISR);
  timer1_enable(TIM_DIV16, TIM_EDGE, TIM_SINGLE);

  pinMode(INPUT_PIN, INPUT);
  attachInterrupt(digitalPinToInterrupt(INPUT_PIN), onInputFall, FALLING);

  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  Serial.begin(115200);
  Serial.printf("\n%s\nDevice name: %s, HTTP password: '%s'\n",
                resetCfgHappened ? "CONFIGURATION RESET HAPPENED!" : "Configuration loaded successfully",
                homeCfg.getName().c_str(), homeCfg.getPassword().c_str()
  );

  checkWiFiResetSequence();

  WiFi.persistent(true);
  WiFi.setAutoConnect(false);
  WiFi.setAutoReconnect(true);

  station_config stationCfg;
  wifi_station_get_config_default(&stationCfg);

  WiFi.disconnect(true);
  WiFi.softAPdisconnect(true);
  isAccessPointEnabled = false;

  // This should partly help for quick responses, details: https://github.com/esp8266/Arduino/issues/6886
  if (!WiFi.setSleepMode(WIFI_NONE_SLEEP)) {
    Serial.println("Failed to set wi-fi sleep mode to None!");
  }

  const char* cfgSsid = reinterpret_cast<char*>(stationCfg.ssid);
  const char* cfgPassphrase = reinterpret_cast<char*>(stationCfg.password);

  if (strlen(cfgSsid) > 0) {
    Serial.println("Found wi-fi credentials");
    connectToWiFi(cfgSsid, cfgPassphrase, true);
  } else {
    Serial.println("No wi-fi credentials found");
    enableAccessPoint();
  }

  server.on("/get_info", HTTP_GET, handleGetInfo);
  server.on("/setup_wifi", HTTP_POST, handleSetupWiFi);
  server.on("/get_setup_wifi_state", HTTP_GET, handleGetSetupWiFiState);
  server.on("/reset_wifi", HTTP_GET, handleResetWiFi);
  server.on("/turn_off_ap", HTTP_GET, handleTurnOffAccessPoint);
  server.on("/set_values", HTTP_ANY, handleSetValues);
  server.on("/set_settings", HTTP_POST, handleSetSettings);
  server.on("/set_password", HTTP_POST, handleSetPassword);
  server.onNotFound(handleNotFound);

  const char* headerKeys[] = {"Password"};
  server.collectHeaders(headerKeys, 1);

  server.begin();
  Serial.printf("Started HTTP server on port %d\n", HTTP_SERVER_PORT);
}

void loop() {
  server.handleClient();
  udpHandlePacket();
}
