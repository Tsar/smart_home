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
from concurrent.futures import ThreadPoolExecutor

from RF24 import RF24, RF24_PA_MAX, RF24_1MBPS

SERVER_NAME = socket.gethostname()
HTTP_PORT = 9732

COMMAND_GET_STATUS = 0x01
COMMAND_SET_STATUS = 0x02

DEVICES = [0x01, 0x02]
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

def sendPacket(packet):
    global radio

    info('debug: Sending: %s' % packet.hex())
    radio.write(packet)
    radio.startListening();
    time.sleep(0.05)
    try:
        hasPayload, pipeId = radio.available_pipe()
        if hasPayload:
            response = radio.read(radio.payloadSize)
            info('debug: Got response: %s' % response.hex())
            return response
        info('debug: No response')
    finally:
        radio.stopListening()
    return None

#def sendPacket(packet, attempts=3):
#    global radio
#
#    result = False
#    while not result and attempts > 0:
#        info('debug: Sending: %s' % packet.hex())
#        result = radio.write(packet)
#        if not result:
#            attempts -= 1
#            time.sleep(0.05)
#    if not result:
#        info('debug: All attempts to send failed')
#        return None
#    if radio.isAckPayloadAvailable():
#        length = radio.getDynamicPayloadSize()
#        if length > 0:
#            response = radio.read(length)
#            info('debug: Got response: %s' % response.hex())
#            return response
#    info('debug: Sent OK, but no response data')
#    return None

def getBits(byte):
    result = []
    for i in range(8):
        result.append(byte & 1)
        byte >>= 1
    return list(reversed(result))

def getBitValues(byteValues):
    result = []
    for byte in byteValues:
        result.extend(getBits(byte))
    return result

def getDeviceStatus(deviceId):
    global deviceStatuses
    global deviceStatusesLock

    packet = struct.pack('>BB', deviceId, COMMAND_GET_STATUS)
    response = sendPacket(packet)
    if response is not None and len(response) >= 3:
        respDeviceId, respCommand, gpioOutputsCount = struct.unpack('>BBB', response[0:3])
        payload = response[3:]
        payloadExpectedLength = math.ceil(gpioOutputsCount / 8)
        if respDeviceId == deviceId and respCommand == COMMAND_GET_STATUS and len(payload) >= payloadExpectedLength:
            gpioOutputs = getBitValues(struct.unpack('>' + 'B' * len(payload), payload))[0:gpioOutputsCount]
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
        if self.path == '/ping':
            result = {'ok': True}
            self.send_response_advanced(200, 'application/json', json.dumps(result))
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

if __name__ == '__main__':
    if not radio.begin():
        raise RuntimeError('Radio hardware is not responding')
    radio.setPALevel(RF24_PA_MAX)
    radio.setDataRate(RF24_1MBPS)
    radio.setAddressWidth(5)
    radio.setAutoAck(True)
    radio.enableDynamicPayloads()
    radio.setChannel(103)
    radio.openWritingPipe(bytearray.fromhex('6BFD703CA8'))
    radio.openReadingPipe(1, bytearray.fromhex('6CFD703CA8'))
    radio.printPrettyDetails()
    radio.stopListening()

    statusFutures = []
    for deviceId in DEVICES:
        statusFutures.append(radioExchange.submit(getDeviceStatus, deviceId))
    for statusFuture in statusFutures:
        statusFuture.result()
    with deviceStatusesLock:
        info('Current devices statuses: %s' % deviceStatuses)

    server = ThreadedHTTPServer(('', HTTP_PORT), HTTPRequestHandler)
    info('Smart Home server "%s" created, serving forever on port %d...' % (SERVER_NAME, HTTP_PORT))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home server "%s"...' % SERVER_NAME)
        server.shutdown()
        radio.powerDown()
        info('Goodbye!')
