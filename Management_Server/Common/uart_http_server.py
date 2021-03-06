import sys
import os.path
from datetime import datetime
import struct
import threading
import socket
import http.server
import socketserver

SERVER_NAME = socket.gethostname()
HTTP_PORT = 9732

PASSWORD_FILE = 'smart_home_password.txt'

password = None

class UARTMessage:
    HEADER_FMT = '<HBB'  # magic, command, payloadSize
    HEADER_SZ = struct.calcsize(HEADER_FMT)

    HEADER_MAGIC = 0xBFCE

    def __init__(self, command=0, payload=b''):
        self.command = command
        self.payload = payload

    def serialize(self):
        return struct.pack(self.HEADER_FMT, self.HEADER_MAGIC, self.command, len(self.payload)) + self.payload

    def parse(self, binary):
        if len(binary) < self.HEADER_SZ:
            return False
        magic, self.command, payloadSize = struct.unpack(self.HEADER_FMT, binary[0:self.HEADER_SZ])
        if magic != self.HEADER_MAGIC or self.HEADER_SZ + payloadSize != len(binary):
            return False
        self.payload = binary[self.HEADER_SZ:]
        return True

def info(msg):
    print('[%s, %d, %s] %s' % (
        datetime.now().strftime('%d.%m.%Y %H:%M:%S'),
        threading.active_count(),
        threading.current_thread().name,
        msg
    ))
    sys.stdout.flush()

def readPasswordFromFile():
    global password
    if not os.path.isfile(PASSWORD_FILE):
        raise RuntimeError('No file "%s" with password' % PASSWORD_FILE)
    with open(PASSWORD_FILE, 'r') as pwdFile:
        password = pwdFile.read().strip()

class BaseUARTMessageHandler(http.server.BaseHTTPRequestHandler):
    def send_response_advanced(self, code, contentType, data):
        dataB = bytes(data, 'UTF-8') if isinstance(data, str) else data
        assert isinstance(dataB, bytes)
        self.send_response(code)
        self.send_header('Content-Type', contentType)
        self.send_header('Content-Length', len(dataB))
        self.end_headers()
        self.wfile.write(dataB)
        self.wfile.flush()

    def do_GET(self):
        self.send_response_advanced(404, 'text/plain', 'Not Found')

    def do_POST(self):
        if self.headers.get('Password', '') != password:
            self.send_response_advanced(401, 'text/plain', 'Unauthorized')
            return

        if self.path == '/uart_message':  # receiving UART-message by HTTP :)
            contentLength = int(self.headers.get('Content-Length', 0))
            if contentLength <= 0:
                self.send_response_advanced(400, 'text/plain', 'Bad Request')
                return
            body = self.rfile.read(contentLength)
            message = UARTMessage()
            if message.parse(body):
                self.handleUartMessage(message)
            else:
                self.send_response_advanced(400, 'text/plain', 'Bad Request')
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

def launchServer(uartMessageHandler):
    readPasswordFromFile()
    server = ThreadedHTTPServer(('', HTTP_PORT), uartMessageHandler)
    info('Smart Home server "%s" created, serving forever on port %d...' % (SERVER_NAME, HTTP_PORT))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home server "%s"...' % SERVER_NAME)
        server.shutdown()
        info('Goodbye!')
