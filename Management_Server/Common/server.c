#include <server.h>

#include <flash.h>
#include <RF24.h>


#pragma pack(push, 1)

typedef struct {
	uint32_t nameId;
	uint32_t uuid;  // contains type of device
} Device;

typedef struct {
	uint32_t uuid;
	uint8_t command;
	uint8_t payloadSize;
} NRFHeader;

typedef struct {
	uint16_t magic;
	uint8_t command;
	uint8_t payloadSize;
} UARTHeader;

#pragma pack(pop)


//#define RESET_DEVICES_CONFIGURATION

#if defined(STM32F103xB)
#define FLASH_DATA_ADDR 0x0800F800
#elif defined(STM32F030x6)
#define FLASH_DATA_ADDR 0x08003800
#endif

#define MAX_DEVICES_COUNT 30

#define DEVICE_UNAVAILABLE_STATE 0xFF0000FF

#define NRF_COMMAND_GET_STATE      0x01
#define NRF_COMMAND_RESPONSE_STATE 0xF1

#define UART_HEADER_MAGIC 0xBFCE

#define UART_COMMAND_PING                 0x01
#define UART_COMMAND_GET_DEVICES          0x02
#define UART_COMMAND_SET_DEVICES          0x03
#define UART_COMMAND_GET_DEVICE_STATES    0x04
#define UART_COMMAND_UPDATE_DEVICE_STATES 0x05
#define UART_COMMAND_SEND_NRF_MESSAGE     0x08

#define UART_COMMAND_RESPONSE_PING                    0x81
#define UART_COMMAND_RESPONSE_GET_DEVICES             0x82
#define UART_COMMAND_RESPONSE_SET_DEVICES             0x83
#define UART_COMMAND_RESPONSE_GET_DEVICE_STATES       0x84
#define UART_COMMAND_RESPONSE_UPDATE_DEVICE_STATES    0x85
#define UART_COMMAND_RESPONSE_SEND_NRF_MESSAGE        0x88
#define UART_COMMAND_RESPONSE_SEND_NRF_MESSAGE_FAILED 0xF8


uint8_t devicesCount = 0;
Device devices[MAX_DEVICES_COUNT];         // configuration
uint32_t deviceStates[MAX_DEVICES_COUNT];  // kind of a cache

uint8_t nrfTxBuf[32] = {};
NRFHeader* nrfTxHeader = (NRFHeader*)nrfTxBuf;

uint8_t nrfRxBuf[32] = {};
NRFHeader* nrfRxHeader = (NRFHeader*)nrfRxBuf;

uint8_t uartTxBuf[256] = {};
UARTHeader* uartTxHeader = (UARTHeader*)uartTxBuf;

uint8_t uartRxBuf[256] = {};
UARTHeader* uartRxHeader = (UARTHeader*)uartRxBuf;

uint8_t uartRxByte = 0;
uint8_t uartRxCurrentPos = 0;

UART_HandleTypeDef* huart;


void fastBlinkForever() {
	while (1) {
		HAL_GPIO_TogglePin(OnboardLED_GPIO_Port, OnboardLED_Pin);
		for (int i = 0; i < 100000; ++i);
	}
}

void readDevicesConfiguration() {
	devicesCount = *((const volatile uint8_t*)FLASH_DATA_ADDR);
	if (devicesCount > MAX_DEVICES_COUNT) {
		// error, if happens use RESET_DEVICES_CONFIGURATION
		fastBlinkForever();
		return;
	}
	memcpy(devices, (/*volatile*/ uint8_t*)FLASH_DATA_ADDR + 2, devicesCount * sizeof(Device));
}

bool sendNRFMessage(uint8_t writeAttempts, uint8_t readAttempts) {
	while (writeAttempts > 0) {
		write(nrfTxBuf, sizeof(NRFHeader) + nrfTxHeader->payloadSize);
		startListening();

		uint8_t readAttemptsLeft = readAttempts;
		while (readAttemptsLeft > 0) {
			HAL_Delay(50);  // TODO: think about reducing this interval
			uint8_t pipeId = 0;
			if (available(&pipeId)) {
				if (pipeId == 1) {
					uint8_t length = getDynamicPayloadSize();
					read(nrfRxBuf, length);
					if (length >= sizeof(NRFHeader)
							&& nrfTxHeader->uuid == nrfRxHeader->uuid
							&& sizeof(NRFHeader) + nrfRxHeader->payloadSize == length) {
						stopListening();
						return true;
					}
				}
			}
			--readAttemptsLeft;
		}

		stopListening();
		--writeAttempts;
	}
	return false;
}

void updateAllDeviceStates() {
	for (int i = 0; i < devicesCount; ++i) {
		nrfTxHeader->uuid = devices[i].uuid;
		nrfTxHeader->command = NRF_COMMAND_GET_STATE;
		nrfTxHeader->payloadSize = 0;

		if (sendNRFMessage(3, 3) && nrfRxHeader->command == NRF_COMMAND_RESPONSE_STATE && nrfRxHeader->payloadSize == 4) {
			deviceStates[i] = *((uint32_t*)(nrfRxBuf + sizeof(NRFHeader)));
		} else {
			deviceStates[i] = DEVICE_UNAVAILABLE_STATE;
		}
	}
}

void sendUartTxMessage() {
	HAL_UART_Transmit_DMA(huart, uartTxBuf, sizeof(UARTHeader) + uartTxHeader->payloadSize);
}

void handleUartPing() {
	uartTxHeader->magic = UART_HEADER_MAGIC;
	uartTxHeader->command = UART_COMMAND_RESPONSE_PING;
	uartTxHeader->payloadSize = uartRxHeader->payloadSize;
	for (int i = sizeof(UARTHeader); i < sizeof(UARTHeader) + uartRxHeader->payloadSize; ++i) {
		uartTxBuf[i] = ~uartRxBuf[i];
	}
	sendUartTxMessage();
}

void handleUartGetDevices(bool afterSet) {
	uartTxHeader->magic = UART_HEADER_MAGIC;
	uartTxHeader->command = afterSet
		? UART_COMMAND_RESPONSE_SET_DEVICES
		: UART_COMMAND_RESPONSE_GET_DEVICES;
	uartTxHeader->payloadSize = devicesCount * sizeof(Device);
	memcpy(uartTxBuf + sizeof(UARTHeader), devices, uartTxHeader->payloadSize);
	sendUartTxMessage();
}

void handleUartSetDevices() {
	devicesCount = uartRxHeader->payloadSize / sizeof(Device);
	memcpy(devices, uartRxBuf + sizeof(UARTHeader), devicesCount * sizeof(Device));

	uint8_t firstTwoBytes[2] = {devicesCount, 0x00};
	flashErase(FLASH_DATA_ADDR);
	flashErase(FLASH_DATA_ADDR + 0x400);
	flashWriteBegin();
	flashWrite(firstTwoBytes, FLASH_DATA_ADDR, 2);
	flashWrite((uint8_t*)devices, FLASH_DATA_ADDR + 2, devicesCount * sizeof(Device));
	flashWriteEnd();

	readDevicesConfiguration();  // read back immediately
	updateAllDeviceStates();     // make our "cache" up to date
	handleUartGetDevices(true);  // send back for verification
}

void handleUartGetDeviceStates(bool update) {
	if (update) {
		updateAllDeviceStates();
	}

	uartTxHeader->magic = UART_HEADER_MAGIC;
	uartTxHeader->command = update
		? UART_COMMAND_RESPONSE_UPDATE_DEVICE_STATES
		: UART_COMMAND_RESPONSE_GET_DEVICE_STATES;
	uartTxHeader->payloadSize = devicesCount * sizeof(uint32_t);
	memcpy(uartTxBuf + sizeof(UARTHeader), deviceStates, uartTxHeader->payloadSize);
	sendUartTxMessage();
}

void handleUartSendNRFMessage() {
	// Payload in UART message is NRF message completely
	memcpy(nrfTxBuf, uartRxBuf + sizeof(UARTHeader), uartRxHeader->payloadSize);

	uartTxHeader->magic = UART_HEADER_MAGIC;
	if (sendNRFMessage(5, 5)) {
		uartTxHeader->command = UART_COMMAND_RESPONSE_SEND_NRF_MESSAGE;
		uartTxHeader->payloadSize = sizeof(NRFHeader) + nrfRxHeader->payloadSize;
		memcpy(uartTxBuf + sizeof(UARTHeader), nrfRxBuf, uartTxHeader->payloadSize);
	} else {
		uartTxHeader->command = UART_COMMAND_RESPONSE_SEND_NRF_MESSAGE_FAILED;
		uartTxHeader->payloadSize = 0;
	}
	sendUartTxMessage();

	// If NRF message has device state update, than update it in our "cache"
	if (nrfRxHeader->command == NRF_COMMAND_RESPONSE_STATE && nrfRxHeader->payloadSize == 4) {
		for (int i = 0; i < devicesCount; ++i) {
			if (devices[i].uuid == nrfRxHeader->uuid) {
				deviceStates[i] = *((uint32_t*)(nrfRxBuf + sizeof(NRFHeader)));
				break;
			}
		}
	}
}

void handleUartMessage() {
	switch (uartRxHeader->command) {
		case UART_COMMAND_PING:
			handleUartPing();
			break;
		case UART_COMMAND_GET_DEVICES:
			handleUartGetDevices(false);
			break;
		case UART_COMMAND_SET_DEVICES:
			handleUartSetDevices();
			break;
		case UART_COMMAND_GET_DEVICE_STATES:
			handleUartGetDeviceStates(false);
			break;
		case UART_COMMAND_UPDATE_DEVICE_STATES:
			handleUartGetDeviceStates(true);
			break;
		case UART_COMMAND_SEND_NRF_MESSAGE:
			handleUartSendNRFMessage();
			break;
	}
}

void HAL_UART_TxCpltCallback(UART_HandleTypeDef* huartPtr) {
	if (huartPtr != huart) return;

	HAL_GPIO_TogglePin(OnboardLED_GPIO_Port, OnboardLED_Pin);
}

// Handle receiving one byte uartRxByte
void HAL_UART_RxCpltCallback(UART_HandleTypeDef* huartPtr) {
	if (huartPtr != huart) return;

	if (uartRxHeader->magic != UART_HEADER_MAGIC) {
		uartRxBuf[0] = uartRxBuf[1];
		uartRxBuf[1] = uartRxByte;
		uartRxCurrentPos = 2;
	} else {
		uartRxBuf[uartRxCurrentPos++] = uartRxByte;
		if (uartRxCurrentPos == sizeof(UARTHeader) + uartRxHeader->payloadSize) {  // message ready
			handleUartMessage();
			uartRxCurrentPos = 0;
		}
	}

	HAL_UART_Receive_DMA(huart, &uartRxByte, 1);
}

void smartHomeManagementServer(UART_HandleTypeDef* huartPtr) {
	huart = huartPtr;

	flashUnlock();
#ifdef RESET_DEVICES_CONFIGURATION
	uint8_t zeros[2] = {0x00, 0x00};
	flashErase(FLASH_DATA_ADDR);
	flashWriteBegin();
	flashWrite(zeros, FLASH_DATA_ADDR, 2);
	flashWriteEnd();
#endif
	readDevicesConfiguration();

	isChipConnected();  // not checking result because it is often wrong
	NRF_Init();
	setPALevel(RF24_PA_MAX);
	setDataRate(RF24_1MBPS);
	setAddressWidth(5);
	enableDynamicPayloads();
	setAutoAck(true);
	setChannel(103);

	openWritingPipe(0xA83C70FD6BLL);
	openReadingPipe(1, 0xA83C70FD6CLL);
	stopListening();

	updateAllDeviceStates();

	HAL_UART_Receive_DMA(huart, &uartRxByte, 1);
}
