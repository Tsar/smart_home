#!/usr/bin/env python3

# Line for crontab:
#   @reboot cd /home/ubuntu && screen -dmS omc ./smart_home_server.py

import sys
from datetime import datetime
import threading
import http.server
import socketserver
import socket
import json
import struct
import time
import math
import copy
from concurrent.futures import ThreadPoolExecutor

from RF24 import RF24, RF24_PA_MAX, RF24_1MBPS

# Fill all STM32 devices here
DEVICES = [0x01, 0x02]

# Fill house configuration
CONFIGURATION = {
    'board_1_LED': {
        'deviceId': 0x01,
        'gpioId': 0,
        'isInverted': True
    },
    'board_2_LED': {
        'deviceId': 0x02,
        'gpioId': 0,
        'isInverted': True
    },
    'testPin': {
        'deviceId': 0x02,
        'gpioId': 1,
        'isInverted': False
    }
}

SERVER_NAME = socket.gethostname()
HTTP_PORT = 9732

# To send to device
COMMAND_GET_STATUS = 0x01
COMMAND_SET_STATUS = 0x02

# Expecting from device
COMMAND_REPORTING_STATUS = 0x00

DEVICE_STATUSES_UPDATE_PERIOD = 20  # seconds

deviceStatuses = {}
for deviceId in DEVICES:
    deviceStatuses[deviceId] = {'status': None, 'updated': 0}
deviceStatusesLock = threading.Lock()

radio = RF24(22, 0)

radioExchange = ThreadPoolExecutor(max_workers=1)

def info(msg):
    print('[%s, %d, %s] %s' % (
        datetime.now().strftime('%d.%m.%Y %H:%M:%S'),
        threading.active_count(),
        threading.current_thread().name,
        msg
    ))
    sys.stdout.flush()

def sendPacket(packet, writeAttemts=3, readAttempts=3):
    global radio

    while writeAttemts > 0:
        info('debug: Sending: %s' % packet.hex())
        radio.write(packet)
        radio.startListening();
        try:
            hasPayload = False
            readAttemptsLeft = readAttempts
            while not hasPayload and readAttemptsLeft > 0:
                time.sleep(0.05)
                hasPayload, pipeId = radio.available_pipe()
                if hasPayload:
                    response = radio.read(radio.getDynamicPayloadSize())
                    info('debug: Got response: %s' % response.hex())
                    return response
                info('debug: No response')
                readAttemptsLeft -= 1
        finally:
            radio.stopListening()
        writeAttemts -= 1
    return None

def byteToBits(byte):
    result = []
    for i in range(8):
        result.append(byte & 1)
        byte >>= 1
    return list(reversed(result))

def bytesToBits(byteValues):
    result = []
    for byte in byteValues:
        result.extend(byteToBits(byte))
    return result

def bitsToByte(bits):
    assert(len(bits) == 8)
    result = 0
    for i in range(8):
        assert(bits[i] in [0, 1])
        result <<= 1
        result |= bits[i]
    return result

def bitsToBytes(bits):
    mod8 = len(bits) % 8
    if mod8 != 0:
        bits.extend([0] * (8 - mod8))
    assert(len(bits) % 8 == 0)
    result = []
    for i in range(0, len(bits), 8):
        result.append(bitsToByte(bits[i:i + 8]))
    return result

def handleDeviceResponse(deviceId, response):
    global deviceStatuses
    global deviceStatusesLock

    if response is not None and len(response) >= 3:
        respDeviceId, respCommand, gpioOutputsCount = struct.unpack('>BBB', response[0:3])
        payload = response[3:]
        payloadExpectedLength = math.ceil(gpioOutputsCount / 8)
        if respDeviceId == deviceId and respCommand == COMMAND_REPORTING_STATUS and len(payload) >= payloadExpectedLength:
            gpioOutputs = bytesToBits(struct.unpack('>' + 'B' * len(payload), payload))[0:gpioOutputsCount]
            status = {
                'status': {
                    'gpioOutputs': gpioOutputs
                },
                'updated': time.time()
            }
            with deviceStatusesLock:
                deviceStatuses[deviceId] = status
            return status
    status = {'status': None, 'updated': time.time()}
    with deviceStatusesLock:
        deviceStatuses[deviceId] = status
    return status

def getDeviceStatus(deviceId):
    packet = struct.pack('>BB', deviceId, COMMAND_GET_STATUS)
    return handleDeviceResponse(deviceId, sendPacket(packet))

def setDeviceStatus(deviceId, status):
    gpioOutputs = copy.deepcopy(status['gpioOutputs'])
    packet = struct.pack('>BBB', deviceId, COMMAND_SET_STATUS, len(gpioOutputs))
    payloadBytes = bitsToBytes(gpioOutputs)
    for byte in payloadBytes:
        packet += struct.pack('>B', byte)
    newStatus = handleDeviceResponse(deviceId, sendPacket(packet))
    if newStatus['status'] is not None and newStatus['status'] == status:
        return True
    print(newStatus['status'])
    print(status)
    return False

def updateStatuses():
    global deviceStatuses
    global deviceStatusesLock
    global radioExchange

    statusFutures = []
    for deviceId in DEVICES:
        statusFutures.append(radioExchange.submit(getDeviceStatus, deviceId))
    for statusFuture in statusFutures:
        statusFuture.result()
    with deviceStatusesLock:
        info('Current devices statuses: %s' % deviceStatuses)

class HTTPRequestHandler(http.server.BaseHTTPRequestHandler):
    def send_response_advanced(self, code, contentType, data):
        dataB = bytes(data, 'UTF-8')
        self.send_response(code)
        self.send_header('Content-type', contentType)
        self.send_header('Content-length', len(dataB))
        self.end_headers()
        self.wfile.write(dataB)
        self.wfile.flush()

    def do_GET(self):
        global deviceStatuses
        global deviceStatusesLock
        global radioExchange

        if self.path == '/ping':
            result = {'ok': True}
            self.send_response_advanced(200, 'application/json', json.dumps(result))

        elif self.path == '/get_all':
            result = {}
            with deviceStatusesLock:
                for settingName, settingCfg in CONFIGURATION.items():
                    deviceStatus = deviceStatuses[settingCfg['deviceId']]['status']
                    if deviceStatus is not None and settingCfg['gpioId'] < len(deviceStatus['gpioOutputs']):
                        gpioValue = deviceStatus['gpioOutputs'][settingCfg['gpioId']]
                        assert(gpioValue in [0, 1])
                        result[settingName] = 1 - gpioValue if settingCfg['isInverted'] else gpioValue
                    else:
                        result[settingName] = None
            self.send_response_advanced(200, 'application/json', json.dumps(result))

        elif self.path.startswith('/setup?'):
            params = self.path[len('/setup?'):].split('&')
            with deviceStatusesLock:
                newDeviceStatuses = copy.deepcopy(deviceStatuses)
            for param in params:
                settingName, settingState = param.split('=')[0:2]
                settingState = int(settingState)
                if settingName not in CONFIGURATION or settingState not in [0, 1]:
                    self.send_response_advanced(400, 'text/plain', 'Bad Request')
                    return
                settingCfg = CONFIGURATION[settingName]
                deviceStatus = newDeviceStatuses[settingCfg['deviceId']]['status']
                if deviceStatus is None:
                    self.send_response_advanced(503, 'text/plain', 'Device Unavailable')
                    return
                if settingCfg['gpioId'] >= len(deviceStatus['gpioOutputs']):
                    self.send_response_advanced(500, 'text/plain', 'Bad gpioId in configuration')
                    return
                deviceStatus['gpioOutputs'][settingCfg['gpioId']] = 1 - settingState if settingCfg['isInverted'] else settingState
            ok = True
            with deviceStatusesLock:
                oldDeviceStatuses = copy.deepcopy(deviceStatuses)
            for deviceId in newDeviceStatuses:
                newStatus = newDeviceStatuses[deviceId]['status']
                if oldDeviceStatuses[deviceId]['status'] != newStatus:
                    ok = ok and radioExchange.submit(setDeviceStatus, deviceId, newStatus).result()
            self.send_response_advanced(200, 'application/json', json.dumps({'ok': ok}))

        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

def regularStatusesUpdateThread():
    while True:
        updateStatuses()
        time.sleep(DEVICE_STATUSES_UPDATE_PERIOD)

if __name__ == '__main__':
    if not radio.begin():
        raise RuntimeError('Radio hardware is not responding')
    radio.setPALevel(RF24_PA_MAX)
    radio.setDataRate(RF24_1MBPS)
    radio.setAddressWidth(5)
    radio.setRetries(5, 15)
    radio.setAutoAck(True)
    radio.enableDynamicPayloads()
    radio.setChannel(103)
    radio.openWritingPipe(bytearray.fromhex('6BFD703CA8'))
    radio.openReadingPipe(1, bytearray.fromhex('6CFD703CA8'))
    radio.printPrettyDetails()
    radio.stopListening()

    threading.Thread(target=regularStatusesUpdateThread, daemon=True).start()

    server = ThreadedHTTPServer(('', HTTP_PORT), HTTPRequestHandler)
    info('Smart Home server "%s" created, serving forever on port %d...' % (SERVER_NAME, HTTP_PORT))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home server "%s"...' % SERVER_NAME)
        server.shutdown()
        radio.powerDown()
        info('Goodbye!')
