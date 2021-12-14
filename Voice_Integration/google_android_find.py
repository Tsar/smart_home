#!/usr/bin/env python3

import time
import json
import threading
import urllib.request
import urllib.parse
import http.cookiejar

class NoParamsException(Exception):
    pass

class FailedToStartRingingException(Exception):
    pass

class UnexpectedResponse(Exception):
    pass

params = {}
cookies = {}
cookiesLock = threading.Lock()

COOKIES_UPDATE_INTERVAL_SECONDS = 3600  # 1 hour

def _cookieUpdaterRoutine():
    global cookies, cookiesLock

    while True:
        # TODO
        time.sleep(COOKIES_UPDATE_INTERVAL_SECONDS)

def init(launchCookieUpdaterRoutine=True):
    global params, cookies

    with open('google_android_find_params.json', 'r') as paramsFile:
        params = json.loads(paramsFile.read())
        for phoneParams in params.values():
            assert 'cookie_id' in phoneParams
            assert 'post_data' in phoneParams
            cookieId = phoneParams['cookie_id']
            cookies[cookieId] = http.cookiejar.MozillaCookieJar('google_cookies/%s.txt' % cookieId)

    if launchCookieUpdaterRoutine:
        threading.Thread(target=_cookieUpdaterRoutine, daemon=True).start()

def ringPhone(phoneId):
    global cookies, cookiesLock

    if phoneId not in params:
        raise NoParamsException()
    phoneParams = params[phoneId]

    with cookiesLock:
        if phoneParams['cookie_id'] not in cookies:
            raise NoParamsException()
        cookie = cookies[phoneParams['cookie_id']]
        cookie.load(ignore_expires=True)

        xtdc = None
        for cookieElement in cookie:
            if cookieElement.name == 'xtdc':
                xtdc = cookieElement.value
                break
        if xtdc is None:
            raise NoParamsException()

        try:
            opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookie))
            request = urllib.request.Request(
                'https://www.google.com/android/find/xhr/dc/stopRinging?xtdc=%s' % urllib.parse.quote_plus(xtdc),
                headers={'content-type': 'application/x-www-form-urlencoded;charset=UTF-8'},
                data=phoneParams['post_data'].encode('UTF-8')
            )
            response = opener.open(request, timeout=1.5).read()
        except Exception as err:
            raise FailedToStartRingingException(err)

        cookie.save(ignore_expires=True)

    respStr = response.decode('UTF-8')
    if respStr != ")]}'\n[]":
        raise UnexpectedResponse(respStr)

if __name__ == "__main__":
    init(launchCookieUpdaterRoutine=False)
    ringPhone('tsar_phone')
