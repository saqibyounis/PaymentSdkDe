/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Assert;
import org.junit.Test;

@SuppressWarnings({"ErrorNotRethrown", "Range"})
public class CommandApduTest {

    @Test
    public void noDataField() {
        // setup

        // execute
        CommandApdu apdu = new CommandApdu(0xd0, 0x0, 0x1, 0x0);
        byte[] actual = apdu.getBytes();

        // verify
        assertThat(actual, is(equalTo(new byte[]{
                (byte) 0xd0, 0x0, 0x1, 0x0
        })));
    }

    @Test
    public void dataField() throws Exception {
        // setup
        byte[] data = "Hello world!".getBytes("US-ASCII");

        // execute
        CommandApdu apdu = new CommandApdu(0xd2, 0x1, 0x0, 0x1, data);
        byte[] actual = apdu.getBytes();

        // verify
        assertThat(actual, is(equalTo(new byte[]{
                (byte) 0xd2, 0x1, 0x0, 0x1, 0x0c,
                0x48, 0x65, 0x6c, 0x6c, 0x6f, 0x20,
                0x77, 0x6f, 0x72, 0x6c, 0x64, 0x21,
        })));
    }

    @Test
    public void leByte() throws Exception {
        // setup

        // execute
        CommandApdu apdu = new CommandApdu(0x00, 0xb0, 0x1, 0x23, null, 0x12);
        byte[] actual = apdu.getBytes();

        // verify
        assertThat(actual, is(equalTo(new byte[]{
                0x00, (byte) 0xb0, 0x01, 0x23, 0x12,
        })));
    }

    @Test
    public void dataFieldLeByte() throws Exception {
        // setup
        byte[] dataField = {0x0c};

        // execute
        CommandApdu apdu = new CommandApdu(0x00, 0xb0, 0x8F, 0xFF, dataField, 0x12);
        byte[] actual = apdu.getBytes();

        // verify
        assertThat(actual, is(equalTo(new byte[]{
                0x00, (byte) 0xb0, (byte) 0x8f, (byte) 0xff, 0x1, 0x0c, 0x12,
        })));
    }

    @Test
    public void max_dataField() throws Exception {
        // setup
        byte[] dataField = new byte[249];
        dataField[48] = 0x1;
        dataField[148] = 0x2;
        dataField[248] = 0x4;

        // execute
        CommandApdu apdu = new CommandApdu(0x00, 0xb0, 0x8F, 0xFF, dataField);
        byte[] actual = apdu.getBytes();

        // verify
        assertThat(actual.length, is(equalTo(254)));
        assertThat(actual[48 + 5], is(equalTo((byte) 0x1)));
        assertThat(actual[148 + 5], is(equalTo((byte) 0x2)));
        assertThat(actual[248 + 5], is(equalTo((byte) 0x4)));
    }

    @Test
    public void max_dataFieldLeByte() throws Exception {
        // setup
        byte[] dataField = new byte[248];
        dataField[47] = 0x1;
        dataField[147] = 0x2;
        dataField[247] = 0x4;


        // execute
        CommandApdu apdu = new CommandApdu(0x00, 0xb0, 0x8F, 0xFF, dataField, 0x12);
        byte[] actual = apdu.getBytes();

        // verify
        assertThat(actual.length, is(equalTo(254)));
        assertThat(actual[47 + 5], is(equalTo((byte) 0x1)));
        assertThat(actual[147 + 5], is(equalTo((byte) 0x2)));
        assertThat(actual[247 + 5], is(equalTo((byte) 0x4)));
        assertThat(actual[253], is(equalTo((byte) 0x12)));
    }


    @Test
    public void badCla() {
        try {
            CommandApdu apdu = new CommandApdu(0x100, 0x0, 0x0, 0x0);
            Assert.fail();
            assert apdu != null;
        } catch (AssertionError ignore) {
        }
    }

    @Test
    public void badIns() {
        try {
            CommandApdu apdu = new CommandApdu(0x0, 0x100, 0x0, 0x0);
            Assert.fail();
            assert apdu != null;
        } catch (AssertionError ignore) {
        }
    }

    @Test
    public void badP1() {
        try {
            CommandApdu apdu = new CommandApdu(0x0, 0x0, -1, 0x0);
            Assert.fail();
            assert apdu != null;
        } catch (AssertionError ignore) {
        }
    }

    @Test
    public void badP2() {
        try {
            CommandApdu apdu = new CommandApdu(0x0, 0x0, 0x0, -15);
            Assert.fail();
            assert apdu != null;
        } catch (AssertionError ignore) {
        }
    }

    @Test
    public void badDataEmpty() {
        try {
            CommandApdu apdu = new CommandApdu(0x0, 0x0, 0x0, 0x0, new byte[0]);
            Assert.fail();
            assert apdu != null;
        } catch (IllegalArgumentException except) {
            assertThat(except.getMessage(), containsString("Invalid data field"));
        }
    }

    @Test
    public void badDataFull() {
        try {
            CommandApdu apdu = new CommandApdu(0x0, 0x0, 0x0, 0x0, new byte[255]);
            Assert.fail();
            assert apdu != null;
        } catch (IllegalArgumentException except) {
            assertThat(except.getMessage(), containsString("Invalid data field"));
        }
    }

    @Test
    public void badle() {
        try {
            CommandApdu apdu = new CommandApdu(0x0, 0x0, 0x0, 0x0, null, -1);
            Assert.fail();
            assert apdu != null;
        } catch (AssertionError ignore) {
        }
    }

    @Test
    public void badMax_dataFieldLeByte() throws Exception {
        // setup
        byte[] dataField = new byte[249];

        // execute
        try {
            CommandApdu apdu = new CommandApdu(0x00, 0xb0, 0x8F, 0xFF, dataField, 0x12);
            Assert.fail();
            assert apdu != null;
        } catch (IllegalArgumentException except) {
            assertThat(except.getMessage(), containsString("Invalid APDU size"));
        }
    }

    @Test
    public void badMax_dataField() throws Exception {
        // setup
        byte[] dataField = new byte[250];

        // execute
        try {
            CommandApdu apdu = new CommandApdu(0x00, 0xb0, 0x8F, 0xFF, dataField);
            Assert.fail();
            assert apdu != null;
        } catch (IllegalArgumentException except) {
            assertThat(except.getMessage(), containsString("Invalid data field"));
        }
    }
}
