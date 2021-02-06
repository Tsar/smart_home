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

from RF24 import RF24, RF24_PA_MAX

radio = RF24(22, 0)

SERVER_NAME = socket.gethostname()
HTTP_PORT = 9732

def info(msg):
    print('[%s, %d, %s] %s' % (
        datetime.now().strftime('%d.%m.%Y %H:%M:%S'),
        threading.active_count(),
        threading.current_thread().name,
        msg
    ))
    sys.stdout.flush()

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
        global radio

        if self.path == '/status':
            result = {'ok': True}
            self.send_response_advanced(200, 'application/json', json.dumps(result))
        elif self.path == '/test_send':
            packet = struct.pack('>BBBB', 77, 86, 97, 00)
            result = radio.write(packet)
            if not result:
                self.send_response_advanced(200, 'text/plain', 'Transmission failed or timed out')
            else:
                remsg = -100
                if radio.isAckPayloadAvailable():
                    response = radio.read(1)
                    remsg = int(struct.unpack('>B', response)[0])
                self.send_response_advanced(200, 'text/plain', 'Transmission successful, ack: %d' % remsg)
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

if __name__ == '__main__':
    if not radio.begin():
        raise RuntimeError("radio hardware is not responding")
    radio.setPALevel(RF24_PA_MAX)
    radio.setAddressWidth(5)
    radio.enableAckPayload()
    radio.setChannel(103)
    radio.openWritingPipe(bytearray.fromhex('E2F0F0E8E8'))
    radio.printPrettyDetails()
    radio.stopListening()

    server = ThreadedHTTPServer(('', HTTP_PORT), HTTPRequestHandler)
    info('Smart Home server "%s" created, serving forever on port %d...' % (SERVER_NAME, HTTP_PORT))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home server "%s"...' % SERVER_NAME)
        server.shutdown()
        radio.powerDown()
        info('Goodbye!')
