/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

public class Tag {
    public final Description description;

    private int unknownTagID;

    public Tag(int id) {

        this.description = Description.valueOf(id);

        if (this.description == Description.UNKNOWN) {
            unknownTagID = id;
        } else {
            unknownTagID = 0;
        }
    }

    /**
     * Though Tag is used for keys of HashMap, you MUST NOT implement hashCode() by the value of Description.
     * Two or more tags that has the same description can appear in a constructed TLV data (like a result of ResetDevice).
     */
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Though Tag is used for keys of HashMap, you MUST NOT implement hashCode() by the value of Description.
     * Two or more tags that has the same discription can appear in a constructed TLV data (like a result of ResetDevice).
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public int getTagID() {
        if (this.description == Description.UNKNOWN) {
            return unknownTagID;
        } else {
            return description.getTag();
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.description.toString());
        sb.append("(");
        if (this.description == Description.UNKNOWN) {
            sb.append(BinaryUtil.parseHexString(this.unknownTagID));
        } else {
            sb.append(BinaryUtil.parseHexString(this.description.getTag()));
        }
        sb.append(")");

        return sb.toString();
    }
}
