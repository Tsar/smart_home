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

bool softAPMode = true;

char ssid[33];
char passphrase[65];

WiFiServer server(SERVER_PORT);

void fastBlinkForever() {
  while (true) {
    digitalWrite(LED_BUILTIN, LOW);
    delay(30);
    digitalWrite(LED_BUILTIN, HIGH);
    delay(30);
  }
}

bool connectToWiFi() {
  Serial.printf("Connecting to wi-fi: SSID [%s], passphrase [%s] ", ssid, passphrase);
  digitalWrite(LED_BUILTIN, LOW);

  WiFi.begin(ssid, passphrase);
  while (WiFi.status() != WL_CONNECTED) {
    if (WiFi.status() == WL_CONNECT_FAILED) {
      Serial.println("Failed");
      return false;
    }
    delay(200);
    Serial.print(".");
  }
  Serial.println();

  Serial.print("Connected, IP address: ");
  Serial.println(WiFi.localIP());
  digitalWrite(LED_BUILTIN, HIGH);
  return true;
}

void setup() {
  Serial.begin(115200);
  Serial.println();
  pinMode(LED_BUILTIN, OUTPUT);

  if (softAPMode) {
    digitalWrite(LED_BUILTIN, LOW);

    uint8_t mac[WL_MAC_ADDR_LENGTH];
    WiFi.macAddress(mac);
    char softAPName[32] = {};
    sprintf(softAPName, "SmartHomeDevice_%02X%02X%02X", mac[3], mac[4], mac[5]);

    Serial.printf("Setting soft-AP '%s' ... ", softAPName);
    const bool result = WiFi.softAP(softAPName, "setup12345");
    if (!result) {
      Serial.println("Failed!");
      fastBlinkForever();
      return;
    }

    Serial.println("Ready");
    digitalWrite(LED_BUILTIN, HIGH);
  } else {
    if (!connectToWiFi()) {
      fastBlinkForever();
      return;
    }
  }

  server.begin();
  server.setNoDelay(true);
  Serial.printf("Server started, IP: %s, port: %d\n", WiFi.softAPIP().toString().c_str(), SERVER_PORT);
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
          if (softAPMode) {
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
                if (connectToWiFi()) {
                  softAPMode = false;
                  client.print("WIFI_CONNECT_OK\n");
                  client.stop();
                  WiFi.softAPdisconnect(true);
                  Serial.println("Disabled soft-AP cause connected to wi-fi");
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
          } else {
            client.print("WIFI_SETUP_UNAVAILABLE\n");
            Serial.println("Unexpected setup wi-fi request, available only in soft-AP mode");
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
