/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * ENUM used to specify the Baud rate for the serial port settings.
 */

public enum SerialPortBaudRate {
    SERIAL_RATE_0((byte) 0x00),
    SERIAL_RATE_50((byte) 0x01),
    SERIAL_RATE_75((byte) 0x02),
    SERIAL_RATE_110((byte) 0x03),
    SERIAL_RATE_134((byte) 0x04),
    SERIAL_RATE_150((byte) 0x05),
    SERIAL_RATE_200((byte) 0x06),
    SERIAL_RATE_300((byte) 0x07),
    SERIAL_RATE_600((byte) 0x08),
    SERIAL_RATE_1200((byte) 0x09),
    SERIAL_RATE_1800((byte) 0x0A),
    SERIAL_RATE_2400((byte) 0x0B),
    SERIAL_RATE_4800((byte) 0x0C),
    SERIAL_RATE_9600((byte) 0x0D),
    SERIAL_RATE_19200((byte) 0x0E),
    SERIAL_RATE_38400((byte) 0x0F),
    SERIAL_RATE_EXTA((byte) 0x10),
    SERIAL_RATE_EXTB((byte) 0x11),
    SERIAL_RATE_56700((byte) 0x12),
    SERIAL_RATE_115200((byte) 0x13),
    SERIAL_RATE_230400((byte) 0x14),
    SERIAL_RATE_460800((byte) 0x15),
    SERIAL_RATE_500000((byte) 0x16),
    SERIAL_RATE_567000((byte) 0x17),
    SERIAL_RATE_921600((byte) 0x18),
    SERIAL_RATE_1000000((byte) 0x19),
    SERIAL_RATE_1152000((byte) 0x1A),
    SERIAL_RATE_1500000((byte) 0x1B),
    SERIAL_RATE_2500000((byte) 0x1C),
    SERIAL_RATE_3000000((byte) 0x1D),
    SERIAL_RATE_3500000((byte) 0x1E);

    private byte value;

    SerialPortBaudRate(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

}
