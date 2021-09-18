#include "configuration.hpp"

#include <Arduino.h>
#include <EEPROM.h>
#include <HardwareSerial.h>

#define CONFIGURATION_MAGIC 0x37B9AFBE
#define CONFIGURATION_FORMAT_VERSION 4  // увеличивать при изменении формата конфигурации

#define PROCESS_ASYNC_EVENTS_INTERVAL_MS 2200

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

uint8_t readUInt8(int& pos) {
    return readT<uint8_t>(pos);
}

void writeUInt8(int& pos, uint8_t value) {
    writeT(pos, value);
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

bool DimmerSettings::areValid() {
    return valueChangeStep > 0 && maxLightnessMicros < minLightnessMicros && maxLightnessMicros > 0 && minLightnessMicros < 10000;
}

Configuration::Configuration()
    : needsSave_(false) {
    EEPROM.begin(4096);
    asyncEventsTicker_.attach_ms_scheduled(PROCESS_ASYNC_EVENTS_INTERVAL_MS, [this]() { processAsyncEvents(); });
}

void Configuration::loadOrReset(bool& resetHappened) {
    int pos = 0;

    const int32_t magic = readInt32(pos);
    const int32_t formatVersion = readInt32(pos);
    if (magic != CONFIGURATION_MAGIC || formatVersion != CONFIGURATION_FORMAT_VERSION) {
        resetAndSave();
        resetHappened = true;
        return;
    }
    resetHappened = false;

    name_ = readString(pos);
    password_ = readString(pos);
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
        switchers_[i] = readBool(pos);
        switchersInverted_[i] = readBool(pos);
    }
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
        dimmers_[i] = readInt32(pos);
        dimmersSettings_[i].valueChangeStep = readInt32(pos);
        dimmersSettings_[i].minLightnessMicros = readInt32(pos);
        dimmersSettings_[i].maxLightnessMicros = readInt32(pos);
    }
    additionalBlob_ = readString(pos);
    wifiResetSequenceLength = readUInt8(pos);
}

void Configuration::save() {
    needsSave_ = false;
    const auto tsStart = micros64();

    int pos = 0;
    writeInt32(pos, CONFIGURATION_MAGIC);
    writeInt32(pos, CONFIGURATION_FORMAT_VERSION);
    writeString(pos, name_);
    writeString(pos, password_);
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
        writeBool(pos, switchers_[i]);
        writeBool(pos, switchersInverted_[i]);
    }
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
        writeInt32(pos, dimmers_[i]);
        writeInt32(pos, dimmersSettings_[i].valueChangeStep);
        writeInt32(pos, dimmersSettings_[i].minLightnessMicros);
        writeInt32(pos, dimmersSettings_[i].maxLightnessMicros);
    }
    writeString(pos, additionalBlob_);
    writeUInt8(pos, wifiResetSequenceLength);
    EEPROM.commit();

    const auto tsEnd = micros64();
    Serial.printf("Saved configuration to flash, spent %.2lf ms\n", (tsEnd - tsStart) / 1000.0);
}

void Configuration::asyncSave() {
    needsSave_ = true;
}

void Configuration::processAsyncEvents() {
    if (needsSave_) {
        save();
    }
}

void Configuration::resetAndSave() {
    setName("new-device");
    setPassword(DEFAULT_HTTP_PASSWORD);
    for (uint8_t i = 0; i < SWITCHERS_COUNT; ++i) {
        setSwitcherValue(i, false);
        setSwitcherInverted(i, false);
    }
    for (uint8_t i = 0; i < DIMMERS_COUNT; ++i) {
        setDimmerValue(i, 0);
        setDimmerSettings(i, DimmerSettings());
    }
    setAdditionalBlob("");
    wifiResetSequenceLength = 0;

    save();
}

const String& Configuration::getName() const {
    return name_;
}

void Configuration::setName(const String& name) {
    name_ = name;
}

const String& Configuration::getPassword() const {
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

bool Configuration::isSwitcherInverted(uint8_t index) const {
    return switchersInverted_[index];
}

void Configuration::setSwitcherInverted(uint8_t index, bool inverted) {
    switchersInverted_[index] = inverted;
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

void Configuration::setDimmerSettings(uint8_t index, const DimmerSettings& settings) {
    dimmersSettings_[index].valueChangeStep = settings.valueChangeStep;
    dimmersSettings_[index].minLightnessMicros = settings.minLightnessMicros;
    dimmersSettings_[index].maxLightnessMicros = settings.maxLightnessMicros;
}

const String& Configuration::getAdditionalBlob() const {
    return additionalBlob_;
}

void Configuration::setAdditionalBlob(const String& additionalBlob) {
    additionalBlob_ = additionalBlob;
}

uint8_t Configuration::getWiFiResetSequenceLength() const {
    return wifiResetSequenceLength;
}

void Configuration::setWiFiResetSequenceLengthAndSave(uint8_t value) {
    wifiResetSequenceLength = value;
    save();
}

}
