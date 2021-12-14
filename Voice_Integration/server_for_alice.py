#!/usr/bin/env python3

import sys
import json
import struct
import threading
import http.server
import socketserver
import urllib.request
import urllib.parse
import hashlib
from datetime import datetime
from multiprocessing.pool import ThreadPool

import google_android_find

HTTP_PORT = 23478

AUTHORIZATION_ENDPOINT_PREFIX = '/authorize?'
TOKEN_ENDPOINT = '/get_token'

EXPECTED_REDIRECT_URI = 'https://social.yandex.net/broker/redirect'
EXPECTED_TOKEN_PREFIX = 'Bearer '

TYPE_DIMMER = 'dimmer'
TYPE_SWITCHER = 'switcher'
TYPE_PHONE_FIND = 'phone_find'

YNDX_TYPE = {
    TYPE_DIMMER: 'devices.types.light',
    TYPE_SWITCHER: 'devices.types.switch',
    TYPE_PHONE_FIND: 'devices.types.switch'
}

YNDX_CAPABILITIES = {
    TYPE_DIMMER: [
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
    ],
    TYPE_SWITCHER: [
        {
            'type': 'devices.capabilities.on_off',
            'retrievable': True
        }
    ],
    TYPE_PHONE_FIND: [
        {
            'type': 'devices.capabilities.on_off',
            'retrievable': False
        }
    ]
}

oauthSettings = {}
homes = {}
users = {}

authCodeToUsername = {}  # authorization code -> username
tokenToUsername = {}  # access token -> username

def info(msg):
    print('[%s, %d, %s] %s' % (
        datetime.now().strftime('%d.%m.%Y %H:%M:%S.%f'),
        threading.active_count(),
        threading.current_thread().name,
        msg
    ))
    sys.stdout.flush()

def loadConfiguration():
    global oauthSettings, homes, users, authCodeToUsername, tokenToUsername

    with open('configuration.json', 'r') as cfgFile:
        cfg = json.loads(cfgFile.read())

    oauthSettings = cfg['oauth_settings']
    assert 'client_id' in oauthSettings
    assert 'client_secret' in oauthSettings

    homes = cfg['homes']
    for devices in homes.values():
        for device in devices.values():
            assert 'name' in device
            assert 'room' in device
            assert 'type' in device
            if device['type'] == TYPE_DIMMER:
                assert 'address' in device
                assert 'password' in device
                assert 'dimmer_number' in device
            elif device['type'] == TYPE_SWITCHER:
                assert 'address' in device
                assert 'password' in device
                assert 'switcher_number' in device
            elif device['type'] == TYPE_PHONE_FIND:
                assert 'phone' in device
            else:
                raise RuntimeError('Unknown device type "%s"' % device['type'])

    users = cfg['users']
    for username, user in users.items():
        assert 'home' in user
        assert user['home'] in homes
        assert 'pwd_sha256' in user
        assert 'authorization_code' in user
        assert 'access_token' in user
        authCodeToUsername[user['authorization_code']] = username
        tokenToUsername[user['access_token']] = username

def keepOnlyExistingDevices(devices, home):
    return list(filter(lambda device: device['id'] in home, devices))

def fetchValue(deviceAddress, devicePassword, dimmerNumber, switcherNumber, httpTimeoutSeconds=1.5):
    assert (dimmerNumber is None) != (switcherNumber is None)  # ровно один из этих параметров должен быть None

    try:
        request = urllib.request.Request('http://%s/get_info?binary&v=2' % deviceAddress, headers={'Password': devicePassword})
        response = urllib.request.urlopen(request, timeout=httpTimeoutSeconds).read()
    except Exception as err:
        info('WARNING: Failed to get info from device %s [%s]' % (deviceAddress, err))
        return {'ok': False, 'error': 'DEVICE_UNREACHABLE', 'error_msg': 'Не удалось получить информацию с устройства'}

    offset = 6  # skip MAC
    nameLen = struct.unpack('<H', response[offset:offset + 2])[0]
    offset += 2 + nameLen + 1  # skip nameLen, name and input pin
    dimmersCount = struct.unpack('<B', response[offset:offset + 1])[0]

    if dimmerNumber is not None:
        if dimmerNumber >= dimmersCount:
            info('ERROR: No dimmer %d exists on device %s' % (dimmerNumber, deviceAddress))
            return {'ok': False, 'error': 'INVALID_VALUE', 'error_msg': 'У устройства нет диммера с номером %d' % dimmerNumber}
        offset += 1 + dimmerNumber * 9 + 1  # skip dimmers count, unneeded dimmers and dimmer pin
        dimmerValue = struct.unpack('<H', response[offset:offset + 2])[0]
        return {'ok': True, 'value': dimmerValue}

    elif switcherNumber is not None:
        offset += 1 + dimmersCount * 9  # skip dimmers count and all dimmers
        switchersCount = struct.unpack('<B', response[offset:offset + 1])[0]
        if switcherNumber >= switchersCount:
            info('ERROR: No switcher %d exists on device %s' % (switcherNumber, deviceAddress))
            return {'ok': False, 'error': 'INVALID_VALUE', 'error_msg': 'У устройства нет переключателя с номером %d' % switcherNumber}
        offset += 1 + switcherNumber * 3 + 1  # skip switchers count, unneeded switchers and switcher pin
        switcherValue = struct.unpack('<B', response[offset:offset + 1])[0] != 0
        return {'ok': True, 'value': switcherValue}

    else:
        assert False

def applyValue(deviceAddress, devicePassword, dimmerNumber, switcherNumber, value, httpTimeoutSeconds=1.5):
    assert (dimmerNumber is None) != (switcherNumber is None)  # ровно один из этих параметров должен быть None

    urlParam = None
    if dimmerNumber is not None:
        assert isinstance(value, int)
        if value < 0 or value > 1000:
            info('WARNING: Tried to apply bad dimmer value %d for device %s' % (value, deviceAddress))
            return {'ok': False, 'error': 'INVALID_VALUE', 'error_msg': 'Недопустимое значение %d' % value}
        urlParam = 'dim%d=%d' % (dimmerNumber, value)
    elif switcherNumber is not None:
        assert isinstance(value, bool)
        urlParam = 'sw%d=%d' % (switcherNumber, 1 if value else 0)
    assert urlParam is not None

    try:
        request = urllib.request.Request('http://%s/set_values?%s' % (deviceAddress, urlParam), headers={'Password': devicePassword})
        response = urllib.request.urlopen(request, timeout=httpTimeoutSeconds).read()
    except Exception as err:
        info('WARNING: Failed to apply value for device %s [%s]' % (deviceAddress, err))
        return {'ok': False, 'error': 'DEVICE_UNREACHABLE', 'error_msg': 'Не удалось отправить команду на устройство'}

    respStr = response.decode('UTF-8').strip()
    if respStr not in ['ACCEPTED', 'NOTHING_CHANGED']:
        info('WARNING: Unexpected response after applying value for device %s [%s]' % (deviceAddress, respStr))
        return {'ok': False, 'error': 'INVALID_ACTION', 'error_msg': 'Устройство ответило непонятным ответом %s' % respStr}

    return {'ok': True}

def applyRelativeValue(deviceAddress, devicePassword, dimmerNumber, switcherNumber, value):
    assert dimmerNumber is not None and switcherNumber is None  # относительное значение поддерживается только для диммеров
    assert isinstance(value, int)

    current = fetchValue(deviceAddress, devicePassword, dimmerNumber, None, httpTimeoutSeconds=0.9)
    if not current['ok']:
        return current
    currentValue = current['value']
    assert isinstance(currentValue, int)

    targetValue = min(max(currentValue + value, 0), 1000)
    if currentValue == targetValue:
        info('WARNING: Brightness will not change, current = %d, delta = %d, refusing' % (currentValue, value))
        return {'ok': False, 'error': 'INVALID_VALUE', 'error_msg': 'Яркость не изменится'}

    info('Setting brightness to %d (current = %d, delta = %d)' % (targetValue, currentValue, value))
    return applyValue(deviceAddress, devicePassword, dimmerNumber, None, targetValue, httpTimeoutSeconds=0.9)

def phoneFind(phoneId, value):
    try:
        google_android_find.ringPhone(phoneId)
    except google_android_find.NoParamsException:
        info('WARNING: No params to ring phone %s' % phoneId)
        return {'ok': False, 'error': 'INVALID_ACTION', 'error_msg': 'Не заданы параметры для поиска устройства %s' % phoneId}
    except google_android_find.FailedToStartRingingException as err:
        info('WARNING: Failed to start ringing phone %s [%s]' % (phoneId, err))
        return {'ok': False, 'error': 'DEVICE_UNREACHABLE', 'error_msg': 'Не удалось запустить прозвон устройства %s [%s]' % (phoneId, err)}
    except google_android_find.UnexpectedResponse as err:
        info('WARNING: Unexpected response after sending query to start ringing phone %s [%s]' % (phoneId, err))
        return {'ok': False, 'error': 'INVALID_ACTION', 'error_msg': 'Непонятный ответ на попытку запустить прозвон устройства %s [%s]' % (phoneId, err)}
    return {'ok': True}

class HTTPRequestHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return

    def sendResponseAdvanced(self, code, contentType, data, writeLog=True):
        dataB = data.encode('UTF-8') if isinstance(data, str) else data
        assert isinstance(dataB, bytes)
        self.send_response(code)
        self.send_header('Content-Type', contentType)
        self.send_header('Content-Length', len(dataB))
        self.end_headers()
        self.wfile.write(dataB)
        self.wfile.flush()
        if writeLog:
            info('Answered with code %d, content-type %s, response:\n%s\n' % (code, contentType, data))

    def sendResponseJson(self, code, data):
        self.sendResponseAdvanced(code, 'application/json', json.dumps(data), writeLog=False)
        info('Answered with code %d, response JSON:\n%s\n' % (code, json.dumps(data, indent=2, ensure_ascii=False)))

    def handleAuthorization(self, body=None):
        params = urllib.parse.parse_qs(self.path[len(AUTHORIZATION_ENDPOINT_PREFIX):])
        for requiredParam in ['state', 'redirect_uri', 'response_type', 'client_id']:
            if requiredParam not in params:
                self.sendResponseAdvanced(401, 'text/plain', 'No required GET-parameter %s' % requiredParam)
                return
        clientId = params['client_id'][0]
        if clientId != oauthSettings['client_id']:
            self.sendResponseAdvanced(403, 'text/plain', 'Bad client_id')
            return
        redirectUri = params['redirect_uri'][0]
        if redirectUri != EXPECTED_REDIRECT_URI:
            self.sendResponseAdvanced(403, 'text/plain', 'Bad redirect_uri')
            return
        if params['response_type'][0] != 'code':
            self.sendResponseAdvanced(403, 'text/plain', 'Bad response_type')
            return

        if body is None:
            with open('auth_page.html', 'rb') as authPage:
                self.sendResponseAdvanced(200, 'text/html', authPage.read())
                return

        credentials = urllib.parse.parse_qs(body)
        if requiredParam in ['uname', 'psw']:
            if requiredParam not in credentials:
                self.sendResponseAdvanced(401, 'text/plain', 'No required POST-parameter %s' % requiredParam)
                return
        username = credentials['uname'][0]
        if username not in users:
            self.sendResponseAdvanced(403, 'text/plain', 'Bad username')
            return
        if hashlib.sha256(credentials['psw'][0].encode('UTF-8')).hexdigest().lower() != users[username]['pwd_sha256'].lower():
            self.sendResponseAdvanced(403, 'text/plain', 'Bad password')
            return

        redirectLocation = '%s?%s' % (redirectUri, urllib.parse.urlencode({
            'code': users[username]['authorization_code'],
            'state': params['state'][0],
            'client_id': clientId
        }, quote_via=urllib.parse.quote_plus))
        self.send_response(302)
        self.send_header('Location', redirectLocation)
        self.end_headers()
        info('Answered with code 302, sent header "Location: %s"' % redirectLocation)

    def handleGetToken(self, body):
        if body is None:
            self.sendResponseAdvanced(400, 'text/plain', 'Bad Request')
            return
        params = urllib.parse.parse_qs(body)
        for requiredParam in ['grant_type', 'code', 'redirect_uri', 'client_id', 'client_secret']:
            if requiredParam not in params:
                self.sendResponseAdvanced(401, 'text/plain', 'No required POST-parameter %s' % requiredParam)
                return
        if params['grant_type'][0] != 'authorization_code':
            self.sendResponseAdvanced(403, 'text/plain', 'Bad grant_type')
            return
        if params['client_id'][0] != oauthSettings['client_id']:
            self.sendResponseAdvanced(403, 'text/plain', 'Bad client_id')
            return
        if params['client_secret'][0] != oauthSettings['client_secret']:
            self.sendResponseAdvanced(403, 'text/plain', 'Bad client_secret')
            return
        if params['redirect_uri'][0] != EXPECTED_REDIRECT_URI:
            self.sendResponseAdvanced(403, 'text/plain', 'Bad redirect_uri')
            return
        authCode = params['code'][0]
        if authCode not in authCodeToUsername:
            self.sendResponseAdvanced(403, 'text/plain', 'Bad authorization code')
            return

        username = authCodeToUsername[authCode]
        info('OAuth client obtains access token for user [%s]' % username)

        self.sendResponseJson(200, {
            'access_token': users[username]['access_token'],
            'token_type': 'Bearer',
            'expires_in': 2147483647
        })

    def getUsernameHomeAndRequestId(self):
        token = self.headers.get('Authorization', None)
        if token is None:
            self.sendResponseAdvanced(401, 'text/plain', 'Unauthorized')
            raise RuntimeError('No Authorization header')
        if not token.startswith(EXPECTED_TOKEN_PREFIX):
            self.sendResponseAdvanced(401, 'text/plain', 'Bad access token prefix')
            raise RuntimeError('Bad access token type')
        token = token[len(EXPECTED_TOKEN_PREFIX):]
        if token not in tokenToUsername:
            self.sendResponseAdvanced(401, 'text/plain', 'Unauthorized')
            raise RuntimeError('Token [%s] not found' % token)

        username = tokenToUsername[token]
        homeId = users[username]['home']
        home = homes[homeId]

        requestId = self.headers.get('X-Request-Id', None)
        if requestId is None:
            self.sendResponseAdvanced(400, 'text/plain', 'Bad Request')
            raise RuntimeError('No X-Request-Id header')

        info('Token [%s], username [%s], home id [%s], request id [%s]' % (token, username, homeId, requestId))
        return username, home, requestId

    def do_HEAD(self):
        info('Handling HEAD request %s' % self.path)

        if self.path == '/v1.0':
            self.sendResponseAdvanced(200, 'text/plain', 'OK')
        else:
            self.sendResponseAdvanced(404, 'text/plain', 'Not Found')

    def do_GET(self):
        info('Handling GET request %s' % self.path)

        if self.path.startswith(AUTHORIZATION_ENDPOINT_PREFIX):
            self.handleAuthorization()
            return

        try:
            username, home, requestId = self.getUsernameHomeAndRequestId()
        except RuntimeError as err:
            info('ERROR: %s' % err)
            return

        if self.path == '/v1.0/user/devices':
            response = {
                'request_id': requestId,
                'payload': {
                    'user_id': username,
                    'devices': [
                        {
                            'id': deviceId,
                            'name': device['name'],
                            'room': device['room'],
                            'type': YNDX_TYPE[device['type']],
                            'capabilities': YNDX_CAPABILITIES[device['type']]
                        } for deviceId, device in home.items()
                    ]
                }
            }
            self.sendResponseJson(200, response)
        else:
            self.sendResponseAdvanced(404, 'text/plain', 'Not Found')

    def do_POST(self):
        contentType = self.headers.get('Content-Type', None)
        contentLength = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(contentLength).decode('UTF-8') if contentLength > 0 else None

        info('Handling POST request %s with %s' % (
            self.path,
            'no body' if body is None else (
                'JSON:\n%s' % json.dumps(json.loads(body), indent=2, ensure_ascii=False) if contentType == 'application/json' else 'body %s' % body
            )
        ))

        if self.path.startswith(AUTHORIZATION_ENDPOINT_PREFIX):
            self.handleAuthorization(body=body)
            return

        if self.path == TOKEN_ENDPOINT:
            self.handleGetToken(body)
            return

        try:
            username, home, requestId = self.getUsernameHomeAndRequestId()
        except RuntimeError as err:
            info('ERROR: %s' % err)
            return

        if self.path == '/v1.0/user/unlink':
            self.sendResponseJson(200, {'request_id': requestId})
            return

        if self.path == '/v1.0/user/devices/query':
            devicesReq = keepOnlyExistingDevices(json.loads(body)['devices'], home)
            devicesResp = []

            with ThreadPool(processes=len(devicesReq)) as pool:
                asyncResults = {}
                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    device = home[deviceId]
                    if device['type'] == TYPE_PHONE_FIND:
                        continue
                    asyncResults[deviceId] = pool.apply_async(fetchValue, (
                        device['address'],
                        device['password'],
                        device.get('dimmer_number'),
                        device.get('switcher_number')
                    ))

                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    device = home[deviceId]
                    if device['type'] == TYPE_PHONE_FIND:
                        continue
                    fetchResult = asyncResults[deviceId].get()
                    if fetchResult['ok']:
                        value = fetchResult['value']
                        if device['type'] == TYPE_DIMMER:
                            assert isinstance(value, int)
                            devicesResp.append({
                                'id': deviceId,
                                'capabilities': [
                                    {
                                        'type': 'devices.capabilities.on_off',
                                        'state': {
                                            'instance': 'on',
                                            'value': value > 0
                                        }
                                    },
                                    {
                                        'type': 'devices.capabilities.range',
                                        'state': {
                                            'instance': 'brightness',
                                            'value': value / 10
                                        }
                                    }
                                ]
                            })
                        elif device['type'] == TYPE_SWITCHER:
                            assert isinstance(value, bool)
                            devicesResp.append({
                                'id': deviceId,
                                'capabilities': [
                                    {
                                        'type': 'devices.capabilities.on_off',
                                        'state': {
                                            'instance': 'on',
                                            'value': value
                                        }
                                    }
                                ]
                            })
                        else:
                            raise RuntimeError('Unable to query state for device of type "%s"' % device['type'])
                    else:
                        devicesResp.append({
                            'id': deviceId,
                            'error_code': fetchResult['error'],
                            'error_message': fetchResult['error_msg']
                        })

            response = {
                'request_id': requestId,
                'payload': {
                    'devices': devicesResp
                }
            }
            self.sendResponseJson(200, response)

        elif self.path == '/v1.0/user/devices/action':
            devicesReq = keepOnlyExistingDevices(json.loads(body)['payload']['devices'], home)
            devicesResp = []

            isPhoneFind = lambda deviceReq: home[deviceReq['id']]['type'] == TYPE_PHONE_FIND
            isNotPhoneFind = lambda deviceReq: not isPhoneFind(deviceReq)

            # Если хоть одно устройство в списке имеет тип отличный от phone_find, то нужно исключить из списка все устройства phone_find,
            # чтобы на команды "включи/выключи всё везде" не прозванивать телефоны
            if any(isNotPhoneFind(deviceReq) for deviceReq in devicesReq):
                toExclude = list(filter(isPhoneFind, devicesReq))
                if len(toExclude) > 0:
                    devicesReq = list(filter(isNotPhoneFind, devicesReq))
                    info('Excluded %s devices [%s]' % (TYPE_PHONE_FIND, ', '.join([deviceReq['id'] for deviceReq in toExclude])))

            with ThreadPool(processes=len(devicesReq)) as pool:
                asyncResults = {}
                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    device = home[deviceId]

                    value = None
                    isRelative = False
                    if device['type'] == TYPE_DIMMER:
                        enabledTarget = None
                        brightnessTarget = None
                        for cap in deviceReq['capabilities']:
                            if cap['type'] == 'devices.capabilities.on_off' and cap['state']['instance'] == 'on':
                                enabledTarget = cap['state']['value']
                                assert isinstance(enabledTarget, bool)
                            elif cap['type'] == 'devices.capabilities.range' and cap['state']['instance'] == 'brightness':
                                brightnessTarget = cap['state']['value']
                                isRelative = cap['state'].get('relative', False)
                                assert isinstance(brightnessTarget, float) or isinstance(brightnessTarget, int)
                            else:
                                info('WARN: Unsupported capability passed for device "%s": %s' % (deviceId, cap))

                        if enabledTarget is None and brightnessTarget is None:
                            info('WARN: No changes will be performed for device %s, INVALID_VALUE error will be returned' % deviceId)
                        elif enabledTarget is None:
                            value = int(brightnessTarget * 10)
                        elif brightnessTarget is None:
                            value = 1000 if enabledTarget else 0
                        else:
                            value = int(brightnessTarget * 10) if enabledTarget else 0
                    elif device['type'] in [TYPE_SWITCHER, TYPE_PHONE_FIND]:
                        for cap in deviceReq['capabilities']:
                            if cap['type'] == 'devices.capabilities.on_off' and cap['state']['instance'] == 'on':
                                value = cap['state']['value']
                                assert isinstance(value, bool)
                            else:
                                info('WARN: Unsupported capability passed for device "%s": %s' % (deviceId, cap))
                    else:
                        raise RuntimeError('Unable to handle action for device of type "%s"' % device['type'])

                    if value is not None:
                        if device['type'] in [TYPE_DIMMER, TYPE_SWITCHER]:
                            asyncResults[deviceId] = pool.apply_async(applyRelativeValue if isRelative else applyValue, (
                                device['address'],
                                device['password'],
                                device.get('dimmer_number'),
                                device.get('switcher_number'),
                                value
                            ))
                        elif device['type'] == TYPE_PHONE_FIND:
                            asyncResults[deviceId] = pool.apply_async(phoneFind, (
                                device['phone'],
                                value
                            ))
                        else:
                            raise RuntimeError('Unable to use value for device of type "%s"' % device['type'])

                for deviceReq in devicesReq:
                    deviceId = deviceReq['id']
                    applyResult = asyncResults[deviceId].get() if deviceId in asyncResults else {
                        'ok': False,
                        'error': 'INVALID_VALUE',
                        'error_msg': 'Непонятно, что хотели изменить. Запрос к устройству не производился'
                    }
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
            self.sendResponseJson(200, response)

        else:
            self.sendResponseAdvanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

if __name__ == '__main__':
    loadConfiguration()
    google_android_find.init()

    server = ThreadedHTTPServer(('', HTTP_PORT), HTTPRequestHandler)
    info('Smart Home Yandex Dialogs HTTP server created, serving forever on port %d...' % HTTP_PORT)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home Yandex Dialogs HTTP server')
        server.shutdown()
        info('Goodbye!')
