/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;


public class TagTest {

    @Test
    public void test() {
        class TestDataEntry {
            public final int givenId;
            public final Description expectedDesc;
            public final String expectedString;

            TestDataEntry(int givenId, Description expectedDesc, String expectedString) {
                this.givenId = givenId;
                this.expectedDesc = expectedDesc;
                this.expectedString = expectedString;
            }
        }

        List<TestDataEntry> testData = Arrays.asList(
                new TestDataEntry(0x80, Description.File_Size, "File_Size(0x0080)(80)"),
                new TestDataEntry(0x0A, Description.Payment_Internal_2,
                        "Payment_Internal_2(0x000a)(0a)"),
                new TestDataEntry(0xdfae5A, Description.Masked_PAN, "Masked_PAN(0xdfae5a)(5a)"),
                new TestDataEntry(0x00000000, Description.UNKNOWN, "UNKNOWN(0x0000)(00)"),
                new TestDataEntry(0x12345678, Description.UNKNOWN, "UNKNOWN(0x0000)(78)"),
                new TestDataEntry(0xFFFFFFFF, Description.UNKNOWN, "UNKNOWN(0x0000)(ff)")
        );

        for (TestDataEntry entry : testData) {
            Tag tag = new Tag(entry.givenId);

            assertThat(tag.description, is(equalTo(entry.expectedDesc)));
            assertThat(tag.getTagID(), is(equalTo(entry.givenId)));
            assertThat(tag.toString(), is(equalTo(entry.expectedString)));
        }
    }

    @Test
    public void testHash() {
        HashMap<Tag, String> map = new HashMap<Tag, String>();

        Tag fileSizeTag = new Tag(0x80);
        map.put(fileSizeTag, "fileSize");
        map.put(fileSizeTag, "override");
        map.put(new Tag(0x80), "new fileSize");
        map.put(new Tag(0x9a), "date");

        assertThat(map.get(fileSizeTag), is(equalTo("override")));
        assertThat(map.get(new Tag(0x9a)), is(equalTo(null)));
    }

    @Test
    public void testEquals() {
        assertThat(new Tag(0x9a), is(not(equalTo(new Tag(0x9a)))));

        Tag dateTag = new Tag(0x9a);
        assertThat(dateTag, is(equalTo(dateTag)));
        assertThat(new Tag(0x80), is(not(equalTo(dateTag))));
    }

}
