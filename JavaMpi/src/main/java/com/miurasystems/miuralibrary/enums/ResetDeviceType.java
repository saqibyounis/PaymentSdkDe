/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * Enum describing the options for resetting the Miura device.
 */
public enum ResetDeviceType {
    Soft_Reset(0x00),
    Hard_Reset(0x01),
    Clear_Files(0x02),
    Clear_Files_And_Reinitialise_MSD(0x03);

    private final int mType;

    private ResetDeviceType(int type) {
        mType = type;
    }

    public int getType() {
        return mType;
    }
}
