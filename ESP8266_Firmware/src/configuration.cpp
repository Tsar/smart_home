#include "configuration.hpp"

#include <EEPROM.h>

namespace smart_home {

namespace {

void alignPos(int& pos) {
    if ((pos % 4) != 0) {
        pos += 4 - (pos % 4);
    }
}

template <class T>
T readT(int& pos) {
    const T result = *reinterpret_cast<const T*>(EEPROM.getConstDataPtr() + pos);
    pos += sizeof(T);
    alignPos(pos);
    return result;
}

template <class T>
void writeT(int& pos, T value) {
    *reinterpret_cast<T*>(EEPROM.getDataPtr() + pos) = value;
    pos += sizeof(T);
    alignPos(pos);
}

uint16_t readUInt16(int& pos) {
    return readT<uint16_t>(pos);
}

void writeUInt16(int& pos, uint16_t value) {
    writeT(pos, value);
}

int32_t readInt32(int& pos) {
    return readT<int32_t>(pos);
}

void writeInt32(int& pos, int32_t value) {
    writeT(pos, value);
}

bool readBool(int& pos) {
    return readT<bool>(pos);
}

void writeBool(int& pos, bool value) {
    writeT(pos, value);
}

String readString(int& pos) {
    const uint16_t length = readUInt16(pos);
    String result;
    result.concat(reinterpret_cast<const char*>(EEPROM.getConstDataPtr() + pos), length);
    pos += length;
    alignPos(pos);
    return result;
}

void writeString(int& pos, const String& value) {
    const uint16_t length = value.length();
    writeUInt16(pos, length);
    memcpy(EEPROM.getDataPtr() + pos, value.c_str(), length);
    pos += length;
    alignPos(pos);
}

}

Configuration::Configuration() {
    EEPROM.begin(4096);
}

void Configuration::load() {
    int pos = 0;
    name_ = readString(pos);
    password_ = readString(pos);
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
        switchers_[i] = readBool(pos);
    }
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
        dimmers_[i] = readInt32(pos);
        dimmersSettings_[i].valueChangeStep = readInt32(pos);
        dimmersSettings_[i].minLightnessMicros = readInt32(pos);
        dimmersSettings_[i].maxLightnessMicros = readInt32(pos);
    }
}

void Configuration::save() const {
    int pos = 0;
    writeString(pos, name_);
    writeString(pos, password_);
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
        writeBool(pos, switchers_[i]);
    }
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
        writeInt32(pos, dimmers_[i]);
        writeInt32(pos, dimmersSettings_[i].valueChangeStep);
        writeInt32(pos, dimmersSettings_[i].minLightnessMicros);
        writeInt32(pos, dimmersSettings_[i].maxLightnessMicros);
    }
    EEPROM.commit();
}

void Configuration::resetAndSave() {
    setName("new-device");
    setPassword("12345");
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
        setSwitcherValue(i, false);
    }
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
        setDimmerValue(i, 0);
        setDimmerSettings(i, 10, 8300, 4000);
    }

    save();
}

String Configuration::getName() const {
    return name_;
}

void Configuration::setName(const String& name) {
    name_ = name;
}

String Configuration::getPassword() const {
    return password_;
}

void Configuration::setPassword(const String& password) {
    password_ = password;
}

bool Configuration::getSwitcherValue(uint8_t index) const {
    return switchers_[index];
}

void Configuration::setSwitcherValue(uint8_t index, bool value) {
    switchers_[index] = value;
}

int32_t Configuration::getDimmerValue(uint8_t index) const {
    return dimmers_[index];
}

void Configuration::setDimmerValue(uint8_t index, int32_t value) {
    dimmers_[index] = value;
}

const volatile DimmerSettings* Configuration::getDimmersSettings() const {
    return dimmersSettings_;
}

void Configuration::setDimmerSettings(uint8_t index, int32_t valueChangeStep, int32_t minLightnessMicros, int32_t maxLightnessMicros) {
    dimmersSettings_[index].valueChangeStep = valueChangeStep;
    dimmersSettings_[index].minLightnessMicros = minLightnessMicros;
    dimmersSettings_[index].maxLightnessMicros = maxLightnessMicros;
}

}
