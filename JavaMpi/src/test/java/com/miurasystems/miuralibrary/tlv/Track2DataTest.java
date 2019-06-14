/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import com.miurasystems.miuralibrary.JavaBeanTester;

import org.junit.Test;

import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class Track2DataTest {

    @Test
    public void toStringNew() throws Exception {
        // ensure a fresh object's toString works without exploding.
        String s = new Track2Data().toString();
        assertThat(s, not(isEmptyOrNullString()));
    }

    @Test
    public void testGetSets() throws Exception {
        JavaBeanTester.test(Track2Data.class);
    }
}
