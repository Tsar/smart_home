#pragma once

#include <WString.h>

#define SWITCHERS_COUNT 4
#define DIMMERS_COUNT   3

namespace smart_home {

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

    private:
        String name_;
        String password_;  // password for managing device by HTTP, NOT wi-fi passphrase

        bool switchers_[SWITCHERS_COUNT];
        int32_t dimmers_[DIMMERS_COUNT];
};

}
