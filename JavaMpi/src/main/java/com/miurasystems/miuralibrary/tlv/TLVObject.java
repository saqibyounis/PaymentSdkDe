/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import android.support.annotation.NonNull;

import java.util.List;

// EMV 4.3, Book 3, Annex B defines the 'Rules for BER-TLV Data Objects'

public class TLVObject {
    public static final String TAG = TLVObject.class.getName();

    private int topTag;
    private final Tag tag;
    private int vLength;
    private int tLength;
    private int lLength;
    private byte[] rawData;
    public List<TLVObject> constructedTLVObject;

    private final boolean isCalcLength;

    /**
     * Constructor
     * @param topTag
     * @param tag
     * @param tLength
     * @param vLength
     * @param lLength
     */

    public TLVObject(int topTag, Tag tag, int tLength, int vLength, int lLength) {

        this.topTag = topTag;
        this.tag = tag;
        this.vLength = vLength;
        this.tLength = tLength;
        this.lLength = lLength;
        this.isCalcLength = false;
    }

    /**
     * Constructor
     * @param tag
     * @param data
     */

    public TLVObject(Tag tag, @NonNull String data) {
        this.tag = tag;
        this.rawData = data.getBytes();
        this.isCalcLength = true;
        this.topTag = this.getTopTag();
        if (this.isConstructed()) {
            this.constructedTLVObject = TLVParser.decode(this.rawData);
        }
    }

    /**
     * Constructor
     * @param tag
     * @param rawData
     */

    public TLVObject(Tag tag, @NonNull byte[] rawData) {
        this.tag = tag;
        this.rawData = rawData;
        this.isCalcLength = true;
        this.topTag = this.getTopTag();
        if (this.isConstructed()) {
            this.constructedTLVObject = TLVParser.decode(this.rawData);
        }
    }

    public TLVObject(Description tag, @NonNull byte[] rawData) {
        this.tag = new Tag(tag.getTag());
        this.rawData = rawData;
        this.isCalcLength = true;
        this.topTag = this.getTopTag();
        if (this.isConstructed()) {
            this.constructedTLVObject = TLVParser.decode(this.rawData);
        }
    }

    public TLVObject(Description tag, List<TLVObject> constructed) {
        this.tag = new Tag(tag.getTag());
        this.isCalcLength = true;
        this.topTag = this.getTopTag();
        this.constructedTLVObject = constructed;
        byte[] rawDataTemp = new byte[this.getConstructedTLVLength()];
        int startPosition = 0;
        for (TLVObject tlv : this.constructedTLVObject) {
            int dataSize = tlv.getRawData().length;
            System.arraycopy(tlv.getRawData(), 0, rawDataTemp, startPosition, dataSize);
            startPosition += dataSize;
        }
        this.rawData = rawDataTemp;
    }

    public int getTopTag() {
        if (isCalcLength) {
            int tagLen = this.gettLength();
            int topTagID = tag.getTagID();

            for (int i = 1; i < tagLen; i++) {
                topTagID = topTagID >> 8;
            }
            return topTagID;
        } else {
            return this.topTag;
        }
    }

    public Tag getTag() {
        return this.tag;
    }

    public int getFullLength() {

        if (isCalcLength) {
            return gettLength() + getlLength() + getvLength();
        } else {
            return this.tLength + this.lLength + this.vLength;
        }
    }

    public int gettLength() {

        if (isCalcLength) {
            int tagID = tag.getTagID();
            int sizeLimit = 0xFF;
            int tagLen = 1;

            for (; tagLen <= 4; tagLen++) {
                if (tagID < sizeLimit) {
                    break;
                }
                sizeLimit = (int) (sizeLimit << 8);
                sizeLimit |= 0xFF;
            }
            return tagLen;
        } else {
            return this.tLength;
        }
    }

    public int getlLength() {

        if (isCalcLength) {
            int lenLen = 1;

            if (getvLength() > 0x7F) {

                int border = 0xFF;

                while (border <= getvLength()) {
                    border = (border << 8) + 0xFF;
                    lenLen++;
                }
                lenLen++;
            }
            return lenLen;
        } else {
            return this.lLength;
        }
    }

    public int getvLength() {

        if (isCalcLength) {
            int length = 0;

            if (isConstructed() == true) {
                length = getConstructedTLVLength();
            } else {
                length = this.rawData.length;
            }
            return length;
        } else {
            return this.vLength;
        }
    }

    public void setData(@NonNull byte[] rawData) {
        this.rawData = rawData;
    }

    @NonNull
    public String getData() {
        StringBuilder sb = new StringBuilder();
        if (new String(rawData).matches("[\\p{Alnum}\\p{Punct}\\p{Space}]*")) {
            return new String(rawData);
        } else {
            for (byte b : rawData) {
                sb.append(String.format("%2s", Integer.toHexString(b & 0xFF)).replace(' ', '0'));
            }
            return sb.toString();
        }
    }

    @NonNull
    public byte[] getRawData() {
        return this.rawData;
    }

    public boolean isConstructed() {
        if (topTag == 0x63 || topTag == 0x48) {
            return false;
        } else if ((topTag & 0x20) == 0x20) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isRawData() {
        if (new String(getRawData()).matches("[\\p{Alnum}\\p{Punct}\\p{Space}]*")) {
            return false;
        } else {
            return true;
        }
    }

    public int getConstructedTLVLength() {

        int len = 0;

        for (TLVObject tlv : constructedTLVObject) {
            len += tlv.gettLength() + tlv.getlLength() + tlv.getvLength();
        }
        return len;
    }


    public List<TLVObject> getConstrustedTLV() {
        return constructedTLVObject;
    }

    public String toString() {
        return toString(0);
    }

    private String toString(int constructLevel) {
        StringBuilder sb = new StringBuilder();

        if (isConstructed()) {
            for (int i = 0; i < constructLevel; i++) {
                sb.append("  ");
            }
            sb.append(" [").append(tag.description.name()).append("] ");
            sb.append("tagLen(").append(tLength).append("),").append("\n");
            sb.append("tagID(").append(Integer.toHexString(tag.getTagID())).append("),").append("\n");
            sb.append("\n");
            sb.append("length(").append(vLength).append("):");
            sb.append("\n");
            for (TLVObject tlv : constructedTLVObject) {
                if (tlv.isConstructed()) {
                    sb.append(tlv.toString(constructLevel + 1));
                } else {
                    for (int i = 0; i <= constructLevel; i++) {
                        sb.append("  ");
                    }
                    sb.append(" [").append(tlv.tag.description.name()).append("] ");
                    sb.append("tagLen(").append(tlv.tLength).append("),").append("\n");
                    sb.append("tagID(").append(Integer.toHexString(tlv.tag.getTagID())).append("),").append("\n");
                    sb.append("length(").append(tlv.vLength).append("),").append("\n");
                    sb.append("data[").append(BinaryUtil.parseHexString(tlv.getRawData())).append("]");
                    if (new String(tlv.getRawData()).matches("[\\p{Alnum}\\p{Punct}\\p{Space}]*")) {
                        sb.append(",text[").append(tlv.getData()).append("]");
                    }
                    sb.append("\n");
                }
            }
        } else {
            sb.append(" [").append(tag.description.name()).append("] ");
            sb.append("tagLen(").append(tLength + "),");
            sb.append("tagID(").append(Integer.toHexString(tag.getTagID()) + "),");
            sb.append("length(").append(vLength + "),");
            sb.append("data[").append(BinaryUtil.parseHexString(getRawData())).append("]");
            if (new String(getRawData()).matches("[\\p{Alnum}\\p{Punct}\\p{Space}]*")) {
                sb.append(",text[").append(getData()).append("]");
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
