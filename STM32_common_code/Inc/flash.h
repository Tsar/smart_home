#ifndef _FLASH_H_
#define _FLASH_H_

void flashUnlock();

//pageAddress - любой адрес, принадлежащий стираемой странице
void flashErase(unsigned int pageAddress);

void flashWriteBegin();

//data - указатель на записываемые данные
//address - адрес во flash
//count - количество записываемых байт, должно быть кратно 2
void flashWrite(unsigned char* data, unsigned int address, unsigned int count);

void flashWriteEnd();

#endif /* _FLASH_H_ */
