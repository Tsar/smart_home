#include <ctime>       // time()
#include <iostream>    // cin, cout, endl
#include <string>      // string, getline()
#include <time.h>      // CLOCK_MONOTONIC_RAW, timespec, clock_gettime()
#include <RF24/RF24.h> // RF24, RF24_PA_LOW, delay()

using namespace std;

/****************** Linux ***********************/
// Radio CE Pin, CSN Pin, SPI Speed
// CE Pin uses GPIO number with BCM and SPIDEV drivers, other platforms use their own pin numbering
// CS Pin addresses the SPI bus number at /dev/spidev<a>.<b>
// ie: RF24 radio(<ce_pin>, <a>*10+<b>); spidev1.0 is 10, spidev1.1 is 11 etc..

// Generic:
RF24 radio(22, 0);
/****************** Linux (BBB,x86,etc) ***********************/
// See http://nRF24.github.io/RF24/pages.html for more information on usage
// See http://iotdk.intel.com/docs/master/mraa/ for more information on MRAA
// See https://www.kernel.org/doc/Documentation/spi/spidev for more information on SPIDEV

uint8_t nrf_data[32] = {0,};

int main(int argc, char** argv) {
    // perform hardware check
    if (!radio.begin()) {
        cout << "radio hardware is not responding!!" << endl;
        return 0;
    }

    if (!radio.isChipConnected()) {
        cout << "can not see device!!" << endl;
        return 0;
    }

    uint8_t address[] = { 0x6B, 0xFD, 0x70, 0x3C, 0xA8 };

    radio.setAddressWidth(5);
    radio.enableAckPayload();
    radio.setChannel(103);
    radio.setPALevel(RF24_PA_MAX); // RF24_PA_MAX is default.

    // set the TX address of the RX node into the TX pipe
    radio.openWritingPipe(address);     // always uses pipe 0
    radio.stopListening();

    // For debugging info
    // radio.printDetails();       // (smaller) function that prints raw register values
    radio.printPrettyDetails(); // (larger) function that prints human readable data

    nrf_data[0] = 77;
    nrf_data[1] = 86;
    nrf_data[2] = 97;

    uint8_t remsg = 0;

    while (true) {
        bool report = radio.write(nrf_data, strlen((const char*)nrf_data));         // transmit & save the report
        if (report) {
            // payload was delivered
            cout << "Transmission successful!" << endl;
            if (radio.isAckPayloadAvailable()) {
                radio.read(&remsg, sizeof(remsg));
                cout << "Ack: " << static_cast<int>(remsg) << endl;
            }
        } else {
            // payload was not delivered
            cout << "Transmission failed or timed out" << endl;
        }

        // to make this example readable in the terminal
        delay(100);  // slow transmissions down by 1 second
    }

    return 0;
}
