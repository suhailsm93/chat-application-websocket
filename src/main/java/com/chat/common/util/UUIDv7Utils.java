package com.chat.common.util;

import java.security.SecureRandom;
import java.util.UUID;

public class UUIDv7Utils {
    private static final SecureRandom RANDOM = new SecureRandom();

    public static UUID generateUUIDv7() {
        long timestamp = System.currentTimeMillis();
        byte[] value = new byte[16];
        RANDOM.nextBytes(value);

        // Timestamp in top 48 bits
        value[0] = (byte) ((timestamp >> 40) & 0xFF);
        value[1] = (byte) ((timestamp >> 32) & 0xFF);
        value[2] = (byte) ((timestamp >> 24) & 0xFF);
        value[3] = (byte) ((timestamp >> 16) & 0xFF);
        value[4] = (byte) ((timestamp >> 8) & 0xFF);
        value[5] = (byte) (timestamp & 0xFF);

        // Version 7 in high 4 bits of octet 6
        value[6] = (byte) ((value[6] & 0x0F) | 0x70);
        // Variant 10 in high 2 bits of octet 8
        value[8] = (byte) ((value[8] & 0x3F) | 0x80);

        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) {
            msb = (msb << 8) | (value[i] & 0xFF);
        }
        for (int i = 8; i < 16; i++) {
            lsb = (lsb << 8) | (value[i] & 0xFF);
        }

        return new UUID(msb, lsb);
    }
}