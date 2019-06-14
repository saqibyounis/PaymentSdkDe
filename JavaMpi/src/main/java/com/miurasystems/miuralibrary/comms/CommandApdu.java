/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import static com.miurasystems.miuralibrary.tlv.BinaryUtil.intToUbyte;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.miurasystems.miuralibrary.CommandType;

import java.util.Locale;

public class CommandApdu {

    private static final int HEADER_SIZE = 4;

    private static final int LE_SIZE = 1;
    private static final int LC_SIZE = 1;
    private static final int MAX_BODY_SIZE = (MpiPacket.MAX_APDU_SIZE - HEADER_SIZE);
    private static final int MAX_DATA_SIZE = (MAX_BODY_SIZE - LC_SIZE);

    @NonNull
    @Size(min = MpiPacket.MIN_APDU_SIZE, max = MpiPacket.MAX_APDU_SIZE)
    private final byte[] mBytes;

    public CommandApdu(@NonNull CommandType type) {
        this(type.Cla, type.Ins, 0, 0, null, null);
    }

    public CommandApdu(
            @NonNull CommandType type,
            @Nullable @Size(min = 1, max = MAX_DATA_SIZE) byte[] dataField) {
        this(type.Cla, type.Ins, 0, 0, dataField, null);
    }

    public CommandApdu(
            @NonNull CommandType type,
            @IntRange(from = 0, to = 255) int p1,
            @IntRange(from = 0, to = 255) int p2) {
        this(type.Cla, type.Ins, p1, p2, null, null);
    }

    public CommandApdu(
            @NonNull CommandType type,
            @IntRange(from = 0, to = 255) int p1,
            @IntRange(from = 0, to = 255) int p2,
            @Nullable @Size(min = 1, max = MAX_DATA_SIZE) byte[] dataField) {
        this(type.Cla, type.Ins, p1, p2, dataField, null);
    }

    public CommandApdu(
            @NonNull CommandType type,
            @IntRange(from = 0, to = 255) int p1,
            @IntRange(from = 0, to = 255) int p2,
            @Nullable @Size(min = 1, max = MAX_DATA_SIZE) byte[] dataField,
            @Nullable @IntRange(from = 0, to = 255) Integer le) {
        this(type.Cla, type.Ins, p1, p2, dataField, le);
    }

    public CommandApdu(
            @IntRange(from = 0, to = 255) int cla,
            @IntRange(from = 0, to = 255) int ins,
            @IntRange(from = 0, to = 255) int p1,
            @IntRange(from = 0, to = 255) int p2) {
        this(cla, ins, p1, p2, null, null);
    }

    public CommandApdu(
            @IntRange(from = 0, to = 255) int cla,
            @IntRange(from = 0, to = 255) int ins,
            @IntRange(from = 0, to = 255) int p1,
            @IntRange(from = 0, to = 255) int p2,
            @Nullable @Size(min = 1, max = MAX_DATA_SIZE) byte[] dataField) {
        this(cla, ins, p1, p2, dataField, null);
    }

    public CommandApdu(
            @IntRange(from = 0, to = 255) int cla,
            @IntRange(from = 0, to = 255) int ins,
            @IntRange(from = 0, to = 255) int p1,
            @IntRange(from = 0, to = 255) int p2,
            @Nullable @Size(min = 1, max = MAX_DATA_SIZE) byte[] dataField,
            @Nullable @IntRange(from = 0, to = 255) Integer le) {

        int apduSize = HEADER_SIZE;
        if (dataField != null) {
            if (dataField.length < 1 || dataField.length > MAX_DATA_SIZE) {
                throw new IllegalArgumentException(
                        String.format(Locale.ENGLISH,
                                "Invalid data field size: %d, min: 1, max: %d",
                                dataField.length, MAX_DATA_SIZE));
            }

            // +1 for the Lc byte
            apduSize += LC_SIZE + dataField.length;
        }

        //noinspection VariableNotUsedInsideIf
        if (le != null) {
            // +1 for the Le byte
            apduSize += LE_SIZE;
        }

        if (apduSize > MpiPacket.MAX_APDU_SIZE) {
            throw new IllegalArgumentException(
                    String.format(Locale.ENGLISH,
                            "Invalid APDU size: %d", apduSize));
        }

        mBytes = new byte[apduSize];
        mBytes[0] = intToUbyte(cla);
        mBytes[1] = intToUbyte(ins);
        mBytes[2] = intToUbyte(p1);
        mBytes[3] = intToUbyte(p2);

        if (apduSize != HEADER_SIZE) {
            int writeOffset = 4;
            if (dataField != null) {
                mBytes[writeOffset++] = intToUbyte(dataField.length);

                System.arraycopy(dataField, 0, mBytes, writeOffset, dataField.length);
                writeOffset += dataField.length;
            }

            if (le != null) {
                //noinspection UnusedAssignment
                mBytes[writeOffset++] = intToUbyte(le);
            }
        }
    }

    @NonNull
    @Size(min = MpiPacket.MIN_APDU_SIZE, max = MpiPacket.MAX_APDU_SIZE)
    public byte[] getBytes() {
        return mBytes;
    }


}
