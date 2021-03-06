#!/usr/bin/env python3

# Line for crontab:
#   @reboot cd /home/ubuntu && screen -dmS smart_home_server sudo ./smart_home_server.py

# Command for testing without client:
#   echo  -ne '\xCE\xBF\x01\x02\xFF\xAA' | curl -s -X POST -H 'Password: <password>' --data-binary @- http://<address>:9732/uart_message | hexdump -C

import os.path
import struct
import threading
import json
import time
from concurrent.futures import ThreadPoolExecutor

from RF24 import RF24, RF24_PA_MAX, RF24_1MBPS

from uart_http_server import UARTMessage, BaseUARTMessageHandler, launchServer

class DeviceParams:
    DEVICE_PARAMS_FMT = '<II'  # nameId, uuid
    DEVICE_PARAMS_SZ = struct.calcsize(DEVICE_PARAMS_FMT)

    KEY_NAME_ID = 'nameId'
    KEY_UUID = 'uuid'

    def __init__(self, deviceJsonOrBytes):
        if isinstance(deviceJsonOrBytes, dict):
            self.nameId = deviceJsonOrBytes[self.KEY_NAME_ID]
            self.uuid = deviceJsonOrBytes[self.KEY_UUID]
        elif isinstance(deviceJsonOrBytes, bytes) and len(deviceJsonOrBytes) == self.DEVICE_PARAMS_SZ:
            self.nameId, self.uuid = struct.unpack(self.DEVICE_PARAMS_FMT, deviceJsonOrBytes)
        else:
            raise RuntimeError('Bad type of deviceJsonOrBytes argument')

    def toDict(self):
        return {
            self.KEY_NAME_ID: self.nameId,
            self.KEY_UUID: self.uuid
        }

    def serialize(self):
        return struct.pack(self.DEVICE_PARAMS_FMT, self.nameId, self.uuid)

class NRFMessage:
    HEADER_FMT = '<IBB'  # uuid, command, payloadSize
    HEADER_SZ = struct.calcsize(HEADER_FMT)

    COMMAND_GET_STATE      = 0x01
    COMMAND_RESPONSE_STATE = 0xF1

    def __init__(self, uuid=0, command=0, payload=b''):
        self.uuid = uuid
        self.command = command
        self.payload = payload

    def serialize(self):
        return struct.pack(self.HEADER_FMT, self.uuid, self.command, len(self.payload)) + self.payload

    def parse(self, binary):
        if len(binary) < self.HEADER_SZ:
            return False
        self.uuid, self.command, payloadSize = struct.unpack(self.HEADER_FMT, binary[0:self.HEADER_SZ])
        if self.HEADER_SZ + payloadSize != len(binary):
            return False
        self.payload = binary[self.HEADER_SZ:]
        return True

UARTMessage.COMMAND_PING                 = 0x01
UARTMessage.COMMAND_GET_DEVICES          = 0x02
UARTMessage.COMMAND_SET_DEVICES          = 0x03
UARTMessage.COMMAND_GET_DEVICE_STATES    = 0x04
UARTMessage.COMMAND_UPDATE_DEVICE_STATES = 0x05
UARTMessage.COMMAND_SEND_NRF_MESSAGE     = 0x08

UARTMessage.COMMAND_RESPONSE_PING                    = 0x81
UARTMessage.COMMAND_RESPONSE_GET_DEVICES             = 0x82
UARTMessage.COMMAND_RESPONSE_SET_DEVICES             = 0x83
UARTMessage.COMMAND_RESPONSE_GET_DEVICE_STATES       = 0x84
UARTMessage.COMMAND_RESPONSE_UPDATE_DEVICE_STATES    = 0x85
UARTMessage.COMMAND_RESPONSE_SEND_NRF_MESSAGE        = 0x88
UARTMessage.COMMAND_RESPONSE_SEND_NRF_MESSAGE_FAILED = 0xF8


DEVICES_FILE = 'smart_home_devices.json'

DEVICE_STATE_SIZE = 4
DEVICE_UNAVAILABLE_STATE = struct.pack('<I', 0xFF0000FF)

devices = []       # configuration
devicesLock = threading.Lock()

deviceStates = {}  # kind of a cache
deviceStatesLock = threading.Lock()

radio = RF24(22, 0)

radioExchange = ThreadPoolExecutor(max_workers=1)

def readDevicesConfiguration():
    global devices

    if not os.path.isfile(DEVICES_FILE):
        return

    devices = []
    with open(DEVICES_FILE, 'r') as cfg:
        cfgJson = json.loads(cfg.read())
        for deviceJson in cfgJson['devices']:
            devices.append(DeviceParams(deviceJson))

def saveDevicesConfiguration():
    with open(DEVICES_FILE, 'w') as cfg:
        cfg.write(json.dumps({'devices': [device.toDict() for device in devices]}, indent=2))

def sendNRFMessageInternal(message, writeAttempts, readAttempts):
    global radio

    while writeAttempts > 0:
        radio.write(message.serialize())
        radio.startListening()

        readAttemptsLeft = readAttempts
        while readAttemptsLeft > 0:
            time.sleep(0.05)  # TODO: think about reducing this interval
            hasPayload, pipeId = radio.available_pipe()
            if hasPayload and pipeId == 1:
                response = radio.read(radio.getDynamicPayloadSize())
                rxMessage = NRFMessage()
                if rxMessage.parse(response) and rxMessage.uuid == message.uuid:
                    radio.stopListening()
                    return rxMessage
            readAttemptsLeft -= 1

        radio.stopListening();
        writeAttempts -= 1
    return None

def sendNRFMessage(message, writeAttempts=3, readAttempts=3):
    global radioExchange
    return radioExchange.submit(sendNRFMessageInternal, message, writeAttempts, readAttempts).result()

def updateAllDeviceStates():
    global deviceStates
    global deviceStatesLock

    newDeviceStates = {}
    for device in devices:
        result = sendNRFMessage(NRFMessage(device.uuid, NRFMessage.COMMAND_GET_STATE, b''))
        if result is not None and result.command == NRFMessage.COMMAND_RESPONSE_STATE and len(result.payload) == DEVICE_STATE_SIZE:
            newDeviceStates[device.uuid] = result.payload
        else:
            newDeviceStates[device.uuid] = DEVICE_UNAVAILABLE_STATE
    with deviceStatesLock:
        deviceStates = newDeviceStates

def handleUartPing(message):
    return UARTMessage(
        UARTMessage.COMMAND_RESPONSE_PING,
        b''.join([bytes([byte ^ 0xFF]) for byte in message.payload])
    )

def handleUartGetDevices(afterSet):
    global devicesLock

    with devicesLock:
        return UARTMessage(
            UARTMessage.COMMAND_RESPONSE_SET_DEVICES if afterSet else UARTMessage.COMMAND_RESPONSE_GET_DEVICES,
            b''.join([device.serialize() for device in devices])
        )

def handleUartSetDevices(message):
    global devices
    global devicesLock

    devicesCount = len(message.payload) // DeviceParams.DEVICE_PARAMS_SZ
    with devicesLock:
        devices = []
        for i in range(0, devicesCount * DeviceParams.DEVICE_PARAMS_SZ, DeviceParams.DEVICE_PARAMS_SZ):
            devices.append(DeviceParams(message.payload[i:i + DeviceParams.DEVICE_PARAMS_SZ]))
        saveDevicesConfiguration()

        readDevicesConfiguration()     # read back immediately
        updateAllDeviceStates()        # make our "cache" up to date
    return handleUartGetDevices(True)  # send back for verification

def handleUartGetDeviceStates(update):
    global devicesLock
    global deviceStatesLock

    with devicesLock:
        if update:
            updateAllDeviceStates()
        with deviceStatesLock:
            return UARTMessage(
                UARTMessage.COMMAND_RESPONSE_UPDATE_DEVICE_STATES if update else UARTMessage.COMMAND_RESPONSE_GET_DEVICE_STATES,
                b''.join([deviceStates[device.uuid] for device in devices])
            )

class BadNRFMessageException(Exception):
    pass

def handleUartSendNRFMessage(message):
    global deviceStates
    global deviceStatesLock

    nrfMessage = NRFMessage()
    if not nrfMessage.parse(message.payload):
        raise BadNRFMessageException()
    result = sendNRFMessage(nrfMessage, 5, 5)
    if result is not None:
        # If NRF message has device state update, than update it in our "cache"
        if result.command == NRFMessage.COMMAND_RESPONSE_STATE and len(result.payload) == DEVICE_STATE_SIZE:
            with deviceStatesLock:
                deviceStates[result.uuid] = result.payload

        return UARTMessage(
            UARTMessage.COMMAND_RESPONSE_SEND_NRF_MESSAGE,
            result.serialize()
        )
    else:
        return UARTMessage(
            UARTMessage.COMMAND_RESPONSE_SEND_NRF_MESSAGE_FAILED,
            b''
        )

UART_MESSAGE_HANDLERS = {
    UARTMessage.COMMAND_PING:                 handleUartPing,
    UARTMessage.COMMAND_GET_DEVICES:          lambda _: handleUartGetDevices(False),
    UARTMessage.COMMAND_SET_DEVICES:          handleUartSetDevices,
    UARTMessage.COMMAND_GET_DEVICE_STATES:    lambda _: handleUartGetDeviceStates(False),
    UARTMessage.COMMAND_UPDATE_DEVICE_STATES: lambda _: handleUartGetDeviceStates(True),
    UARTMessage.COMMAND_SEND_NRF_MESSAGE:     handleUartSendNRFMessage
}

class UARTMessageHandler(BaseUARTMessageHandler):
    def handleUartMessage(self, message):
        if message.command not in UART_MESSAGE_HANDLERS:
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            return
        try:
            result = UART_MESSAGE_HANDLERS[message.command](message)
            self.send_response_advanced(200, 'application/octet-stream', result.serialize())
        except BadNRFMessageException:
            self.send_response_advanced(400, 'text/plain', 'Bad NRF Message')

if __name__ == '__main__':
    readDevicesConfiguration()

    if not radio.begin():
        raise RuntimeError('Radio hardware is not responding')
    radio.setPALevel(RF24_PA_MAX)
    radio.setDataRate(RF24_1MBPS)
    radio.setAddressWidth(5)
    #radio.setRetries(5, 15)  # TODO: uncomment if needed
    radio.setAutoAck(True)
    radio.enableDynamicPayloads()
    radio.setChannel(103)

    radio.openWritingPipe(bytearray.fromhex('6BFD703CA8'))
    radio.openReadingPipe(1, bytearray.fromhex('6CFD703CA8'))
    radio.stopListening()
    radio.printPrettyDetails()

    updateAllDeviceStates()

    launchServer(UARTMessageHandler)

    radio.powerDown()
