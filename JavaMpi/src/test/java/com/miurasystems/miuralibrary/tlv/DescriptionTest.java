/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import com.miurasystems.miuralibrary.Pair;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@SuppressWarnings("unchecked")
public class DescriptionTest {

    @Test
    public void valueOf() {
        List<Pair<Integer, Description>> data = Arrays.asList(
            new Pair<Integer, Description>(0x9a, Description.Date),
            new Pair<Integer, Description>(0x9f21, Description.Time),
            new Pair<Integer, Description>(0xdfa20a, Description.Battery_Status),
            new Pair<Integer, Description>(0x9c00, Description.Transaction_Information_Status_sale),

            new Pair<Integer, Description>(0xffffffff, Description.UNKNOWN),
            new Pair<Integer, Description>(0x0, Description.UNKNOWN),

            // this is a made up value
            new Pair<Integer, Description>(0x12345678, Description.UNKNOWN)
        );
        for (Pair<Integer, Description> pair : data) {
            Description desc = Description.valueOf(pair.first);
            assertThat(desc, is(equalTo(pair.second)));
        }
    }

    @Test
    public void getTag() {
        List<Pair<Description, Integer>> data = Arrays.asList(
            new Pair<Description, Integer>(Description.AID, 0x4f),
            new Pair<Description, Integer>(Description.Terminal_Action_Code_DEFAULT, 0xff0d),
            new Pair<Description, Integer>(Description.PIN_Digit_Status, 0xdfa201),

            //new Pair<Description, Integer>(Description.UNKNOWN, 0xffffffff),
            new Pair<Description, Integer>(Description.UNKNOWN, 0x0)
        );
        for (Pair<Description, Integer> pair : data) {
            int tag = pair.first.getTag();
            assertThat(tag, is(equalTo(pair.second)));
        }
    }

    @Test
    public void test_toString() {
        List<Pair<Description, String>> data = Arrays.asList(
            new Pair<Description, String>(Description.AID, "AID(0x004f)"),
            new Pair<Description, String>(Description.Terminal_Action_Code_DEFAULT,
                "Terminal_Action_Code_DEFAULT(0xff0d)"),
            new Pair<Description, String>(Description.PIN_Digit_Status,
                "PIN_Digit_Status(0xdfa201)"),

            //new Pair<Description, String>(Description.UNKNOWN, "UNKNOWN(0xffffffff)"),
            new Pair<Description, String>(Description.UNKNOWN, "UNKNOWN(0x0000)")
        );
        for (Pair<Description, String> pair : data) {
            String s = pair.first.toString();
            assertThat(s, is(equalTo(pair.second)));
        }
    }

}
