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
	uint32_t uuid;
	uint8_t command;
	uint8_t payloadSize;
} NRFHeader;

#pragma pack(pop)

/* USER CODE END PTD */

/* Private define ------------------------------------------------------------*/
/* USER CODE BEGIN PD */

#define DEVICE_UUID 0x00015566

#define FLASH_DATA_ADDR 0x08003800

#define NRF_COMMAND_GET_STATE 0x01
#define NRF_COMMAND_SET_STATE 0x02

#define NRF_COMMAND_RESPONSE_STATE 0xF1

#define OUTPUTS_COUNT 7

/* USER CODE END PD */

/* Private macro -------------------------------------------------------------*/
/* USER CODE BEGIN PM */

/* USER CODE END PM */

/* Private variables ---------------------------------------------------------*/
SPI_HandleTypeDef hspi1;

/* USER CODE BEGIN PV */

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

/* USER CODE END PV */

/* Private function prototypes -----------------------------------------------*/
void SystemClock_Config(void);
static void MX_GPIO_Init(void);
static void MX_SPI1_Init(void);
/* USER CODE BEGIN PFP */

/* USER CODE END PFP */

/* Private user code ---------------------------------------------------------*/
/* USER CODE BEGIN 0 */

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
  MX_SPI1_Init();
  /* USER CODE BEGIN 2 */

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

  /* USER CODE END 2 */

  /* Infinite loop */
  /* USER CODE BEGIN WHILE */
  while (1) {
    tryToReadNRF();
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
  * @brief GPIO Initialization Function
  * @param None
  * @retval None
  */
static void MX_GPIO_Init(void)
{
  GPIO_InitTypeDef GPIO_InitStruct = {0};

  /* GPIO Ports Clock Enable */
  __HAL_RCC_GPIOF_CLK_ENABLE();
  __HAL_RCC_GPIOA_CLK_ENABLE();
  __HAL_RCC_GPIOB_CLK_ENABLE();

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOF, OUT1_Pin|OUT2_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(GPIOA, OUT5_Pin|CE_Pin|CSN_Pin|OnboardLED_Pin
                          |OUT4_Pin|OUT3_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pin Output Level */
  HAL_GPIO_WritePin(OUT6_GPIO_Port, OUT6_Pin, GPIO_PIN_RESET);

  /*Configure GPIO pins : OUT1_Pin OUT2_Pin */
  GPIO_InitStruct.Pin = OUT1_Pin|OUT2_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOF, &GPIO_InitStruct);

  /*Configure GPIO pins : OUT5_Pin CE_Pin CSN_Pin OnboardLED_Pin
                           OUT4_Pin OUT3_Pin */
  GPIO_InitStruct.Pin = OUT5_Pin|CE_Pin|CSN_Pin|OnboardLED_Pin
                          |OUT4_Pin|OUT3_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(GPIOA, &GPIO_InitStruct);

  /*Configure GPIO pin : IRQ_Pin */
  GPIO_InitStruct.Pin = IRQ_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_IT_FALLING;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  HAL_GPIO_Init(IRQ_GPIO_Port, &GPIO_InitStruct);

  /*Configure GPIO pin : OUT6_Pin */
  GPIO_InitStruct.Pin = OUT6_Pin;
  GPIO_InitStruct.Mode = GPIO_MODE_OUTPUT_PP;
  GPIO_InitStruct.Pull = GPIO_NOPULL;
  GPIO_InitStruct.Speed = GPIO_SPEED_FREQ_LOW;
  HAL_GPIO_Init(OUT6_GPIO_Port, &GPIO_InitStruct);

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
