/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

import android.support.annotation.Nullable;

import java.util.HashMap;

/**
 * Interface type which is an NAD byte in Prologue
 */
public enum InterfaceType {

    /**
     * Miura Payments Interface
     */
    MPI((byte) 0x01),
    /**
     * Miura Retail POS Interface
     */
    RPI((byte) 0x02);

    private static HashMap<Byte, InterfaceType> map = new HashMap<>();

    static {
        for (InterfaceType e : InterfaceType.values()) {
            map.put(e.interfaceByteCode, e);
        }
    }

    byte interfaceByteCode;

    InterfaceType(byte interfaceByteCode) {
        this.interfaceByteCode = interfaceByteCode;
    }

    /**
     * @return byte represent of interface
     */
    public byte getInterfaceType() {
        return interfaceByteCode;
    }

    /**
     * Gets the enum with value `interfaceByteCode`, or null if none exists.
     *
     * @param interfaceByteCode value to lookup
     * @return Enum, or null if no match found
     */
    @Nullable
    public static InterfaceType valueOf(byte interfaceByteCode) {
        return map.get(interfaceByteCode);
    }
}
