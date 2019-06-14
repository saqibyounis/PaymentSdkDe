/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * ENUM used to specify the number of stop bits used n the serial port settings
 */

public enum SerialPortStopBits {
    SERIAL_PORT_STOP_BITS_1((byte) 0x00),
    SERIAL_PORT_STOP_BITS_2((byte) 0x01);

    private byte value;

    SerialPortStopBits(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
