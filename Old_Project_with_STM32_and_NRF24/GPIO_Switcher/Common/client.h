#ifndef _CLIENT_H_
#define _CLIENT_H_

#include <main.h>

void smartHomeGPIOSwitcher(
    TIM_HandleTypeDef* pwmTimer1,
    int pwmTimer1Channel,
    TIM_HandleTypeDef* pwmTimer2,
    int pwmTimer2Channel,
    TIM_HandleTypeDef* pwmTimer3,
    int pwmTimer3Channel
);

#endif /* _CLIENT_H_ */
