Actions to prepare and check RF24 library:

1. sudo apt install make g++ git

2. ./install.sh

3. Choose to install only "RF24 core library"

4. ./build.sh

5. sudo ./test_send_data_using_rf24

To use python (full doc here: https://nrf24.github.io/RF24/Python.html)

6. sudo apt install python3-dev libboost-python-dev python3-setuptools python3-rpi.gpio

7. sudo ln -s /usr/lib/arm-linux-gnueabihf/libboost_python38.so.1.71.0 /usr/lib/arm-linux-gnueabihf/libboost_python3.so

8. cd rf24libs/RF24/pyRF24/

9. python3 setup.py build

10. sudo python3 setup.py install

11. Change dir to script location

12. sudo ./test_send_data_using_rf24.py

Tsar. 04.02.2021
