/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.tlv.BinaryUtil;

public enum RKIError {
    NoError(0),
    RkiDataMissing(0xE0),
    RkiHSMCertFailure(0xE1),
    RkiErrorWithRSAKey(0xE2),
    RkiErrorWithTransportKey(0xE3),
    RkiErrorWithDUKPTKey(0xE4),
    RkiErrorWithIKSN(0xE5),
    RkiMiuraInternalError(0xE6);

    private final int mValue;

    RKIError(int value) {
        mValue = value;
    }

    @NonNull
    public static RKIError valueOf(byte value) {
        int iValue = BinaryUtil.ubyteToInt(value);
        for (RKIError field : RKIError.values()) {
            if (field.mValue == iValue) {
                return field;
            }
        }
        return RKIError.RkiMiuraInternalError;
    }

}
