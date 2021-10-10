#pragma once

#include <WString.h>

#define DEFAULT_HTTP_PASSWORD "12345"

namespace smart_home {

class Configuration {
    public:
        Configuration();

        void loadOrReset(bool& resetHappened);
        void save() const;

        const String& getName() const;
        void setName(const String& name);

        const String& getPassword() const;
        void setPassword(const String& password);

        bool getSwitcherValue() const;
        void setSwitcherValue(bool value);

        const String& getAdditionalBlob() const;
        void setAdditionalBlob(const String& additionalBlob);

        uint8_t getWiFiResetSequenceLength() const;
        void setWiFiResetSequenceLengthAndSave(uint8_t value);

    private:
        void resetAndSave();

        String name_;
        String password_;  // password for managing device by HTTP, NOT wi-fi passphrase

        bool switcher_;

        String additionalBlob_;  // for allowing App to save some GUI display settings on controller

        uint8_t wifiResetSequenceLength;  // длина кодовой последовательности продолжительности включений контроллера для сброса настроек wi-fi
};

}
