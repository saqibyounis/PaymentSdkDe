/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * Cash Drawer state.
 */
public enum CashDrawer {

    Closed((byte) 0x00),
    Opened((byte) 0x01);

    private byte value;

    CashDrawer(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static CashDrawer getByValue(byte value) {
        for (CashDrawer field : CashDrawer.values()) {
            if (field.getValue() == value) {
                return field;
            }
        }
        return null;
    }

}
