Actions to prepare and launch Smart Home Server:

1. sudo apt install make g++ git

2. ./install.sh

3. Choose to install only "RF24 core library"

Next stage is installing RF24 for Python (full doc here: https://nrf24.github.io/RF24/Python.html):

4. sudo apt install python3-dev libboost-python-dev python3-setuptools python3-rpi.gpio

5. sudo ln -s /usr/lib/arm-linux-gnueabihf/libboost_python38.so.1.71.0 /usr/lib/arm-linux-gnueabihf/libboost_python3.so

6. cd rf24libs/RF24/pyRF24/

7. python3 setup.py build

8. sudo python3 setup.py install

9. Change dir to script location

10. Create file "smart_home_auth.txt" with auth token (password)

11. sudo ./smart_home_server.py

Tsar. 27.02.2021
