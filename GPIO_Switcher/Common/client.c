#include <client.h>

#include <main.h>
#include <flash.h>
#include <RF24.h>


#pragma pack(push, 1)

typedef struct {
  uint32_t uuid;
  uint8_t command;
  uint8_t payloadSize;
} NRFHeader;

#pragma pack(pop)


#define DEVICE_UUID 0x00015566

#if defined(STM32F103xB)
#define FLASH_DATA_ADDR 0x0800F800
#elif defined(STM32F030x6)
#define FLASH_DATA_ADDR 0x08003800
#endif

#define NRF_COMMAND_GET_STATE 0x01
#define NRF_COMMAND_SET_STATE 0x02

#define NRF_COMMAND_RESPONSE_STATE 0xF1

#define OUTPUTS_COUNT 7


uint32_t state = 0;

uint8_t nrfTxBuf[32] = {};
NRFHeader* nrfTxHeader = (NRFHeader*)nrfTxBuf;

uint8_t nrfRxBuf[32] = {};
NRFHeader* nrfRxHeader = (NRFHeader*)nrfRxBuf;

GPIO_TypeDef* gpio_ports[OUTPUTS_COUNT] = {
    OnboardLED_GPIO_Port,
    OUT1_GPIO_Port,
    OUT2_GPIO_Port,
    OUT3_GPIO_Port,
    OUT4_GPIO_Port,
    OUT5_GPIO_Port,
    OUT6_GPIO_Port
};

const uint16_t gpio_pins[OUTPUTS_COUNT] = {
    OnboardLED_Pin,
    OUT1_Pin,
    OUT2_Pin,
    OUT3_Pin,
    OUT4_Pin,
    OUT5_Pin,
    OUT6_Pin
};


void applyState() {
  uint8_t byte0 = ((uint8_t*)&state)[0];
  for (int i = 0; i < OUTPUTS_COUNT; ++i) {
    HAL_GPIO_WritePin(
      gpio_ports[i],
      gpio_pins[i],
      ((byte0 & 0x80) == 0x80) ? GPIO_PIN_SET : GPIO_PIN_RESET
    );
    byte0 <<= 1;
  }
}

void setState(uint32_t newState) {
  if (state != newState) {
    state = newState;
    applyState();

    // saving state to flash
    flashErase(FLASH_DATA_ADDR);
    flashWriteBegin();
    flashWrite((uint8_t*)&state, FLASH_DATA_ADDR, 4);
    flashWriteEnd();
  }
}

void handleNRFGetState() {
  nrfTxHeader->uuid = DEVICE_UUID;
  nrfTxHeader->command = NRF_COMMAND_RESPONSE_STATE;
  nrfTxHeader->payloadSize = 4;
  *((uint32_t*)(nrfTxBuf + sizeof(NRFHeader))) = state;

  stopListening();
  write(nrfTxBuf, sizeof(NRFHeader) + nrfTxHeader->payloadSize);
  startListening();
}

void handleNRFSetState() {
  if (nrfRxHeader->payloadSize < 4) return;

  setState(*((uint32_t*)(nrfRxBuf + sizeof(NRFHeader))));
  handleNRFGetState();
}

void handleNRFMessage() {
  switch (nrfRxHeader->command) {
    case NRF_COMMAND_GET_STATE:
      handleNRFGetState();
      break;
    case NRF_COMMAND_SET_STATE:
      handleNRFSetState();
      break;
  }
}

void tryToReadNRF() {
  uint8_t pipeId = 0;
  if (available(&pipeId)) {
    if (pipeId == 1) {
      uint8_t length = getDynamicPayloadSize();
      read(nrfRxBuf, length);
      if (length >= sizeof(NRFHeader)
          && nrfRxHeader->uuid == DEVICE_UUID
          && sizeof(NRFHeader) + nrfRxHeader->payloadSize == length) {
        handleNRFMessage();
      }
    }
  }
}

void smartHomeGPIOSwitcher() {
  flashUnlock();
  state = *((volatile uint32_t*)FLASH_DATA_ADDR);
  applyState();

  isChipConnected();  // not checking result because it is often wrong
  NRF_Init();
  setPALevel(RF24_PA_MAX);
  setDataRate(RF24_1MBPS);
  setAddressWidth(5);
  enableDynamicPayloads();
  setAutoAck(true);
  setChannel(103);

  openWritingPipe(0xA83C70FD6CLL);
  openReadingPipe(1, 0xA83C70FD6BLL);
  startListening();

  while (1) {
    tryToReadNRF();
  }
}
