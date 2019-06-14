/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.Test;

public class InterfaceTypeTest {
    @Test
    public void valueOf() {
        assertThat(InterfaceType.valueOf((byte) 0x1), is(equalTo(InterfaceType.MPI)));
        assertThat(InterfaceType.valueOf((byte) 0x2), is(equalTo(InterfaceType.RPI)));
        assertThat(InterfaceType.valueOf((byte) 0x3), is(nullValue()));
    }


}
