/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.utils;

import com.miurasystems.miuralibrary.enums.SerialPortBaudRate;
import com.miurasystems.miuralibrary.enums.SerialCharacterSize;
import com.miurasystems.miuralibrary.enums.SerialPortStopBits;

/**
 * Class used to configure a serial port.
 * Added to support a USB to serial converter plugged into a POS device.
 */

public class SerialPortProperties {
    private SerialPortBaudRate baudRate;
    private SerialCharacterSize charSize;
    private boolean parityEnabled;
    private boolean oddParity;
    private SerialPortStopBits stopBits;
    private boolean hwFlowControlEnabled;
    private boolean swFlowControlEnabled;

    public SerialPortProperties() {
        /*Sensible defaults*/
        baudRate = SerialPortBaudRate.SERIAL_RATE_115200;
        charSize = SerialCharacterSize.SERIAL_CHARACTER_SIZE_8;
        parityEnabled = false;
        oddParity = false;
        stopBits = SerialPortStopBits.SERIAL_PORT_STOP_BITS_1;
        hwFlowControlEnabled = false;
        swFlowControlEnabled = false;
    }

    public SerialPortProperties(SerialPortBaudRate baudRate, SerialCharacterSize charSize, boolean parityEnabled, boolean oddParity, SerialPortStopBits stopBits, boolean hwFlowControlEnabled, boolean swFlowControlEnabled) {
        this.baudRate = baudRate;
        this.charSize = charSize;
        this.parityEnabled = parityEnabled;
        this.oddParity = oddParity;
        this.stopBits = stopBits;
        this.hwFlowControlEnabled = hwFlowControlEnabled;
        this.swFlowControlEnabled = swFlowControlEnabled;
    }

    public void setBaudRate(SerialPortBaudRate baudRate) {
        this.baudRate = baudRate;
    }

    public void setCharSize(SerialCharacterSize charSize) {
        this.charSize = charSize;
    }

    public void setParityEnabled(boolean parityEnabled) {
        this.parityEnabled = parityEnabled;
    }

    public void setOddParity(boolean oddParity) {
        this.oddParity = oddParity;
    }

    public void setStopBits(SerialPortStopBits stopBits) {
        this.stopBits = stopBits;
    }

    public void setHwFlowControlEnabled(boolean hwFlowControlEnabled) {
        this.hwFlowControlEnabled = hwFlowControlEnabled;
    }

    public void setSwFlowControlEnabled(boolean swFlowControlEnabled) {
        this.swFlowControlEnabled = swFlowControlEnabled;
    }

    public SerialPortBaudRate getBaudRate() {
        return baudRate;
    }

    public SerialCharacterSize getCharSize() {
        return charSize;
    }

    public boolean isParityEnabled() {
        return parityEnabled;
    }

    public boolean isOddParity() {
        return oddParity;
    }

    public SerialPortStopBits getStopBits() {
        return stopBits;
    }

    public boolean isHwFlowControlEnabled() {
        return hwFlowControlEnabled;
    }

    public boolean isSwFlowControlEnabled() {
        return swFlowControlEnabled;
    }
}
