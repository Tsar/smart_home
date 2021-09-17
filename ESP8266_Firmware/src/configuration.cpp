#include "configuration.hpp"

#include <EEPROM.h>

#define CONFIGURATION_MAGIC 0x426A74CB
#define CONFIGURATION_FORMAT_VERSION 3  // увеличивать при изменении формата конфигурации

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

Configuration::Configuration() {
    EEPROM.begin(4096);
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
    switcher_ = readBool(pos);
    wifiResetSequenceLength = readUInt8(pos);
}

void Configuration::save() const {
    int pos = 0;
    writeInt32(pos, CONFIGURATION_MAGIC);
    writeInt32(pos, CONFIGURATION_FORMAT_VERSION);
    writeString(pos, name_);
    writeString(pos, password_);
    writeBool(pos, switcher_);
    writeUInt8(pos, wifiResetSequenceLength);
    EEPROM.commit();
}

void Configuration::resetAndSave() {
    setName("MLP 2.0");
    setPassword(DEFAULT_HTTP_PASSWORD);
    setSwitcherValue(false);
    wifiResetSequenceLength = 0;

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

bool Configuration::getSwitcherValue() const {
    return switcher_;
}

void Configuration::setSwitcherValue(bool value) {
    switcher_ = value;
}

uint8_t Configuration::getWiFiResetSequenceLength() const {
    return wifiResetSequenceLength;
}

void Configuration::setWiFiResetSequenceLengthAndSave(uint8_t value) {
    wifiResetSequenceLength = value;
    save();
}

}
