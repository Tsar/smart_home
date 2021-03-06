#!/usr/bin/env python3

# Line for crontab:
#   @reboot cd /<path_to_script> && screen -dmS smart_home_http2uart sudo ./smart_home_http2uart.py

# Command for testing without client:
#   echo  -ne '\xCE\xBF\x01\x02\xFF\xAA' | curl -s -X POST -H 'Password: <password>' --data-binary @- http://<address>:9732/uart_message | hexdump -C

import struct
import threading

import serial

from uart_http_server import UARTMessage, BaseUARTMessageHandler, launchServer

SERIAL_DEVICE = '/dev/ttyUSB0'

serialLock = threading.Lock()

def uartMessageParseHeader(self, binary):
    if len(binary) < self.HEADER_SZ:
        return False
    magic, self.command, self.payloadSize = struct.unpack(self.HEADER_FMT, binary[0:self.HEADER_SZ])
    if magic != self.HEADER_MAGIC:
        return False
    return True

UARTMessage.parseHeader = uartMessageParseHeader

class UARTMessageHandler(BaseUARTMessageHandler):
    def handleUartMessage(self, message):
        global serialLock

        with serialLock:
            try:
                with serial.Serial(port=SERIAL_DEVICE, baudrate=115200) as ser:
                    ser.reset_input_buffer()
                    ser.write(message.serialize())

                    rawResult = ser.read(UARTMessage.HEADER_SZ)
                    result = UARTMessage()
                    while not result.parseHeader(rawResult):
                        rawResult = rawResult[1:] + ser.read(1)
                    if result.payloadSize > 0:
                        rawResult += ser.read(result.payloadSize)

                    if result.parse(rawResult):
                        self.send_response_advanced(200, 'application/octet-stream', result.serialize())
                    else:
                        self.send_response_advanced(500, 'text/plain', 'Internal Server Error (on parsing)')
            except:
                self.send_response_advanced(500, 'text/plain', 'Internal Server Error')

if __name__ == '__main__':
    launchServer(UARTMessageHandler)
