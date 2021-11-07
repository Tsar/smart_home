#!/usr/bin/env python3

import sys
import json
import threading
import http.server
import socketserver
import urllib.request
import urllib.parse
from datetime import datetime

HTTP_PORT = 23478

def info(msg):
    print('[%s, %d, %s] %s' % (
        datetime.now().strftime('%d.%m.%Y %H:%M:%S'),
        threading.active_count(),
        threading.current_thread().name,
        msg
    ))
    sys.stdout.flush()

class HTTPRequestHandler(http.server.BaseHTTPRequestHandler):
#    def log_message(self, format, *args):
#        return

    def send_response_advanced(self, code, contentType, data):
        dataB = bytes(data, 'UTF-8') if isinstance(data, str) else data
        assert isinstance(dataB, bytes)
        self.send_response(code)
        self.send_header('Content-Type', contentType)
        self.send_header('Content-Length', len(dataB))
        self.end_headers()
        self.wfile.write(dataB)
        self.wfile.flush()

    def do_HEAD(self):
        info('Received HEAD request %s' % self.path)

        if self.path == '/v1.0':
            self.send_response_advanced(200, 'text/plain', 'OK')
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

    def do_GET(self):
        info('Received GET request %s' % self.path)

        for oauthPage in ['suggest', 'token']:
            if self.path.startswith('/oauth/%s' % oauthPage):
                with open('oauth/%s.html' % oauthPage) as oauthPageFile:
                    oauthPageHTML = oauthPageFile.read()
                self.send_response_advanced(200, 'text/html', oauthPageHTML)
                return

        if self.path.startswith('/oauth/handle_token'):
            params = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)

            redirectUri = params['redirect_uri'][0]
            assert redirectUri == 'https://social.yandex.net/broker/redirect'

            postData = {
                'code': params['access_token'][0],
                'state': params['state'][0],
                'client_id': params['client_id'][0]
            }
            if 'scope' in params:
                postData['scope'] = params['scope'][0]
            else:
                postData['scope'] = 'home:lights'
            print(postData)

            internalResponse = urllib.request.urlopen(urllib.request.Request(
                redirectUri,
                data=json.dumps(postData).encode('UTF-8'),
                headers={'Content-Type': 'application/json'}
            ))

            self.send_response_advanced(200, 'text/plain', 'OK, you may close this page (redirect_uri answered: %s)' % internalResponse)

        # TODO: refactor copypaste
        auth = self.headers.get('Authorization', None)
        if auth is None:
            info('No Authorization header')
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            return

        requestId = self.headers.get('X-Request-Id', None)
        if requestId is None:
            info('No X-Request-Id header')
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            return

        if self.path == '/v1.0/user/devices':
            response = {
                'request_id': requestId,
                'payload': {
                    'user_id': 'Medved',  # TODO
                    'devices': [
                        {
                            'id': 'C4-5B-BE-4B-48-07',
                            'name': 'Свет в большой комнате',
                            'room': 'Большая комната',
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
                        }
                    ]
                }
            }
            self.send_response_advanced(200, 'application/json', json.dumps(response))
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

    def do_POST(self):
        info('Received POST request %s' % self.path)

        # TODO: refactor copypaste
        auth = self.headers.get('Authorization', None)
        if auth is None:
            info('No Authorization header')
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            return

        requestId = self.headers.get('X-Request-Id', None)
        if requestId is None:
            info('No X-Request-Id header')
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            return

        if self.path == '/v1.0/user/unlink':
            self.send_response_advanced(200, 'application/json', json.dumps({'request_id': requestId}))
            return

        contentLength = int(self.headers.get('Content-Length', 0))
        if contentLength <= 0:
            info('No header Content-Length in POST request')
            self.send_response_advanced(400, 'text/plain', 'Bad Request')
            return
        body = self.rfile.read(contentLength)

        if self.path == '/v1.0/user/devices/query':
            # TODO
            pass
        elif self.path == '/v1.0/user/devices/action':
            # TODO
            pass
        else:
            self.send_response_advanced(404, 'text/plain', 'Not Found')

class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    pass

if __name__ == '__main__':
    server = ThreadedHTTPServer(('', HTTP_PORT), HTTPRequestHandler)
    info('Smart Home Yandex Dialogs HTTP server created, serving forever on port %d...' % HTTP_PORT)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        info('Halting Smart Home Yandex Dialogs HTTP server')
        server.shutdown()
        info('Goodbye!')
