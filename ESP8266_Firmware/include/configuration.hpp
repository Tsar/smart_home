#pragma once

#include <WString.h>

#define DIMMER_PINS_COUNT 3

namespace smart_home {

class Configuration {
    public:
        Configuration();

        void updateIP(const String& ip);

        void load();
        void save() const;

        void resetAndSave();

        String getName() const;
        void setName(const String& name);

        String getPassword() const;
        void setPassword(const String& password);

        int32_t getValue(uint8_t index) const;
        void setValue(uint8_t index, int32_t value);

    private:
        String ip_;

        String name_;
        String password_;  // password for managing device by HTTP, NOT wi-fi passphrase

        int32_t values_[DIMMER_PINS_COUNT];
};

}
