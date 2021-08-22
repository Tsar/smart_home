#pragma once

#include <WString.h>

#define SWITCHERS_COUNT 4
#define DIMMERS_COUNT   3

namespace smart_home {

struct DimmerSettings {
    int32_t valueChangeStep;     // на сколько может меняться значение dimmer value за 20 мс, дефолт = 10
    int32_t minLightnessMicros;  // отступ в микросекундах для наименьшей яркости, дефолт = 8300
    int32_t maxLightnessMicros;  // отступ в микросекундах для наибольшей яркости, дефолт = 4000
};

class Configuration {
    public:
        Configuration();

        void load();
        void save() const;

        void resetAndSave();

        String getName() const;
        void setName(const String& name);

        String getPassword() const;
        void setPassword(const String& password);

        bool getSwitcherValue(uint8_t index) const;
        void setSwitcherValue(uint8_t index, bool value);

        int32_t getDimmerValue(uint8_t index) const;
        void setDimmerValue(uint8_t index, int32_t value);

        const volatile DimmerSettings* getDimmersSettings() const;
        void setDimmerSettings(uint8_t index, int32_t valueChangeStep, int32_t minLightnessMicros, int32_t maxLightnessMicros);

    private:
        String name_;
        String password_;  // password for managing device by HTTP, NOT wi-fi passphrase

        bool switchers_[SWITCHERS_COUNT];
        int32_t dimmers_[DIMMERS_COUNT];
        volatile DimmerSettings dimmersSettings_[DIMMERS_COUNT];
};

}
