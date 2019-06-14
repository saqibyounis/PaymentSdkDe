/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

public enum StatusSettings {
    Disable(0x00),
    Enable(0x01),
    No_Change(0xff);

    private final int mStatus;

    StatusSettings(final int status) {
        this.mStatus = status;
    }

    public int getValue() {
        return mStatus;
    }
}
