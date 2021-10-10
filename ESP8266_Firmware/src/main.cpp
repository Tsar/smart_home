#include <ESP8266WiFi.h>
#include <ESP8266WiFiGratuitous.h>
#include <ESP8266WebServer.h>
#include <WiFiUdp.h>
#include <lwip/igmp.h>
#include <Ticker.h>

#include "configuration.hpp"

#define HTTP_SERVER_PORT 80

#define SWITCHER_ARG_NAME "sw0"

#define INPUT_PIN 15

#define OUTPUT_PIN_A  12
#define OUTPUT_PIN_B  13
#define OUTPUT_PIN_EN 4

#define TIMER_ACTION_SWITCH_AB 0
#define TIMER_ACTION_EN_TO_LOW 1

volatile uint32_t inputRiseTimeMs = 0;
volatile uint8_t timerAction;
volatile uint8_t outputAState;

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

ICACHE_RAM_ATTR void startSwitchAB() {
  outputAState = !outputAState;
  digitalWrite(OUTPUT_PIN_EN, HIGH);
  timerAction = TIMER_ACTION_SWITCH_AB;
  timer1_write(156250);  // 500 ms
}

ICACHE_RAM_ATTR void onInputRise() {
  // защита от многократных срабатываний
  const uint32_t now = millis();
  if (now - inputRiseTimeMs < 1500) return;
  inputRiseTimeMs = now;

  startSwitchAB();
}

ICACHE_RAM_ATTR void onTimerISR() {
  switch (timerAction) {
    case TIMER_ACTION_SWITCH_AB:
      digitalWrite(OUTPUT_PIN_A, outputAState);
      digitalWrite(OUTPUT_PIN_B, !outputAState);
      timerAction = TIMER_ACTION_EN_TO_LOW;
      timer1_write(156250);  // 500 ms
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

// Same as WiFi.disconnect(true), but without touching config
void disconnectWiFi() {
    if (WiFi.getMode() & WIFI_STA) {
      wifi_station_disconnect();
    }
    WiFi.enableSTA(false);
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
  Serial.printf("Sent 403 for %s\n", server.uri().c_str());
  return false;
}

void sendBadRequest() {
  server.send(400, "text/plain", "Bad Request");
  Serial.printf("Sent 400 for %s\n", server.uri().c_str());
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
  const String additionalBlob = homeCfg.getAdditionalBlob();

  const size_t sz = WL_MAC_ADDR_LENGTH            // MAC size
                  + 2 + name.length()             // name length and name size
                  + 1                             // input pin
                  + 1                             // dimmers info size
                  + 1 + 3                         // switchers info size
                  + 2 + additionalBlob.length();  // some App's GUI display settings
  binInfoStorage.resize(sz);

  UnalignedBinarySerializer serializer(binInfoStorage.data());
  serializer.writeWiFiMacAddress();
  serializer.writeString(name);
  serializer.writeUInt8(INPUT_PIN);
  serializer.writeUInt8(0);  // dimmers count
  serializer.writeUInt8(1);  // switchers count
  serializer.writeUInt8(OUTPUT_PIN_A);
  serializer.writeUInt8(outputAState);
  serializer.writeUInt8(0);
  serializer.writeString(additionalBlob);

  if (serializer.getWrittenSize() != sz) {
    Serial.printf("WARNING: Serialized: %d, expected: %d\n", serializer.getWrittenSize(), sz);
  }
}

void handleGetInfo() {
  const auto tsStart = micros64();

  if (!checkPassword()) return;

  generateInfoBinary();
  server.send(200, "application/octet-stream", binInfoStorage.data(), binInfoStorage.size());

  const auto tsEnd = micros64();
  Serial.printf("Handled %s, spent %.2lf ms\n", server.uri().c_str(), (tsEnd - tsStart) / 1000.0);
}

void handleSetupWiFi() {
  if (!checkPassword()) return;

  if (!server.hasArg("ssid") || !server.hasArg("passphrase")) {
    sendBadRequest();
    return;
  }

  wifiSetupState = WiFiSetupState::IN_PROGRESS;
  server.send(200, "text/plain", "TRYING_TO_CONNECT");
  Serial.printf("Sent response to %s\n", server.uri().c_str());
  delay(100);  // задержка нужна, чтобы успеть отправить ответ до попытки
  disconnectWiFi();
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
  Serial.printf("Handled %s (response: %s)\n", server.uri().c_str(), result.c_str());
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
  const auto tsStart = micros64();

  if (!checkPassword()) return;

  bool switcherChanged = false;
  if (server.hasArg(SWITCHER_ARG_NAME)) {
    const uint8_t value = server.arg(SWITCHER_ARG_NAME).toInt();
    if (value != outputAState) {
      startSwitchAB();
      switcherChanged = true;
    }
  }

  server.send(200, "text/plain", switcherChanged ? "ACCEPTED\n" : "NOTHING_CHANGED\n");
  const auto tsEnd = micros64();
  Serial.printf(
    "Handled %s, spent %.2lf ms, %s\n",
    server.uri().c_str(),
    (tsEnd - tsStart) / 1000.0,
    switcherChanged ? "accepted new value" : "nothing was changed"
  );
}

void handleSetSettings() {
  if (!checkPassword()) return;

  if (server.hasArg("name")) {
    homeCfg.setName(server.arg("name"));
  }

  if (server.hasArg("blob")) {
    homeCfg.setAdditionalBlob(server.arg("blob"));
  }

  homeCfg.save();
  server.send(200, "text/plain", "ACCEPTED\n");
  Serial.printf("Handled %s\n", server.uri().c_str());
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
  Serial.printf("Handled %s\n", server.uri().c_str());
}

void handleNotFound() {
  server.send(404, "text/plain", "Not Found");
  Serial.printf("Sent 404 for %s\n", server.uri().c_str());
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
  pinMode(OUTPUT_PIN_A, OUTPUT);
  pinMode(OUTPUT_PIN_B, OUTPUT);
  pinMode(OUTPUT_PIN_EN, OUTPUT);

  bool resetCfgHappened;
  homeCfg.loadOrReset(resetCfgHappened);

  outputAState = homeCfg.getSwitcherValue() ? HIGH : LOW;
  digitalWrite(OUTPUT_PIN_A, outputAState);
  digitalWrite(OUTPUT_PIN_B, outputAState == LOW ? HIGH : LOW);
  digitalWrite(OUTPUT_PIN_EN, outputAState);

  timer1_attachInterrupt(onTimerISR);
  timer1_enable(TIM_DIV256, TIM_EDGE, TIM_SINGLE);

  pinMode(INPUT_PIN, INPUT);
  attachInterrupt(digitalPinToInterrupt(INPUT_PIN), onInputRise, RISING);

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

  // Не используем WiFi.disconnect и WiFi.softAPdisconnect, чтобы не трогать сохранённые настройки, а то reset в неподходящий момент всё портит
  disconnectWiFi();
  WiFi.enableAP(false);
  isAccessPointEnabled = false;

  // This should partly help for quick responses, details: https://github.com/esp8266/Arduino/issues/6886
  if (!WiFi.setSleepMode(WIFI_NONE_SLEEP)) {
    Serial.println("Failed to set wi-fi sleep mode to None!");
  }

  station_config stationCfg;
  wifi_station_get_config_default(&stationCfg);
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
  if (homeCfg.getSwitcherValue() != outputAState) {
    Serial.print("Saving new output A state to flash ... ");
    homeCfg.setSwitcherValue(outputAState);
    homeCfg.save();
    Serial.println("Done");
  }

  server.handleClient();
  udpHandlePacket();
}
