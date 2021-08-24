#!/usr/bin/env python3

import socket
from datetime import datetime

UDP_IP = '192.168.199.2'  # your local IP
UDP_PORT = 25062

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
sock.bind((UDP_IP, UDP_PORT))

while True:
    data, addr = sock.recvfrom(1024)
    print('[%s] Received message [%s] from [%s]' % (datetime.now().strftime('%d.%m.%Y %H:%M:%S'), data, addr))
