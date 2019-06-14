/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

public enum BacklightSettings {
    Disable(0x00),
    Enable(0x01),
    NoChange(0xff);

    private final int mBacklight;

    BacklightSettings(int backlight) {
        this.mBacklight = backlight;
    }

    public int getValue() {
        return mBacklight;
    }
}
