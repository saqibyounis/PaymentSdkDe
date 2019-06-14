/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import com.miurasystems.miuralibrary.JavaBeanTester;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class CardStatusTest {
    @Test
    public void toStringEmpty() throws Exception {
        String s = new CardStatus().toString();
        assertThat(s, not(isEmptyOrNullString()));
    }
    @Test
    public void testGetSets() throws Exception {
        JavaBeanTester.test(CardStatus.class);
    }
}
