/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

public enum SelectFileMode {
    Append(0x00),
    Truncate(0x01),
    AppendWithMD5Response(0x80);

    private final int mMode;

    SelectFileMode(final int mode) {
        this.mMode = mode;
    }

    public int getValue() {
        return mMode;
    }
}
