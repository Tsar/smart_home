package ru.tsar_ioann.smarthome;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UartMessage {
    private static final short HEADER_MAGIC = (short) 0xBFCE;
    private static final int HEADER_SZ = 4;

    public static final byte COMMAND_PING                 = 0x01;
    public static final byte COMMAND_GET_DEVICES          = 0x02;
    public static final byte COMMAND_SET_DEVICES          = 0x03;
    public static final byte COMMAND_GET_DEVICE_STATES    = 0x04;
    public static final byte COMMAND_UPDATE_DEVICE_STATES = 0x05;
    public static final byte COMMAND_SEND_NRF_MESSAGE     = 0x08;

    public static final byte COMMAND_RESPONSE_PING                    = (byte) 0x81;
    public static final byte COMMAND_RESPONSE_GET_DEVICES             = (byte) 0x82;
    public static final byte COMMAND_RESPONSE_SET_DEVICES             = (byte) 0x83;
    public static final byte COMMAND_RESPONSE_GET_DEVICE_STATES       = (byte) 0x84;
    public static final byte COMMAND_RESPONSE_UPDATE_DEVICE_STATES    = (byte) 0x85;
    public static final byte COMMAND_RESPONSE_SEND_NRF_MESSAGE        = (byte) 0x88;
    public static final byte COMMAND_RESPONSE_SEND_NRF_MESSAGE_FAILED = (byte) 0xF8;

    public static class ParseException extends Exception {
        public ParseException(String errorMessage) {
            super(errorMessage);
        }
    }

    private byte command;
    private byte[] payload;

    public UartMessage(byte command, byte[] payload) {
        this.command = command;
        this.payload = payload;
    }

    public UartMessage(byte[] data) throws ParseException {
        if (data.length < HEADER_SZ) {
            throw new ParseException("Less data than header size");
        }
        ByteBuffer buffer = ByteBuffer.allocate(data.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(data);
        buffer.position(0);
        short magic = buffer.getShort();
        if (magic != HEADER_MAGIC) {
            throw new ParseException("Wrong magic");
        }
        command = buffer.get();
        byte payloadSize = buffer.get();
        if (HEADER_SZ + payloadSize != data.length) {
            throw new ParseException("Bad payload size");
        }
        payload = new byte[payloadSize];
        buffer.get(payload);
    }

    public byte[] serialize() {
        return ByteBuffer.allocate(HEADER_SZ + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(HEADER_MAGIC)
                .put(command)
                .put((byte)payload.length)
                .put(payload)
                .array();
    }

    public byte getCommand() {
        return command;
    }

    public byte[] getPayload() {
        return payload;
    }
}
