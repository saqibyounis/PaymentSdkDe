/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;

import com.miurasystems.miuralibrary.tlv.Description;
import com.miurasystems.miuralibrary.tlv.TLVObject;

import java.util.List;

public class CommandUtil {

    /**
     * Add the LRC into a byte array
     *
     * @param test
     * @return
     */
    public static byte[] addLRC(byte[] test) {
        byte LRC = calculateLRC(test);
        byte[] data = new byte[test.length + 1];
        for (int i = 0; i < test.length; i++) {
            data[i] = test[i];
        }
        data[test.length] = LRC;
        return data;
    }

    /**
     * Calculation of LRC
     *
     * @param data
     * @return
     */
    private static byte calculateLRC(byte[] data) {
        int checksum = 0;
        for (int i = 0; i < data.length; i++) {
            checksum = (checksum ^ data[i]) & 0xFF;
        }
        return (byte) checksum;
    }

    /**
     * To serialize the tags and values
     *
     * @param tag
     * @param value
     * @return
     */
    public static byte[] serialise(byte[] tag, byte[] value) {
        // Configure Tag
        byte[] result = tag.clone();

        // Calculate Length
        int lenBytes = 0;
        if (value.length >= 128) {
            lenBytes++;
        }
        if (value.length >= 256) {
            lenBytes++;
        }

        if (lenBytes > 0) {
            result = copyArray(result, new byte[]{(byte) (0x80 + lenBytes)});
            for (int i = lenBytes; i > 0; i--) {
                result = copyArray(result, new byte[]{(byte) (value.length >> (8 * (i - 1)) & 0xff)});
            }
        } else {
            result = copyArray(result, new byte[]{(byte) value.length});
        }

        result = copyArray(result, value);

        return result;
    }

    /**
     * Copy array
     *
     * @param array1
     * @param array2
     * @return
     */
    public static byte[] copyArray(byte[] array1, byte[] array2) {
        byte[] result = new byte[array1.length + array2.length];

        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);

        return result;
    }

    /**
     * Extraction from the beginning of the array
     *
     * @param array
     * @param length
     * @return
     */
    public static byte[] cutArray(byte[] array, int length) {
        byte[] result = new byte[length];
        System.arraycopy(array, 0, result, 0, length);
        return result;
    }

    /**
     * Extraction of the array
     *
     * @param array
     * @param offset
     * @param length
     * @return
     */
    public static byte[] cutArray(byte[] array, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(array, offset, result, 0, length);
        return result;
    }


    // ***************************************************************************
    // search tag
    // ***************************************************************************

    /**
     * first Match
     *
     * @return
     */
    public static TLVObject firstMatch(List<TLVObject> tlvs, Description tag) {

        for (TLVObject tlv : tlvs) {
            if (tlv.getTag().description == tag) {
                return tlv;
            }
        }

        for (TLVObject tlv : tlvs) {
            if (tlv.isConstructed()) {
                return firstMatch(tlv.constructedTLVObject, tag);
            }
        }
        return null;
    }

    private static int mSumTagFound = 0;

    /**
     * Search tag value
     *
     * @param tlvs
     * @param tag
     * @param count
     * @return
     */
    public static TLVObject searchTagValue(List<TLVObject> tlvs, Description tag, int count) {
        mSumTagFound = 0;
        return searchValue(tlvs, tag, count);
    }

    /**
     * Search value
     *
     * @param tlvs
     * @param tag
     * @param count
     * @return
     */
    private static TLVObject searchValue(List<TLVObject> tlvs, Description tag, int count) {

        for (TLVObject tlv : tlvs) {
            if (tlv.getTag().description == tag) {

                mSumTagFound++;

                if (mSumTagFound == count) {
                    return tlv;
                }
            }
        }


        for (TLVObject tlv : tlvs) {
            if (tlv.isConstructed()) {
                TLVObject data = searchValue(tlv.constructedTLVObject, tag, count);
                if (data != null) return data;
            }
        }
        return null;
    }
}