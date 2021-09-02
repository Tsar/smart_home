#pragma once

#include <WString.h>

#define SWITCHERS_COUNT 4
#define DIMMERS_COUNT   3

namespace smart_home {

struct DimmerSettings {
    int32_t valueChangeStep = 10;       // на сколько может меняться значение dimmer value за 20 мс
    int32_t minLightnessMicros = 8300;  // отступ в микросекундах для наименьшей яркости
    int32_t maxLightnessMicros = 4000;  // отступ в микросекундах для наибольшей яркости

    bool areValid();
};

class Configuration {
    public:
        Configuration();

        void loadOrReset(bool& resetHappened);
        void save() const;

        String getName() const;
        void setName(const String& name);

        String getPassword() const;
        void setPassword(const String& password);

        bool getSwitcherValue(uint8_t index) const;
        void setSwitcherValue(uint8_t index, bool value);

        bool isSwitcherInverted(uint8_t index) const;
        void setSwitcherInverted(uint8_t index, bool inverted);

        int32_t getDimmerValue(uint8_t index) const;
        void setDimmerValue(uint8_t index, int32_t value);

        const volatile DimmerSettings* getDimmersSettings() const;
        void setDimmerSettings(uint8_t index, const DimmerSettings& settings);

    private:
        void resetAndSave();

        String name_;
        String password_;  // password for managing device by HTTP, NOT wi-fi passphrase

        bool switchers_[SWITCHERS_COUNT];
        bool switchersInverted_[SWITCHERS_COUNT];

        int32_t dimmers_[DIMMERS_COUNT];
        volatile DimmerSettings dimmersSettings_[DIMMERS_COUNT];
};

}
