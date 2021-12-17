#!/usr/bin/env python3

import json
import urllib.request

from oauth2client.service_account import ServiceAccountCredentials

KEYFILE_NAME = 'smart-home-by-tsar-firebase-adminsdk-sho7e-711cef4eec.json'

with open('fcm_token.txt', 'r') as tokenFile:
    FCM_TOKEN = tokenFile.read().strip()

def _get_access_token():
    credentials = ServiceAccountCredentials.from_json_keyfile_name(KEYFILE_NAME, ['https://www.googleapis.com/auth/firebase.messaging'])
    access_token_info = credentials.get_access_token()
    return access_token_info.access_token

if __name__ == "__main__":
    try:
        request = urllib.request.Request(
            'https://fcm.googleapis.com/v1/projects/smart-home-by-tsar/messages:send',
            headers={
                'Authorization': 'Bearer ' + _get_access_token(),
                'Content-Type': 'application/json; UTF-8'
            },
            data=json.dumps({
                #"validate_only": True,
                "message": {
                    "data": {
                        "preved": "medved1234",
                        "hello": "world",
                        "ring": "enable"
                    },
                    "token": FCM_TOKEN
                }
            }).encode('UTF-8')
        )
        response = urllib.request.urlopen(request, timeout=3).read().decode('UTF-8')
        print('Sent FCM successfully')
        print(response)
    except urllib.error.HTTPError as err:
        print('Send FCM failed with error %s: %s' % (err, err.read().decode('UTF-8')))
