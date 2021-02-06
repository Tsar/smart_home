#!/usr/bin/env python3

import time
import struct
from RF24 import RF24, RF24_PA_MAX

if __name__ == "__main__":
    radio = RF24(22, 0)
    if not radio.begin():
        raise RuntimeError("radio hardware is not responding")
    radio.setPALevel(RF24_PA_MAX)
    radio.setAddressWidth(5)
    radio.enableAckPayload()
    radio.setChannel(103)
    radio.openWritingPipe(bytearray.fromhex('6BFD703CA8'))
    radio.printPrettyDetails()
    radio.stopListening()
    try:
        packet = struct.pack('>BBBB', 77, 86, 97, 00)
        while True:
            result = radio.write(packet)
            if not result:
                print("Transmission failed or timed out")
            else:
                print("Transmission successful!")
                if radio.isAckPayloadAvailable():
                    response = radio.read(1)
                    remsg = int(struct.unpack('>B', response)[0])
                    print("Ack: %d" % remsg)
            time.sleep(0.1)
    except KeyboardInterrupt:
        radio.powerDown()
