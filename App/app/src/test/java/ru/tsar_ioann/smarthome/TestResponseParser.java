package ru.tsar_ioann.smarthome;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestResponseParser {
    @Test
    public void testParseMacAndName() {
        DeviceInfo result = ResponseParser.parseMacAndName("MAC=C4:5B:BE:63:8F:E5;NAME=new-device");
        assertNotNull(result);
        assertEquals("C4:5B:BE:63:8F:E5", result.getMacAddress());
        assertEquals("new-device", result.getName());

        assertNull(ResponseParser.parseMacAndName("MAC=C4:5B:BE:6:8F:E5;NAME=new-device"));
        assertNull(ResponseParser.parseMacAndName("MAC=C4:5B:BE:6Q:8F:E5;NAME=new-device"));
        assertNull(ResponseParser.parseMacAndName("mac=C4:5B:BE:63:8F:E5;NAME=new-device"));
    }
}
