#!/usr/bin/env python3

# Emulate some HTTP handlers and UDP multicast handler of Smart Home device

import sys
import math
import json
import socket
import struct
import threading
import http.server
import socketserver
import urllib.parse
from datetime import datetime

MAC_ADDRESS = 'AA:BB:CC:DD:EE:FF'

HTTP_PORT = 80

UDP_MULTICAST_GRP = '227.16.119.203'
UDP_MULTICAST_PORT = 25061

DIMMER_MAX_VALUE = 1000

name = 'device-emulator'

password = '12345'

values = {
    'dim0': 500,
    'dim1': 250,
    'dim2': 750,
    'sw0': 0,
    'sw1': 1,
    'sw2': 1,
    'sw3': 0
}

dimmers_settings = {
    'dim0': {
        'value_change_step': 10,
        'min_lightness_micros': 8300,
        'max_lightness_micros': 4000
    },
    'dim1': {
        'value_change_step': 10,
        'min_lightness_micros': 8300,
        'max_lightness_micros': 4000
    },
    'dim2': {
        'value_change_step': 10,
        'min_lightness_micros': 8300,
        'max_lightness_micros': 4000
    }
}

switchers_inverted = {
    'sw0': 1,
    'sw1': 1,
    'sw2': 0,
    'sw3': 1
}

def info(msg):
    print('[%s, %d, %s] %s' % (
        datetime.now().strftime('%d.%m.%Y %H:%M:%S'),
        threading.active_count(),
        threading.current_thread().name,
        msg
    ))
    sys.stdout.flush()

def dimmerValueToMicros(dimmer):
    minLM = dimmers_settings[dimmer]['min_lightness_micros'];
    maxLM = dimmers_settings[dimmer]['max_lightness_micros'];
    return round(math.cos(math.pi * (values[dimmer] - 1) / 2 / (DIMMER_MAX_VALUE - 1)) * (minLM - maxLM) + maxLM)

class HTTPRequestHandler(http.server.BaseHTTPRequestHandler):
#    def log_message(self, format, *args):
#        return

    def send_response_advanced(self, code, contentType, data):
        dataB = bytes(data, 'UTF-8') if isinstance(data, str) else data
        assert isinstance(dataB, bytes)
        self.send_response(code)
        self.send_header('Content-Type', contentType)
        self.send_header('Content-Length', len(dataB))
        self.end_headers()
        self.wfile.write(dataB)
        self.wfile.flush()

    def checkPassword(self):
        if self.headers.get('Password', '') != password:
            self.send_response_advanced(403, 'text/plain', 'Forbidden')
            return False
        return True

    def handleSetValues(self, argsStr):
        global values

        try:
            argsList = argsStr.split('&')
            for arg in argsList:
                key, value = arg.split('=')[0:2]
                value = int(value)
                assert key in values
                if key.startswith('dim'):
                    assert 0 <= value and value <= 1000
                elif key.startswith('sw'):
                    assert value in [0, 1]
                values[key] = value
            self.send_response_advanced(200, 'text/plain', 'ACCEPTED\n')  # TODO: support NOTHING_CHANGED response
        except:
            self.send_response_advanced(400, 'text/plain', 'Bad Request')

    def do_GET(self):
        if not self.checkPassword():
            return

        if self.path == '/get_info?minimal':
            result = {
                'mac': MAC_ADDRESS,
                'name': name
            }
            self.send_response_advanced(200, 'application/json', json.dumps(result, indent=2) + '\n')

        elif self.path == '/get_info':
            result = {
                'mac': MAC_ADDRESS,
                'name': name,
                'values': values,
                'micros': {
                    'dim0': dimmerValueToMicros('dim0'),
                    'dim1': dimmerValueToMicros('dim1'),
                    'dim2': dimmerValueToMicros('dim2')
                },
                'dimmers_settings': dimmers_settings,
                'switchers_inverted': switchers_inverted,
                'order': {
                    'dimmers': [0, 1, 2],
                    'switchers': [0, 1, 2, 3]
                }
            }
            self.send_response_advanced(200, 'application/json', json.dumps(result, indent=2) + '\n')

        elif self.path.startswith('/set_values?'):
            self.handleSetValues(self.path[len('/set_values?'):])

        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

    def do_POST(self):
        global name, dimmers_settings, switchers_inverted

        if not self.checkPassword():
            return

        contentLength = int(self.headers.get('Content-Length', 0))
        if contentLength <= 0:
            info('No header Content-Length in POST request')
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            return
        body = self.rfile.read(contentLength)

        if self.path == '/set_values':
            self.handleSetValues(body.decode('UTF-8'))

        elif self.path == '/set_settings':
            try:
                argsList = body.decode('UTF-8').split('&')
                for arg in argsList:
                    key, value = arg.split('=')[0:2]
                    if key == 'name':
                        name = urllib.parse.unquote_plus(value)
                    elif key.startswith('dim'):
                        assert key in dimmers_settings
                        dimSettings = [int(dimSetting) for dimSetting in value.split(',')]
                        assert len(dimSettings) == 3
                        dimmers_settings[key] = {
                            'value_change_step': dimSettings[0],
                            'min_lightness_micros': dimSettings[1],
                            'max_lightness_micros': dimSettings[2]
                        }
                    elif key.startswith('sw'):
                        assert key in switchers_inverted
                        value = int(value)
                        assert value in [0, 1]
                        switchers_inverted[key] = value
                self.send_response_advanced(200, 'text/plain', 'ACCEPTED\n')
            except:
                self.send_response_advanced(400, 'text/plain', 'Bad Request')

        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

def handleUdpMulticastForever():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((UDP_MULTICAST_GRP, UDP_MULTICAST_PORT))

    mreq = struct.pack('4sl', socket.inet_aton(UDP_MULTICAST_GRP), socket.INADDR_ANY)

    sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

    sockResp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)

    while True:
        data, addr = sock.recvfrom(1024)
        info('Received UDP message [%s] from [%s]' % (data, addr))
        if data == b'SMART_HOME_SCAN':
            udpResponse = ('MAC=%s\nNAME=%s' % (MAC_ADDRESS, name)).encode('UTF-8')
            sock.sendto(udpResponse, (addr[0], 25062))
            info('Sent UDP response [%s]' % udpResponse)

if __name__ == '__main__':
    threading.Thread(target=handleUdpMulticastForever, daemon=True).start()
    server = ThreadedHTTPServer(('', HTTP_PORT), HTTPRequestHandler)
    info('Smart Home Device Emulator HTTP server created, serving forever on port %d...' % HTTP_PORT)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home Device Emulator HTTP server')
        server.shutdown()
        info('Goodbye!')
