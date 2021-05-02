# include "configuration.hpp"

#include <EEPROM.h>

namespace smart_home {

Configuration::Configuration() {
    EEPROM.begin(4096);
}

void Configuration::updateIP(const String& ip) {
    ip_ = ip;
}

void Configuration::load() {
    int pos = 0;
    name_ = readString(pos);
    password_ = readString(pos);
}

void Configuration::save() const {
    int pos = 0;
    writeString(pos, name_);
    writeString(pos, password_);
    EEPROM.commit();
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

uint16_t Configuration::readUInt16(int& pos) const {
    const uint16_t result = *reinterpret_cast<const uint16_t*>(EEPROM.getConstDataPtr() + pos);
    pos += sizeof(uint16_t);
    return result;
}

String Configuration::readString(int& pos) const {
    const uint16_t length = readUInt16(pos);
    String result;
    result.concat(reinterpret_cast<const char*>(EEPROM.getConstDataPtr() + pos), length);
    pos += length;
    return result;
}

void Configuration::writeUInt16(int& pos, uint16_t value) const {
    *reinterpret_cast<uint16_t*>(EEPROM.getDataPtr() + pos) = value;
    pos += sizeof(uint16_t);
}

void Configuration::writeString(int& pos, const String& value) const {
    const uint16_t length = value.length();
    writeUInt16(pos, length);
    memcpy(EEPROM.getDataPtr() + pos, value.c_str(), length);
    pos += length;
}

}
