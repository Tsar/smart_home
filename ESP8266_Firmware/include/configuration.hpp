#pragma once

#include <WString.h>
#include <Ticker.h>

#define SWITCHERS_COUNT 3
#define DIMMERS_COUNT   3

#define DEFAULT_HTTP_PASSWORD "12345"

namespace smart_home {

struct DimmerSettings {
    uint16_t valueChangeStep = 10;       // на сколько может меняться значение dimmer value за 20 мс
    uint16_t minLightnessMicros = 8300;  // отступ в микросекундах для наименьшей яркости
    uint16_t maxLightnessMicros = 4000;  // отступ в микросекундах для наибольшей яркости

    bool areValid();
};

class Configuration {
    public:
        Configuration();

        void loadOrReset(bool& resetHappened);
        void save();
        void asyncSave();

        const String& getName() const;
        void setName(const String& name);

        const String& getPassword() const;
        void setPassword(const String& password);

        bool getSwitcherValue(uint8_t index) const;
        void setSwitcherValue(uint8_t index, bool value);

        bool isSwitcherInverted(uint8_t index) const;
        void setSwitcherInverted(uint8_t index, bool inverted);

        uint8_t getSwitcherValueAfterBoot() const;
        void setSwitcherValueAfterBoot(uint8_t value);

        uint16_t getDimmerValue(uint8_t index) const;
        void setDimmerValue(uint8_t index, uint16_t value);

        const volatile DimmerSettings* getDimmersSettings() const;
        void setDimmerSettings(uint8_t index, const DimmerSettings& settings);

        uint16_t getDimmerValueAfterBoot() const;
        void setDimmerValueAfterBoot(uint16_t value);

        const String& getAdditionalBlob() const;
        void setAdditionalBlob(const String& additionalBlob);

        uint8_t getWiFiResetSequenceLength() const;
        void setWiFiResetSequenceLengthAndSave(uint8_t value);

    private:
        void resetAndSave();
        void processAsyncEvents();  // called regularly

        Ticker asyncEventsTicker_;
        bool needsSave_;

        String name_;
        String password_;  // password for managing device by HTTP, NOT wi-fi passphrase

        bool switchers_[SWITCHERS_COUNT];
        bool switchersInverted_[SWITCHERS_COUNT];
        uint8_t switcherValueAfterBoot_;  // if 0xFF, than use saved value

        uint16_t dimmers_[DIMMERS_COUNT];
        volatile DimmerSettings dimmersSettings_[DIMMERS_COUNT];
        uint16_t dimmerValueAfterBoot_;  // if 0xFFFF, than use saved value

        String additionalBlob_;  // for allowing App to save some GUI display settings on controller

        uint8_t wifiResetSequenceLength;  // длина кодовой последовательности продолжительности включений контроллера для сброса настроек wi-fi
};

}
