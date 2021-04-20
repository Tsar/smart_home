#include <ESP8266WiFi.h>

#define SERVER_PORT 7131

#define MSG_HEADER_MAGIC 0xDFCE

#define MSG_COMMAND_PING       0x01
#define MSG_COMMAND_SETUP_WIFI 0x02

#pragma pack(push, 1)

struct MsgHeader {
	uint16_t magic;
	uint8_t command;
	uint16_t payloadSize;
};

#pragma pack(pop)

MsgHeader msgHeader;
uint8_t msgPayload[256];

char ssid[32];
char passphrase[64];

WiFiServer server(SERVER_PORT);

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

bool connectToWiFiOrEnableAP(bool useSsidPassphrase) {
  digitalWrite(LED_BUILTIN, LOW);

  if (useSsidPassphrase) {
    Serial.printf("Connecting to wi-fi: SSID [%s], passphrase [%s]\n", ssid, passphrase);
    WiFi.disconnect(true);
    WiFi.begin(ssid, passphrase);
  } else {
    Serial.println("Waiting for connect to wi-fi");
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
    connectToWiFiOrEnableAP(false);
  } else {
    Serial.println("No wi-fi credentials found");
    enableAccessPoint();
  }

  server.begin();
  server.setNoDelay(true);
  Serial.printf("Server started, local IP: %s, access point IP: %s, port: %d\n", WiFi.localIP().toString().c_str(), WiFi.softAPIP().toString().c_str(), SERVER_PORT);
}

void loop() {
  WiFiClient client = server.available();
  if (client) {
    Serial.println("Client connected");
    if (client.connected()
        && client.available()
        && client.readBytes(reinterpret_cast<uint8_t*>(&msgHeader), sizeof(MsgHeader)) == sizeof(MsgHeader)
        && msgHeader.magic == MSG_HEADER_MAGIC
        && (msgHeader.payloadSize == 0 || client.readBytes(msgPayload, msgHeader.payloadSize) == msgHeader.payloadSize)
       ) {
      switch (msgHeader.command) {
        case MSG_COMMAND_PING:
          client.print("tmp_response\n");
          Serial.println("Handled ping");
          break;
        case MSG_COMMAND_SETUP_WIFI:
          if (msgHeader.payloadSize >= 3) {
            // payload should be <ssid>0x00<passphrase>
            bool parsed = false;
            for (int i = 1; i < msgHeader.payloadSize - 1; ++i) {
              if (msgPayload[i] == 0x00) {
                memcpy(ssid, msgPayload, i + 1);
                memcpy(passphrase, msgPayload + i + 1, msgHeader.payloadSize - i - 1);
                passphrase[msgHeader.payloadSize - i - 1] = 0;
                parsed = true;
                break;
              }
            }

            if (parsed) {
              if (connectToWiFiOrEnableAP(true)) {
                client.print("WIFI_CONNECT_OK\n");
                client.stop();
                delay(10);
                WiFi.softAPdisconnect(true);
                Serial.println("Disabled access point because connected to wi-fi");
                return;
              } else {
                client.print("WIFI_CONNECT_FAILED\n");
              }
            } else {
              Serial.println("Bad setup wi-fi request (#2), ignoring");
            }
          } else {
            Serial.println("Bad setup wi-fi request (#1), ignoring");
          }
          break;
      }
    } else {
      Serial.println("Bad request, ignoring");
    }

    client.stop();
    Serial.println("Client disconnected");
  }
}
