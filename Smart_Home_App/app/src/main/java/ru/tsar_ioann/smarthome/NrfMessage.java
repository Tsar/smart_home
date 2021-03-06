package ru.tsar_ioann.smarthome;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NrfMessage {
    private static final int HEADER_SZ = 6;

    public static final byte COMMAND_GET_STATE = 0x01;
    public static final byte COMMAND_SET_STATE = 0x02;

    public static final byte COMMAND_RESPONSE_STATE = (byte) 0xF1;

    public static class ParseException extends Exception {
        public ParseException(String errorMessage) {
            super(errorMessage);
        }
    }

    private int uuid;
    private byte command;
    private byte[] payload;

    public NrfMessage(int uuid, byte command, byte[] payload) {
        this.uuid = uuid;
        this.command = command;
        this.payload = payload;
    }

    public NrfMessage(byte[] data) throws ParseException {
        if (data.length < HEADER_SZ) {
            throw new ParseException("Less data than header size");
        }
        ByteBuffer buffer = Utils.createByteBuffer(data);
        uuid = buffer.getInt();
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
                .putInt(uuid)
                .put(command)
                .put((byte)payload.length)
                .put(payload)
                .array();
    }

    public int getUuid() {
        return uuid;
    }

    public byte getCommand() {
        return command;
    }

    public byte[] getPayload() {
        return payload;
    }
}
