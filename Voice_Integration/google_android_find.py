#!/usr/bin/env python3

import json
import urllib.request

class NoParamsException(Exception):
    pass

class FailedToStartRingingException(Exception):
    pass

class UnexpectedResponse(Exception):
    pass

def ringPhone(phoneId):
    with open('phone_find_queries.json', 'r') as phoneFindFile:
        phoneFindQueries = json.loads(phoneFindFile.read())
    if phoneId not in phoneFindQueries:
        raise NoParamsException()

    phoneFindQuery = phoneFindQueries[phoneId]
    try:
        request = urllib.request.Request(phoneFindQuery['url'], headers=phoneFindQuery['headers'], data=phoneFindQuery['post_data'].encode('UTF-8'))
        response = urllib.request.urlopen(request, timeout=1.5).read()
    except Exception as err:
        raise FailedToStartRingingException(err)

    respStr = response.decode('UTF-8')
    if respStr != phoneFindQuery['expected_response']:
        raise UnexpectedResponse(respStr)
