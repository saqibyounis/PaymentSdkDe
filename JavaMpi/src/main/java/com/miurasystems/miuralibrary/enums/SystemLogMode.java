/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

public enum SystemLogMode {
    Archive(0x00),
    Remove(0x01);

    private final int mMode;

    SystemLogMode(final int mode) {
        this.mMode = mode;
    }

    public int getValue() {
        return mMode;
    }
}
