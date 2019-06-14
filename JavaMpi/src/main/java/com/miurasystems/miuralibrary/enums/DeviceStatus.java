/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

public enum DeviceStatus {

    /**
     * This is alwasy sent when opening a session with the PED.
     */
    DevicePoweredOn((byte) 0x01),

    /**
     * This is sent when the user enters a PIN digit. NOTE: the digit is not sent!
     */
    PinEntryEvent((byte) 0x02),
    ApplicationSelection((byte) 0x03),
    DeviceRebooting((byte) 0x0B),

    /**
     * This is sent immidiatley before the device powers down.
     */
    DevicePoweringOff((byte) 0x0A),
    MPIRestarting((byte) 0x0C),

    /**
     * This is sent during a contactless transaction when the user is asked to enter the PIN on the phone used for the payment
     */
    SeePhone((byte) 0xC0),

    /**
     * Used to indicate the app should emit a success beep during a contactless transaction
     */
    SuccessBeep((byte) 0xCB),

    /**
     * Used to indicate the app should emit an error beep during a contactless transaction
     */
    ErrorBeep((byte) 0xCE);

    private byte value;

    DeviceStatus(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static DeviceStatus getByValue(byte value) {
        for (DeviceStatus field : DeviceStatus.values()) {
            if (field.getValue() == value) {
                return field;
            }
        }
        return null;
    }
}
