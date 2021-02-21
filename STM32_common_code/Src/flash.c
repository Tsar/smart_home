#include <flash.h>

#include <main.h>

// Articles about flash: https://habrahabr.ru/post/213771/ and http://easystm32.ru/for-beginners/38-flash-stm32

void flashUnlock() {
  FLASH->KEYR = 0x45670123;
  FLASH->KEYR = 0xCDEF89AB;
}

//pageAddress - любой адрес, принадлежащий стираемой странице
void flashErase(unsigned int pageAddress) {
  while (FLASH->SR & FLASH_SR_BSY);
  if (FLASH->SR & FLASH_SR_EOP) {
    FLASH->SR = FLASH_SR_EOP;
  }

  FLASH->CR |= FLASH_CR_PER;
  FLASH->AR = pageAddress;
  FLASH->CR |= FLASH_CR_STRT;
  while (!(FLASH->SR & FLASH_SR_EOP));
  FLASH->SR = FLASH_SR_EOP;
  FLASH->CR &= ~FLASH_CR_PER;
}

void flashWriteBegin() {
  while (FLASH->SR & FLASH_SR_BSY);
  if (FLASH->SR & FLASH_SR_EOP) {
    FLASH->SR = FLASH_SR_EOP;
  }
  FLASH->CR |= FLASH_CR_PG;
}

//data - указатель на записываемые данные
//address - адрес во flash
//count - количество записываемых байт, должно быть кратно 2
void flashWrite(unsigned char* data, unsigned int address, unsigned int count) {
  unsigned int i;
  for (i = 0; i < count; i += 2) {
    *(volatile unsigned short*)(address + i) = (((unsigned short)data[i + 1]) << 8) + data[i];
    while (!(FLASH->SR & FLASH_SR_EOP));
    FLASH->SR = FLASH_SR_EOP;
  }
}

void flashWriteEnd() {
  FLASH->CR &= ~(FLASH_CR_PG);
}
