#!/usr/bin/env python3

import json
import time
import datetime
import random
import urllib.request

import numpy as np

DEVICE_ADDRESS = '192.168.199.20'

if __name__ == '__main__':
    ok = 0
    errors = 0
    timings = []
    for i in range(5000):
        try:
            tsStart = datetime.datetime.now()
            request = urllib.request.Request('http://%s/get_info' % DEVICE_ADDRESS, headers={'Password': '12345'})
            response = urllib.request.urlopen(request, timeout=120).read().decode('UTF-8')
            tsFinish = datetime.datetime.now()
            jsonContent = json.loads(response)
            assert 'mac' in jsonContent and 'name' in jsonContent and 'values' in jsonContent
            ok += 1
            delta = tsFinish - tsStart
            deltaMs = delta.total_seconds() * 1000
            print('+1 ok: ms = %.3f' % deltaMs)
            timings.append(deltaMs)
        except KeyboardInterrupt:
            break
        except Exception as e:
            errors += 1
            print('+1 error: %s' % e)
        try:
            time.sleep(random.random() / 10.0)  # max 100 ms
        except KeyboardInterrupt:
            break

    timings = np.array(timings)
    print('\nall timings:', timings)
    print('\n\n%d ok\n%d errors\n\ntimings (ms)' % (ok, errors))
    print(' average = %f' % np.mean(timings))
    for prc in [50, 80, 90, 95, 98, 99.9, 100]:
        print('%4s prc = %.3f' % (prc, np.percentile(timings, prc)))
