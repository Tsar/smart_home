#include <WString.h>

namespace smart_home {

class Configuration {
    public:
        Configuration();

        void updateIP(const String& ip);

        void load();
        void save() const;

        String getName() const;
        void setName(const String& name);

        String getPassword() const;
        void setPassword(const String& password);

    private:
        uint16_t readUInt16(int& pos) const;
        String readString(int& pos) const;

        void writeUInt16(int& pos, uint16_t value) const;
        void writeString(int& pos, const String& value) const;

        String ip_;

        String name_;
        String password_;  // password for managing device, NOT wi-fi passphrase
};

}
