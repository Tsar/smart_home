#!/usr/bin/env python3

import sys
import json
import struct
import threading
import http.server
import socketserver
import urllib.request
import urllib.parse
from datetime import datetime
from multiprocessing.pool import ThreadPool

HTTP_PORT = 23478

homes = {}
users = {}

def info(msg):
    print('[%s, %d, %s] %s' % (
        datetime.now().strftime('%d.%m.%Y %H:%M:%S.%f'),
        threading.active_count(),
        threading.current_thread().name,
        msg
    ))
    sys.stdout.flush()

def loadConfiguration():
    global homes, users
    with open('configuration.json', 'r') as cfgFile:
        cfg = json.loads(cfgFile.read())
    homes = cfg['homes']
    users = cfg['users']
    for userId, user in users.items():
        if user['home'] not in homes:
            info('WARN: Invalid home %s specified for user %s, ignoring user' % (user['home'], user['id']))
            del users[userId]

def fetchDimmerValue(deviceAddress, devicePassword, dimmerNumber):
    try:
        request = urllib.request.Request('http://%s/get_info?binary&v=2' % deviceAddress, headers={'Password': devicePassword})
        response = urllib.request.urlopen(request, timeout=1.5).read()
    except Exception as err:
        info('WARNING: Failed to get info from device %s [%s]' % (deviceAddress, err))
        return 0
    offset = 6  # skip MAC
    nameLen = struct.unpack('<H', response[offset:offset + 2])[0]
    offset += 2 + nameLen + 1  # skip nameLen, name and input pin
    dimmersCount = struct.unpack('<B', response[offset:offset + 1])[0]
    if dimmerNumber >= dimmersCount:
        info('ERROR: No dimmer %d exists on device %s' % deviceAddress)
        return 0
    offset += 1 + dimmerNumber * 9 + 1  # skip dimmers count, unneeded dimmers and dimmer pin
    return struct.unpack('<H', response[offset:offset + 2])[0]

def applyDimmerValue(deviceAddress, devicePassword, dimmerNumber, dimmerValue):
    dimmerValue = int(dimmerValue)
    if dimmerValue < 0 or dimmerValue > 1000:
        info('WARNING: Tried to apply bad dimmer value %d for device %s' % (dimmerValue, deviceAddress))
        return {'ok': False, 'error': 'INVALID_VALUE', 'error_msg': 'Недопустимое значение %d' % dimmerValue}
    try:
        request = urllib.request.Request('http://%s/set_values?dim%d=%d' % (deviceAddress, dimmerNumber, dimmerValue), headers={'Password': devicePassword})
        response = urllib.request.urlopen(request, timeout=1.5).read()
    except Exception as err:
        info('WARNING: Failed to apply dimmer value for device %s [%s]' % (deviceAddress, err))
        return {'ok': False, 'error': 'DEVICE_UNREACHABLE', 'error_msg': 'Не удалось отправить команду на устройство'}
    respStr = response.decode('UTF-8').strip()
    if respStr not in ['ACCEPTED', 'NOTHING_CHANGED']:
        info('WARNING: Unexpected response after applying dimmer value for device %s [%s]' % (deviceAddress, respStr))
        return {'ok': False, 'error': 'INVALID_ACTION', 'error_msg': 'Устройство ответило непонятным ответом %s' % respStr}
    return {'ok': True}

class HTTPRequestHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def send_response_advanced(self, code, contentType, data):
        dataB = bytes(data, 'UTF-8') if isinstance(data, str) else data
        assert isinstance(dataB, bytes)
        self.send_response(code)
        self.send_header('Content-Type', contentType)
        self.send_header('Content-Length', len(dataB))
        self.end_headers()
        self.wfile.write(dataB)
        self.wfile.flush()
        info('Answered with code %d, content-type %s, response:\n%s' % (code, contentType, data))

    def getUserAndRequestId(self):
        auth = self.headers.get('Authorization', None)
        if auth is None:
            self.send_response_advanced(401, 'text/plain', 'Unauthorized')
            raise RuntimeError('No Authorization header')
        if auth not in users:
            self.send_response_advanced(401, 'text/plain', 'Unauthorized')
            raise RuntimeError('Auth [%s] not found' % auth)
        user = users[auth]

        requestId = self.headers.get('X-Request-Id', None)
        if requestId is None:
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            raise RuntimeError('No X-Request-Id header')

        info('Auth [%s], user id [%s], request id [%s]' % (auth, user['id'], requestId))
        return user, requestId

    def do_HEAD(self):
        info('Handling HEAD request %s' % self.path)

        if self.path == '/v1.0':
            self.send_response_advanced(200, 'text/plain', 'OK')
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

    def do_GET(self):
        info('Handling GET request %s' % self.path)

        for oauthPage in ['suggest', 'token']:
            if self.path.startswith('/oauth/%s' % oauthPage):
                with open('oauth/%s.html' % oauthPage, 'r') as oauthPageFile:
                    oauthPageHTML = oauthPageFile.read()
                self.send_response_advanced(200, 'text/html', oauthPageHTML)
                return

        try:
            user, requestId = self.getUserAndRequestId()
        except RuntimeError as err:
            info('ERROR: %s' % err)
            return

        if self.path == '/v1.0/user/devices':
            devicesResp = []
            for deviceId, device in homes[user['home']].items():
                devicesResp.append({
                    'id': deviceId,
                    'name': device['name'],
                    'room': device['room'],
                    'type': 'devices.types.light',
                    'capabilities': [
                        {
                            'type': 'devices.capabilities.on_off',
                            'retrievable': True
                        },
                        {
                            'type': 'devices.capabilities.range',
                            'retrievable': True,
                            'parameters': {
                                'instance': 'brightness',
                                'unit': 'unit.percent',
                                'random_access': True,
                                'range': {
                                    'min': 0,
                                    'max': 100,
                                    'precision': 0.1
                                }
                            }
                        }
                    ]
                })

            response = {
                'request_id': requestId,
                'payload': {
                    'user_id': user['id'],
                    'devices': devicesResp
                }
            }
            self.send_response_advanced(200, 'application/json', json.dumps(response))
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

    def do_POST(self):
        contentLength = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(contentLength).decode('UTF-8') if contentLength > 0 else None

        info('Handling POST request %s with %s' % (self.path, 'no body' if body is None else 'body %s' % body))

        if self.path == '/oauth/get_token':
            params = urllib.parse.parse_qs(body)
            print(params)
            self.send_response_advanced(200, 'application/json', json.dumps({
                'access_token': params['code'][0],
                'token_type': 'Bearer',
                'expires_in': 2147483647
            }))
            return

        try:
            user, requestId = self.getUserAndRequestId()
        except RuntimeError as err:
            info('ERROR: %s' % err)
            return

        if self.path == '/v1.0/user/unlink':
            self.send_response_advanced(200, 'application/json', json.dumps({'request_id': requestId}))
            return

        if self.path == '/v1.0/user/devices/query':
            devicesReq = json.loads(body)['devices']
            devicesResp = []

            with ThreadPool(processes=len(devicesReq)) as pool:
                asyncResults = {}
                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    device = homes[user['home']][deviceId]
                    asyncResults[deviceId] = pool.apply_async(fetchDimmerValue, (device['address'], device['password'], device['dimmer_number']))

                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    dimmerValue = asyncResults[deviceId].get()
                    devicesResp.append({
                        'id': deviceId,
                        'capabilities': [
                            {
                                'type': 'devices.capabilities.on_off',
                                'state': {
                                    'instance': 'on',
                                    'value': dimmerValue > 0
                                }
                            },
                            {
                                'type': 'devices.capabilities.range',
                                'state': {
                                    'instance': 'brightness',
                                    'value': dimmerValue / 10
                                }
                            }
                        ]
                    })

            response = {
                'request_id': requestId,
                'payload': {
                    'devices': devicesResp
                }
            }
            self.send_response_advanced(200, 'application/json', json.dumps(response))

        elif self.path == '/v1.0/user/devices/action':
            devicesReq = json.loads(body)['payload']['devices']
            devicesResp = []

            with ThreadPool(processes=len(devicesReq)) as pool:
                asyncResults = {}
                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    device = homes[user['home']][deviceId]

                    enabledTarget = None
                    brightnessTarget = None
                    for cap in deviceReq['capabilities']:
                        if cap['type'] == 'devices.capabilities.on_off' and cap['state']['instance'] == 'on':
                            enabledTarget = cap['state']['value']
                            assert isinstance(enabledTarget, bool)
                        elif cap['type'] == 'devices.capabilities.range' and cap['state']['instance'] == 'brightness':
                            brightnessTarget = cap['state']['value']
                            assert isinstance(brightnessTarget, float) or isinstance(brightnessTarget, int)
                        else:
                            info('WARN: Unsupported capability passed: %s' % cap)

                    dimmerValue = None
                    if enabledTarget is None and brightnessTarget is None:
                        info('WARN: No changes will be performed for device %s, INVALID_VALUE error will be returned' % deviceId)
                    elif enabledTarget is None:
                        dimmerValue = brightnessTarget * 10
                    elif brightnessTarget is None:
                        dimmerValue = 1000 if enabledTarget else 0
                    else:
                        dimmerValue = brightnessTarget * 10 if enabledTarget else 0

                    if dimmerValue is not None:
                        asyncResults[deviceId] = pool.apply_async(applyDimmerValue, (device['address'], device['password'], device['dimmer_number'], dimmerValue))

                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    applyResult = asyncResults[deviceId].get() if deviceId in asyncResults else {'ok': False, 'error': 'INVALID_VALUE', 'error_msg': 'Непонятно, что хотели изменить. Запрос к устройству не производился'}
                    if applyResult['ok']:
                        actionResult = {
                            'status': 'DONE'
                        }
                    else:
                        actionResult = {
                            'status': 'ERROR',
                            'error_code': applyResult['error'],
                            'error_message': applyResult['error_msg']
                        }
                    devicesResp.append({
                        'id': deviceId,
                        'action_result': actionResult
                    })

            response = {
                'request_id': requestId,
                'payload': {
                    'devices': devicesResp
                }
            }
            self.send_response_advanced(200, 'application/json', json.dumps(response))

        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

if __name__ == '__main__':
    loadConfiguration()
    server = ThreadedHTTPServer(('', HTTP_PORT), HTTPRequestHandler)
    info('Smart Home Yandex Dialogs HTTP server created, serving forever on port %d...' % HTTP_PORT)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home Yandex Dialogs HTTP server')
        server.shutdown()
        info('Goodbye!')
