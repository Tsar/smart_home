#!/usr/bin/env python3

import socket
import time
from datetime import datetime

MCAST_GRP = '227.16.119.203'
MCAST_PORT = 25061

MULTICAST_TTL = 2

sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
sock.setsockopt(socket.IPPROTO_IP, socket.IP_MULTICAST_TTL, MULTICAST_TTL)

while True:
    sock.sendto(b'SMART_HOME_SCAN', (MCAST_GRP, MCAST_PORT))
    print('[%s] Sent SMART_HOME_SCAN' % datetime.now().strftime('%d.%m.%Y %H:%M:%S'))
    time.sleep(1)
