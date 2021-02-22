/* USER CODE BEGIN Header */
/**
  ******************************************************************************
  * @file           : main.c
  * @brief          : Main program body
  ******************************************************************************
  * @attention
  *
  * <h2><center>&copy; Copyright (c) 2021 STMicroelectronics.
  * All rights reserved.</center></h2>
  *
  * This software component is licensed by ST under BSD 3-Clause license,
  * the "License"; You may not use this file except in compliance with the
  * License. You may obtain a copy of the License at:
  *                        opensource.org/licenses/BSD-3-Clause
  *
  ******************************************************************************
  */
/* USER CODE END Header */
/* Includes ------------------------------------------------------------------*/
#include "main.h"

/* Private includes ----------------------------------------------------------*/
/* USER CODE BEGIN Includes */
#include <flash.h>
#include <RF24.h>
/* USER CODE END Includes */

/* Private typedef -----------------------------------------------------------*/
/* USER CODE BEGIN PTD */

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

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */

#define RESET_DEVICES_CONFIGURATION

#define FLASH_DATA_ADDR 0x08003800

#define MAX_DEVICES_COUNT 30

#define DEVICE_UNAVAILABLE_STATE 0xFF000000

#define NRF_COMMAND_GET_STATE          0x01
#define NRF_COMMAND_RESPONSE_GET_STATE 0xF1

#define UART_HEADER_MAGIC 0xBFCE

#define UART_COMMAND_PING              0x01
#define UART_COMMAND_GET_DEVICES       0x02
#define UART_COMMAND_GET_DEVICE_STATES 0x03
#define UART_COMMAND_SEND_NRF_MESSAGE  0x04

#define UART_COMMAND_RESPONSE_PING                    0x81
#define UART_COMMAND_RESPONSE_GET_DEVICES             0x82
#define UART_COMMAND_RESPONSE_GET_DEVICE_STATES       0x83
#define UART_COMMAND_RESPONSE_SEND_NRF_MESSAGE        0x84
#define UART_COMMAND_RESPONSE_SEND_NRF_MESSAGE_FAILED 0xF4

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/
SPI_HandleTypeDef hspi1;

UART_HandleTypeDef huart1;
DMA_HandleTypeDef hdma_usart1_tx;
DMA_HandleTypeDef hdma_usart1_rx;

/* USER CODE BEGIN PV */

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

/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_DMA_Init(void);
static void MX_SPI1_Init(void);
static void MX_USART1_UART_Init(void);
/* USER CODE BEGIN PFP */

/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */

void fastBlinkForever() {
	while (1) {
		HAL_GPIO_TogglePin(OnboardLED_GPIO_Port, OnboardLED_Pin);
		for (int i = 0; i < 100000; ++i);
	}
}

void readDevicesConfiguration() {
	devicesCount = *((const volatile uint8_t*)FLASH_DATA_ADDR);
	if (devicesCount > MAX_DEVICES_COUNT) {
		// error
		fastBlinkForever();
		return;
	}
	memcpy(devices, (/*volatile*/ uint8_t*)FLASH_DATA_ADDR + 1, devicesCount * sizeof(Device));
}

bool sendNRFMessage(uint8_t writeAttempts, uint8_t readAttempts) {
	while (writeAttempts > 0) {
		write(nrfTxBuf, sizeof(NRFHeader) + nrfTxHeader->payloadSize);
		startListening();

		uint8_t readAttemptsLeft = readAttempts;
		while (readAttemptsLeft > 0) {
			HAL_Delay(50);
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

		if (sendNRFMessage(5, 5) && nrfRxHeader->command == NRF_COMMAND_RESPONSE_GET_STATE && nrfRxHeader->payloadSize == 4) {
			deviceStates[i] = *((uint32_t*)(nrfRxBuf + sizeof(NRFHeader)));
		} else {
			deviceStates[i] = DEVICE_UNAVAILABLE_STATE;
		}
	}
}

void sendUartTxMessage() {
	HAL_UART_Transmit_DMA(&huart1, uartTxBuf, sizeof(UARTHeader) + uartTxHeader->payloadSize);
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

void handleUartGetDevices() {
	uartTxHeader->magic = UART_HEADER_MAGIC;
	uartTxHeader->command = UART_COMMAND_RESPONSE_GET_DEVICES;
	uartTxHeader->payloadSize = devicesCount * sizeof(Device);
	memcpy(uartTxBuf + sizeof(UARTHeader), devices, uartTxHeader->payloadSize);
	sendUartTxMessage();
}

void handleUartGetDeviceStates() {
	uartTxHeader->magic = UART_HEADER_MAGIC;
	uartTxHeader->command = UART_COMMAND_RESPONSE_GET_DEVICE_STATES;
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
	if (nrfRxHeader->command == NRF_COMMAND_RESPONSE_GET_STATE && nrfRxHeader->payloadSize == 4) {
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
			handleUartGetDevices();
			break;
		case UART_COMMAND_GET_DEVICE_STATES:
			handleUartGetDeviceStates();
			break;
		case UART_COMMAND_SEND_NRF_MESSAGE:
			handleUartSendNRFMessage();
			break;
	}
}

void HAL_UART_TxCpltCallback(UART_HandleTypeDef* huart) {
	if (huart != &huart1) return;

	HAL_GPIO_TogglePin(OnboardLED_GPIO_Port, OnboardLED_Pin);
}

// Handle receiving one byte uartRxByte
void HAL_UART_RxCpltCallback(UART_HandleTypeDef* huart) {
	if (huart != &huart1) return;

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

	HAL_UART_Receive_DMA(&huart1, &uartRxByte, 1);
}

/* USER CODE END 0 */

/**
  * @brief  The application entry point.
  * @retval int
  */
int main(void)
{
  /* USER CODE BEGIN 1 */

  /* USER CODE END 1 */

  /* MCU Configuration--------------------------------------------------------*/

  /* Reset of all peripherals, Initializes the Flash interface and the Systick. */
  HAL_Init();

  /* USER CODE BEGIN Init */

  /* USER CODE END Init */

  /* Configure the system clock */
  SystemClock_Config();

  /* USER CODE BEGIN SysInit */

  /* USER CODE END SysInit */

  /* Initialize all configured peripherals */
  MX_GPIO_Init();
  MX_DMA_Init();
  MX_SPI1_Init();
  MX_USART1_UART_Init();
  /* USER CODE BEGIN 2 */

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

  HAL_UART_Receive_DMA(&huart1, &uartRxByte, 1);

  /* USER CODE END 2 */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1)
  {
    /* USER CODE END WHILE */

    /* USER CODE BEGIN 3 */
  }
  /* USER CODE END 3 */
}

/**
  * @brief System Clock Configuration
  * @retval None
  */
void SystemClock_Config(void)
{
  RCC_OscInitTypeDef RCC_OscInitStruct = {0};
  RCC_ClkInitTypeDef RCC_ClkInitStruct = {0};
  RCC_PeriphCLKInitTypeDef PeriphClkInit = {0};

  /** Initializes the RCC Oscillators according to the specified parameters
  * in the RCC_OscInitTypeDef structure.
  */
  RCC_OscInitStruct.OscillatorType = RCC_OSCILLATORTYPE_HSI;
  RCC_OscInitStruct.HSIState = RCC_HSI_ON;
  RCC_OscInitStruct.HSICalibrationValue = RCC_HSICALIBRATION_DEFAULT;
  RCC_OscInitStruct.PLL.PLLState = RCC_PLL_NONE;
  if (HAL_RCC_OscConfig(&RCC_OscInitStruct) != HAL_OK)
  {
    Error_Handler();
  }
  /** Initializes the CPU, AHB and APB buses clocks
  */
  RCC_ClkInitStruct.ClockType = RCC_CLOCKTYPE_HCLK|RCC_CLOCKTYPE_SYSCLK
                              |RCC_CLOCKTYPE_PCLK1;
  RCC_ClkInitStruct.SYSCLKSource = RCC_SYSCLKSOURCE_HSI;
  RCC_ClkInitStruct.AHBCLKDivider = RCC_SYSCLK_DIV1;
  RCC_ClkInitStruct.APB1CLKDivider = RCC_HCLK_DIV1;

  if (HAL_RCC_ClockConfig(&RCC_ClkInitStruct, FLASH_LATENCY_0) != HAL_OK)
  {
    Error_Handler();
  }
  PeriphClkInit.PeriphClockSelection = RCC_PERIPHCLK_USART1;
  PeriphClkInit.Usart1ClockSelection = RCC_USART1CLKSOURCE_PCLK1;
  if (HAL_RCCEx_PeriphCLKConfig(&PeriphClkInit) != HAL_OK)
  {
    Error_Handler();
  }
}

/**
  * @brief SPI1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_SPI1_Init(void)
{

  /* USER CODE BEGIN SPI1_Init 0 */

  /* USER CODE END SPI1_Init 0 */

  /* USER CODE BEGIN SPI1_Init 1 */

  /* USER CODE END SPI1_Init 1 */
  /* SPI1 parameter configuration*/
  hspi1.Instance = SPI1;
  hspi1.Init.Mode = SPI_MODE_MASTER;
  hspi1.Init.Direction = SPI_DIRECTION_2LINES;
  hspi1.Init.DataSize = SPI_DATASIZE_8BIT;
  hspi1.Init.CLKPolarity = SPI_POLARITY_LOW;
  hspi1.Init.CLKPhase = SPI_PHASE_1EDGE;
  hspi1.Init.NSS = SPI_NSS_SOFT;
  hspi1.Init.BaudRatePrescaler = SPI_BAUDRATEPRESCALER_2;
  hspi1.Init.FirstBit = SPI_FIRSTBIT_MSB;
  hspi1.Init.TIMode = SPI_TIMODE_DISABLE;
  hspi1.Init.CRCCalculation = SPI_CRCCALCULATION_DISABLE;
  hspi1.Init.CRCPolynomial = 7;
  hspi1.Init.CRCLength = SPI_CRC_LENGTH_DATASIZE;
  hspi1.Init.NSSPMode = SPI_NSS_PULSE_ENABLE;
  if (HAL_SPI_Init(&hspi1) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN SPI1_Init 2 */

  /* USER CODE END SPI1_Init 2 */

}

/**
  * @brief USART1 Initialization Function
  * @param None
  * @retval None
  */
static void MX_USART1_UART_Init(void)
{

  /* USER CODE BEGIN USART1_Init 0 */

  /* USER CODE END USART1_Init 0 */

  /* USER CODE BEGIN USART1_Init 1 */

  /* USER CODE END USART1_Init 1 */
  huart1.Instance = USART1;
  huart1.Init.BaudRate = 115200;
  huart1.Init.WordLength = UART_WORDLENGTH_8B;
  huart1.Init.StopBits = UART_STOPBITS_1;
  huart1.Init.Parity = UART_PARITY_NONE;
  huart1.Init.Mode = UART_MODE_TX_RX;
  huart1.Init.HwFlowCtl = UART_HWCONTROL_NONE;
  huart1.Init.OverSampling = UART_OVERSAMPLING_16;
  huart1.Init.OneBitSampling = UART_ONE_BIT_SAMPLE_DISABLE;
  huart1.AdvancedInit.AdvFeatureInit = UART_ADVFEATURE_NO_INIT;
  if (HAL_UART_Init(&huart1) != HAL_OK)
  {
    Error_Handler();
  }
  /* USER CODE BEGIN USART1_Init 2 */

  /* USER CODE END USART1_Init 2 */

}

/**
  * Enable DMA controller clock
  */
static void MX_DMA_Init(void)
{

  /* DMA controller clock enable */
  __HAL_RCC_DMA1_CLK_ENABLE();

  /* DMA interrupt init */
  /* DMA1_Channel2_3_IRQn interrupt configuration */
  HAL_NVIC_SetPriority(DMA1_Channel2_3_IRQn, 1, 0);
  HAL_NVIC_EnableIRQ(DMA1_Channel2_3_IRQn);

}

/**
  * @brief GPIO Initialization Function
  * @param None
  * @retval None
  */
static void MX_GPIO_Init(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};

  /* GPIO Ports Clock Enable */
  __HAL_RCC_GPIOA_CLK_ENABLE();

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOA, CE_Pin|CSN_Pin|OnboardLED_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin : IRQ_Pin */
  GPIO_InitStruct.Pin = IRQ_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_IT_FALLING;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(IRQ_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pins : CE_Pin CSN_Pin OnboardLED_Pin */
  GPIO_InitStruct.Pin = CE_Pin|CSN_Pin|OnboardLED_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

}

/* USER CODE BEGIN 4 */

/* USER CODE END 4 */

/**
  * @brief  This function is executed in case of error occurrence.
  * @retval None
  */
void Error_Handler(void)
{
  /* USER CODE BEGIN Error_Handler_Debug */
  /* User can add his own implementation to report the HAL error return state */
  __disable_irq();
  while (1)
  {
  }
  /* USER CODE END Error_Handler_Debug */
}

#ifdef  USE_FULL_ASSERT
/**
  * @brief  Reports the name of the source file and the source line number
  *         where the assert_param error has occurred.
  * @param  file: pointer to the source file name
  * @param  line: assert_param error line source number
  * @retval None
  */
void assert_failed(uint8_t *file, uint32_t line)
{
  /* USER CODE BEGIN 6 */
  /* User can add his own implementation to report the file name and line number,
     ex: printf("Wrong parameters value: file %s on line %d\r\n", file, line) */
  /* USER CODE END 6 */
}
#endif /* USE_FULL_ASSERT */

/************************ (C) COPYRIGHT STMicroelectronics *****END OF FILE****/
