/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * ENUM user to specify the character size for the serial port settings.
 */

public enum SerialCharacterSize {
    SERIAL_CHARACTER_SIZE_5((byte) 0x00),
    SERIAL_CHARACTER_SIZE_6((byte) 0x01),
    SERIAL_CHARACTER_SIZE_7((byte) 0x02),
    SERIAL_CHARACTER_SIZE_8((byte) 0x03);

    private byte value;

    SerialCharacterSize(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
