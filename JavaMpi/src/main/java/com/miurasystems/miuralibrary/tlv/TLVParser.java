/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import java.util.ArrayList;
import java.util.List;

public class TLVParser {

    //region decode

    /**
     * It decodes the TLV message.
     *
     * @param bytes
     * @return TLVObject
     */
    public static final String TAG = TLVParser.class.getName();


    /**
     * Decoding responseMessage.getBody() from Test Driver.
     */
    public static List<TLVObject> decode(byte[] bytes) {

        /**
         * List of TLVObject Creation tlvs
         */
        List<TLVObject> tlvs = new ArrayList<TLVObject>();

        if (bytes == null) {
            return tlvs;
        }

        int i = 0;
        while (i < bytes.length) {

            // topTag(1byte)
            int tagID = BinaryUtil.ubyteToInt(bytes[i]);
            int topTagID = tagID;
            int tagLen = 1;

            if ((tagID & 0x1F) == 0x1F) {
                while ((i + tagLen) < bytes.length) {
                    tagID = (tagID << 8) + BinaryUtil.ubyteToInt(bytes[i + tagLen]);
                    if ((bytes[i + tagLen] & 0x80) != 0x80) {
                        tagLen++;
                        break;
                    }
                    tagLen++;
                }
            }

            if (topTagID == 0x0F) {
                break;
            }

            Tag tag = new Tag(tagID);
            i += tagLen;
            // Acquisition of Length (2 byte)
            // Length is stored until 0x7f of the relevant bytes.
            // If 0x80 is standing, the first byte is the Length length
            int length = 0;
            int lenLen = 1;

            if ((bytes[i] & 0x80) == 0x80) {

                int byteLength = (bytes[i] & 0x7F) & 0xFF;

                for (int shift = 1; shift <= byteLength; shift++) {
                    length = (length << 8) + BinaryUtil.ubyteToInt(bytes[i + shift]);
                    lenLen++;
                }
            } else {
                length = (bytes[i] & 0x7F) & 0xFF;
            }

            i += lenLen;

            TLVObject tlv = new TLVObject(topTagID, tag, tagLen, length, lenLen);

            // Acquisition of data
            // Discrimination of structured data
            if (tlv.isConstructed()) {
                // In the case of structured data, loop decoding work
                byte[] rawData = new byte[tlv.getvLength()];
                System.arraycopy(bytes, i, rawData, 0, tlv.getvLength());
                tlv.setData(rawData);
                tlv.constructedTLVObject = decode(bytes, i, tlv.getvLength());
                i += tlv.getConstructedTLVLength();
            } else {
                // In the case of unstructured data, the end by setting the data
                byte[] data = new byte[tlv.getvLength()];
                System.arraycopy(bytes, i, data, 0, tlv.getvLength());
                tlv.setData(data);
                i += tlv.getvLength();
            }
            tlvs.add(tlv);
        }

        return tlvs;
    }

    private static List<TLVObject> decode(byte[] bytes, int offset, int size) {
        int dataSize = size;

        if (bytes.length - offset < size) {
            dataSize = bytes.length - offset;
        }

        byte[] newBytes = new byte[dataSize];
        System.arraycopy(bytes, offset, newBytes, 0, newBytes.length);
        List<TLVObject> tlvs = decode(newBytes);
        return tlvs;

    }

    //endregion

    //region encode

    public static byte[] encode(TLVObject tlv) {

        if (tlv == null) {
            return null;
        }

        if (tlv.isConstructed()) {
            if (tlv.getRawData() != null && tlv.getRawData().length != 0 &&
                    (tlv.getConstrustedTLV() == null || tlv.getConstrustedTLV().size() == 0)) {
                return TLVParser.encode(tlv.getTag().description, tlv.getRawData());
            }
            return TLVParser.encode(tlv.getTag().description,
                    TLVParser.encode(tlv.getConstrustedTLV()));
        } else {
            return TLVParser.encode(tlv.getTag().description, tlv.getRawData());
        }
    }

    private static byte[] encode(List<TLVObject> tlvs) {

        List<Byte> rawTemp = new ArrayList<Byte>();

        for (TLVObject tlv : tlvs) {
            byte[] currentRaw = null;
            if (tlv.isConstructed()) {
                if (tlv.getRawData() != null && tlv.getRawData().length != 0 &&
                        (tlv.getConstrustedTLV() == null || tlv.getConstrustedTLV().size() == 0)) {
                    currentRaw = TLVParser.encode(tlv.getTag().description, tlv.getRawData());
                } else {
                    currentRaw = TLVParser.encode(tlv.getTag().description,
                            TLVParser.encode(tlv.getConstrustedTLV()));
                }
            } else {
                currentRaw = TLVParser.encode(tlv.getTag().description, tlv.getRawData());
            }

            if (currentRaw == null) {
                continue;
            }

            final int currentRawSize = currentRaw.length;
            for (int i = 0; i < currentRawSize; i++) {
                rawTemp.add(Byte.valueOf(currentRaw[i]));
            }
        }

        final int rawSize = rawTemp.size();
        byte[] raw = new byte[rawSize];
        for (int i = 0; i < rawSize; i++) {
            raw[i] = rawTemp.get(i);
        }

        return raw;
    }

    public static byte[] encode(Description tag, byte[] value) {

        if (tag == null || value == null) {
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

        byte lenByte = 0x00;

        final int valueLength = value.length;
        int lenBytes = (int) Math.floor(valueLength / 128);
        if (lenBytes > 0) {
            lenByte = (byte) (0x80 + lenBytes);
            rawTemp.add(Byte.valueOf(lenByte));

            for (int i = lenBytes; i > 0; i--) {
                lenByte = (byte) (valueLength >> (8 * (i - 1)) & 0xFF);
                rawTemp.add(Byte.valueOf(lenByte));
            }
        } else {
            lenByte = (byte) (valueLength & 0xFF);
            rawTemp.add(Byte.valueOf(lenByte));
        }

        final int rawSize = rawTemp.size();
        byte[] raw = new byte[rawSize + value.length];
        for (int i = 0; i < rawSize; i++) {
            raw[i] = rawTemp.get(i);
        }
        System.arraycopy(value, 0, raw, rawSize, value.length);

        return raw;
    }

    //endregion
}
