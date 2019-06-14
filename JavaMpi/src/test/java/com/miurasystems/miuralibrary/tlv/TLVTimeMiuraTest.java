/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;


import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TLVTimeMiuraTest {

    @Test
    public void nullPointer() {
        try {
            TLVTimeMiura.getTLVDateTime(null);
            Assert.fail();
        } catch (NullPointerException ignore) {

        }
    }

    @Test
    public void now() {
        /* Just make sure now's date works without exploding */

        Date date = new Date();
        TLVObject e0 = TLVTimeMiura.getTLVDateTime(date);

        List<TLVObject> e0SubTags = e0.getConstrustedTLV();
        TLVObject dateTlv = e0SubTags.get(0);
        TLVObject timeTlv = e0SubTags.get(1);

        assertThat(e0.getTag().description, is(equalTo(Description.Command_Data)));
        assertThat(dateTlv.getTag().description, is(equalTo(Description.Date)));
        assertThat(timeTlv.getTag().description, is(equalTo(Description.Time)));
    }

    @Test
    public void testSpecificDateAndTime() {
        // setup
        // Months are indexed from 0 in java Calendars so use the constant
        Calendar calendar = new GregorianCalendar(1985, Calendar.OCTOBER, 26, 1, 21, 22);
        Date date = calendar.getTime();

        // execute
        TLVObject e0 = TLVTimeMiura.getTLVDateTime(date);

        //verify
        List<TLVObject> e0SubTags = e0.getConstrustedTLV();
        TLVObject dateTlv = e0SubTags.get(0);
        TLVObject timeTlv = e0SubTags.get(1);

        assertThat(e0.getTag().description, is(equalTo(Description.Command_Data)));

        assertThat(dateTlv.getTag().description, is(equalTo(Description.Date)));
        assertThat(dateTlv.getData(), is(equalTo("851026")));

        assertThat(timeTlv.getTag().description, is(equalTo(Description.Time)));
        assertThat(timeTlv.getData(), is(equalTo("012122")));
    }

}
