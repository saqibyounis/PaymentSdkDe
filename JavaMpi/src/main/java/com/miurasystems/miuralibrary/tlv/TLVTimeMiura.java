/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class TLVTimeMiura {

    public static TLVObject getTLVDateTime(Date dateTime) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd");
        String strDate = dateFormat.format(dateTime);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        String strTime = timeFormat.format(dateTime);

        TLVObject tlvDate = new TLVObject(Description.Date, BinaryUtil.parseHexBinary(strDate));
        TLVObject tlvTime = new TLVObject(Description.Time, BinaryUtil.parseHexBinary(strTime));

        ArrayList<TLVObject> list = new ArrayList<TLVObject>();
        list.add(tlvDate);
        list.add(tlvTime);

        return new TLVObject(Description.Command_Data, list);
    }
}
