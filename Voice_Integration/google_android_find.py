#!/usr/bin/env python3

import time
import json
import string
import random
import threading
import urllib.request
import urllib.parse

class NoParamsException(Exception):
    pass

class FailedToStartRingingException(Exception):
    pass

class UnexpectedResponse(Exception):
    pass

class SimpliestCookieJar:
    lock = threading.RLock()

    def __init__(self, filename):
        with self.lock:
            self.filename = filename
            self.params = {}
            with open(filename, 'r') as cookieFile:
                for expr in cookieFile.read().strip().split(';'):
                    key, value = expr.strip().split('=', 1)
                    self.params[key] = value

    def get(self):
        with self.lock:
            return '; '.join([key + '=' + value for key, value in self.params.items()])

    def getParam(self, key):
        with self.lock:
            return self.params.get(key)

    def update(self, headers):
        changedParams = 0
        with self.lock:
            for headerName, headerValue in headers:
                if headerName.lower() == 'set-cookie':
                    key, value = headerValue.split(';', 1)[0].split('=', 1)
                    if key not in self.params or self.params[key] != value:
                        self.params[key] = value
                        changedParams += 1
            if changedParams > 0:
                with open(self.filename, 'w') as cookieFile:
                    cookieFile.write(self.get())
        return changedParams

params = {}
cookies = {}

COOKIES_UPDATE_INTERVAL_SECONDS = 1800  # 30 minutes

def _cookieUpdaterRoutine():
    global cookies

    if len(cookies) == 0:
        return
    while True:
        for cookieName, cookie in cookies.items():
            print('Updating cookie "%s"... ' % cookieName, end='')
            try:
                response = urllib.request.urlopen('https://www.google.com/android/find', timeout=3)
                respHeaders = response.getheaders()
            except Exception as err:
                print('FAILED [%s]' % err)
            changedParams = cookie.update(respHeaders)
            print('OK [%d params changed]' % changedParams)

        time.sleep(COOKIES_UPDATE_INTERVAL_SECONDS)

def _randomString(length=12):
    return ''.join(random.choices(string.ascii_letters + string.digits, k=length))

def init(launchCookieUpdaterRoutine=True):
    global params, cookies

    with open('google_android_find_params.json', 'r') as paramsFile:
        params = json.loads(paramsFile.read())
        for phoneParams in params.values():
            assert 'cookie_name' in phoneParams
            assert 'google_id' in phoneParams
            cookieName = phoneParams['cookie_name']
            cookies[cookieName] = SimpliestCookieJar('google_cookies/%s.txt' % cookieName)

    if launchCookieUpdaterRoutine:
        threading.Thread(target=_cookieUpdaterRoutine, daemon=True).start()

def ringPhone(phoneId, stopRinging=False):
    global cookies

    if phoneId not in params:
        raise NoParamsException()
    phoneParams = params[phoneId]

    if phoneParams['cookie_name'] not in cookies:
        raise NoParamsException()
    cookie = cookies[phoneParams['cookie_name']]

    xtdc = cookie.getParam('xtdc')
    if xtdc is None:
        raise NoParamsException()

    try:
        request = urllib.request.Request(
            'https://www.google.com/android/find/xhr/dc/%s?xtdc=%s' % (
                'stopRinging' if stopRinging else 'ring',
                urllib.parse.quote_plus(xtdc)
            ),
            headers={
                'content-type': 'application/x-www-form-urlencoded;charset=UTF-8',
                'cookie': cookie.get()
            },
            data=('["%s","%s"]' % (phoneParams['google_id'], _randomString())).encode('UTF-8')
        )
        response = urllib.request.urlopen(request, timeout=1.5)
        respHeaders = response.getheaders()
        respContent = response.read()
    except Exception as err:
        raise FailedToStartRingingException(err)

    cookie.update(respHeaders)

    respStr = respContent.decode('UTF-8')
    if respStr != ")]}'\n[]":
        raise UnexpectedResponse(respStr)

if __name__ == '__main__':
    init(launchCookieUpdaterRoutine=False)
    testPhoneId = 'tsar_phone'
    print('Starting ringing %s' % testPhoneId)
    ringPhone(testPhoneId)
    time.sleep(5)
    print('Stopping ringing %s' % testPhoneId)
    ringPhone(testPhoneId, stopRinging=True)
