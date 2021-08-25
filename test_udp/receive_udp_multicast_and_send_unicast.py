#!/usr/bin/env python3

# Emulate UDP of Smart Home device

import socket
import struct
from datetime import datetime

MCAST_GRP = '227.16.119.203'
MCAST_PORT = 25061

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind((MCAST_GRP, MCAST_PORT))

mreq = struct.pack("4sl", socket.inet_aton(MCAST_GRP), socket.INADDR_ANY)

sock.setsockopt(socket.IPPROTO_IP, socket.IP_ADD_MEMBERSHIP, mreq)

sockResp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)

while True:
    data, addr = sock.recvfrom(1024)
    print('[%s] Received message [%s] from [%s]' % (datetime.now().strftime('%d.%m.%Y %H:%M:%S'), data, addr))
    if data == b'SMART_HOME_SCAN':
        sock.sendto(b'MAC=XX:XX:XX:XX:XX:XX', (addr[0], 25062))
