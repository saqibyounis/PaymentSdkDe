/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;


import android.support.annotation.IntRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BinaryUtil {
    public static byte[] parseHexBinary(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static String parseBinaryString(int b) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        return sb.toString();
    }

    public static String parseHexString(int b) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%2s", Integer.toHexString(b & 0xFF)).replace(' ', '0'));
        return sb.toString();
    }

    public static String parseBinaryString(byte[] bytes) {

        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
        }
        return sb.toString();
    }

    public static String parseHexString(byte b) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%2s", Integer.toHexString(b & 0xFF)).replace(' ', '0'));
        return sb.toString();
    }

    public static String parseHexString(byte[] bytes) {

        if (bytes == null || bytes.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%2s", Integer.toHexString(b & 0xFF)).replace(' ', '0'));
        }
        return sb.toString();
    }

    /**
     * Asserts that int i is in the range 0-255
     * @param i integer, hopefully in the range  [0, 255]
     */
    public static void assertByteRange(int i) {
        intToUbyte(i);
    }

    /**
     * Convert int to byte, treating each as if they are 'unsigned'.
     *
     * Asserts that int is in the range 0-0xff.
     *
     * Function exists because Java's lack of unsigned bytes makes
     * code like `b > 0x82` annoyingly difficult, as:
     * int -2 = byte 0xfe
     * int 0xfe = byte 0xfe
     *
     * And it will happily convert (byte) 0xfe to (int) -2 when doing comparisons,
     * so this is here to ensure they're in the range we want.
     *
     * @param i integer in the range [0, 255].
     * @return A byte
     */
    public static byte intToUbyte(@IntRange(from = 0, to = 255) int i) {
        byte b = (byte) i;
        if (i < 0 || i > 255) {
            String msg = "Dodgy byte conversion: int 0x%02x -> byte 0x%02x, int is: %d";
            String err = String.format(Locale.ENGLISH, msg, i, b, i);
            throw new AssertionError(err);
        }
        return b;
    }

    /**
     * Convert a byte to an int, treating each as if they are 'unsigned'.
     *
     * Asserts that returned int is in the range 0-0xff.
     *
     * Function exists because Java's lack of unsigned bytes makes
     * code like `b > 0x82` annoyingly difficult, as:
     * int -2 = byte 0xfe
     * int 0xfe = byte 0xfe
     *
     * And it will happily convert (byte) 0xfe to (int) -2 when doing comparisons
     *
     * @param b a byte
     * @return An int in the range [0, 255]
     */
    @IntRange(from = 0, to = 255)
    public static int ubyteToInt(byte b) {
        // & works on ints, so the byte is implicitly sign-extended to an int
        // then put into the range of 0-255. We're doing the sign-ext explicitly here
        // so that it doesn't look like such a no-op.
        int i = ((int) b) & 0xFF;
        assertByteRange(i);
        return i;
    }

    public static byte[] parseHexBinary(Description tag) {

        if (tag == null) {
            return null;
        }

        List<Byte> rawTemp = new ArrayList<Byte>();

        byte hexTemp = 0x00;
        hexTemp = (byte) ((tag.getTag() >> 24) & 0xFF);
        if (hexTemp != 0x00 || rawTemp.size() != 0) {
            rawTemp.add(Byte.valueOf(hexTemp));
        }
        hexTemp = (byte) ((tag.getTag() >> 16) & 0xFF);
        if (hexTemp != 0x00 || rawTemp.size() != 0) {
            rawTemp.add(Byte.valueOf(hexTemp));
        }
        hexTemp = (byte) ((tag.getTag() >> 8) & 0xFF);
        if (hexTemp != 0x00 || rawTemp.size() != 0) {
            rawTemp.add(Byte.valueOf(hexTemp));
        }
        hexTemp = (byte) ((tag.getTag() >> 0) & 0xFF);
        if (hexTemp != 0x00 || rawTemp.size() != 0) {
            rawTemp.add(Byte.valueOf(hexTemp));
        }

        final int rawSize = rawTemp.size();
        byte[] raw = new byte[rawSize];
        for (int i = 0; i < rawSize; i++) {
            raw[i] = rawTemp.get(i);
        }

        return raw;
    }


    public static byte[] parseHexBinary(List<Description> tag) {

        if (tag == null) {
            return null;
        }


        List<List<Byte>> rawTemp = new ArrayList<List<Byte>>();
        int size = 0;

        for (int i = 0; i < tag.size(); i++) {
            List<Byte> tmp = new ArrayList<Byte>();
            byte hexTemp = 0x00;
            hexTemp = (byte) ((tag.get(i).getTag() >> 24) & 0xFF);
            if (hexTemp != 0x00 || tmp.size() != 0) {
                tmp.add(Byte.valueOf(hexTemp));
            }
            hexTemp = (byte) ((tag.get(i).getTag() >> 16) & 0xFF);
            if (hexTemp != 0x00 || tmp.size() != 0) {
                tmp.add(Byte.valueOf(hexTemp));
            }
            hexTemp = (byte) ((tag.get(i).getTag() >> 8) & 0xFF);
            if (hexTemp != 0x00 || tmp.size() != 0) {
                tmp.add(Byte.valueOf(hexTemp));
            }
            hexTemp = (byte) ((tag.get(i).getTag() >> 0) & 0xFF);
            if (hexTemp != 0x00 || tmp.size() != 0) {
                tmp.add(Byte.valueOf(hexTemp));
            }
            size += tmp.size();
            rawTemp.add(tmp);
        }

        byte[] raw = new byte[size];
        int i = 0;
        for (List<Byte> bytes : rawTemp) {
            for (Byte bTmp : bytes) {
                raw[i] = bTmp;
                i++;
            }
        }

        return raw;
    }


    /**
     * Convert `value` into a zero-padded, right-aligned, binary-coded-decimal (BCD).
     *
     * Corresponds to the 'Numeric data' format in EMV 4.3 Book 3, section 4.3
     *
     * Example: Amount_Authorised_Numeric(0x9f02) is defined as “n 12” with a
     *  length of six bytes. A value of 12345 is stored in Amount, Authorised
     *  (Numeric) as Hex '00 00 00 01 23 45'.
     *
     * Which would equate to `getBCD(12345, 6);`.
     *
     * @param value     Value to convert into BCD. Must be >= 0.
     * @param byteCount Size of the returned array.
     * @return Returns a byte[] of length byteCount, containing the big-endian BCD number.
     */
    public static byte[] getBCD(int value, int byteCount) {

        if (value < 0) {
            throw new IllegalArgumentException("value can't be negative");
        }
        int numDecDigits = (int) Math.floor(Math.log10(value) + 1.0d);
        int minNumBytes = (numDecDigits + 1) / 2;
        if (minNumBytes > byteCount) {
            String msg = "byteCount (%d) not large enough. Minimum for (%d) is (%d)";
            throw new IllegalArgumentException(
                    String.format(Locale.ENGLISH, msg, byteCount, value, minNumBytes));
        }

        // reverse loop as we want it to be big-endian
        byte[] output = new byte[byteCount];
        for (int i = byteCount - 1; i > -1; i--) {
            int a = value % 10;
            value /= 10;
            int b = value % 10;
            value /= 10;

            int bcd = (b << 4) | a;
            output[i] = (byte) bcd;
        }
        return output;
    }
}
