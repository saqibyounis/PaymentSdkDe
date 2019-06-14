/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;
import static com.miurasystems.miuralibrary.enums.InterfaceType.RPI;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(Enclosed.class)
public class ResponseMessageTest {

    public static class ResponseMessageObjectTest {

        @Test
        public void ctor_tooSmall() {

            //setup
            List<byte[]> data = Arrays.asList(new byte[0], new byte[1]);

            for (byte[] bytes : data) {
                try {
                    // execute
                    ResponseMessage x = new ResponseMessage(MPI, false, bytes);
                    Assert.fail();
                    assertThat(x, is(nullValue()));
                } catch (IllegalArgumentException ex) {
                    // validate
                    assertThat(ex.getMessage(), containsString("too small"));
                }
            }
        }

        @Test
        public void ctor_min() {
            // setup
            byte[] bytes = new byte[]{(byte) 0x90, (byte) 0x00};

            // execute
            ResponseMessage response = new ResponseMessage(MPI, false, bytes);

            //verify
            assertThat(response, is(not(nullValue())));
        }

        @Test
        public void ctor_large() {
            // setup
            byte[] bytes = new byte[4096];
            bytes[4094] = (byte) 0x90;
            bytes[4095] = (byte) 0x00;

            // execute
            ResponseMessage response = new ResponseMessage(MPI, false, bytes);

            //verify
            assertThat(response, is(not(nullValue())));
        }

        @Test
        public void isSuccessTrue() {
            // setup
            byte[] bytes = {(byte) 0x90, (byte) 0x00};
            ResponseMessage responseMessage = new ResponseMessage(MPI, false, bytes);

            // execute
            boolean success = responseMessage.isSuccess();

            //verify
            assertThat(success, is(true));
        }

        @Test
        public void isSuccessFalse() {
            // setup
            List<byte[]> errors = Arrays.asList(
                    new byte[]{(byte) 0x6a, (byte) 0x88}, // Referenced data not found.
                    new byte[]{(byte) 0x6f, (byte) 0x00}, // packet problems
                    new byte[]{(byte) 0x9f, (byte) 0x01}, // File System Error.
                    new byte[]{(byte) 0x9f, (byte) 0x02}, // File Storage Number Limit exceeded
                    new byte[]{(byte) 0x9f, (byte) 0x03}, // File Storage Size Limit exceeded
                    new byte[]{(byte) 0x9f, (byte) 0x0D}, // Error processing proprietary command
                    new byte[]{(byte) 0x9f, (byte) 0x14}, // Command formatting error
                    new byte[]{(byte) 0x9f, (byte) 0x21}, // Invalid data in command APDU
                    new byte[]{(byte) 0x9f, (byte) 0x22}, // Terminal not ready
                    new byte[]{(byte) 0x9f, (byte) 0x23}, // No smartcard in slot
                    new byte[]{(byte) 0x9f, (byte) 0x25}, // Invalid card
                    new byte[]{(byte) 0x9f, (byte) 0x26}, // Command not allowed at this state.
                    new byte[]{(byte) 0x9f, (byte) 0x27}, // Data missing from command APDU
                    new byte[]{(byte) 0x9f, (byte) 0x28}, // Unsupported card
                    new byte[]{(byte) 0x9f, (byte) 0x2A}, // Missing file
                    new byte[]{(byte) 0x9f, (byte) 0x30}, // ICC Read error
                    new byte[]{(byte) 0x9f, (byte) 0x40}, // Invalid issuer public key
                    new byte[]{(byte) 0x9f, (byte) 0x41}, // Cancelled / Aborted
                    new byte[]{(byte) 0x9f, (byte) 0x42}, // Transaction Timed Out
                    new byte[]{(byte) 0x9f, (byte) 0x43}, // Transaction Aborted by a card insertion
                    new byte[]{(byte) 0x9f, (byte) 0x44}, // Transaction Aborted by a card swipe
                    new byte[]{(byte) 0x9f, (byte) 0xC1}, // No applications for the transaction
                    new byte[]{(byte) 0x9f, (byte) 0xC2}, // Transaction not possible
                    new byte[]{(byte) 0x9f, (byte) 0xC3}, // The card asked for Chip interface
                    new byte[]{(byte) 0x9f, (byte) 0xCF}, // Hardware error
                    new byte[]{(byte) 0x9f, (byte) 0xE0},
                    new byte[]{(byte) 0x9f, (byte) 0xE1},
                    new byte[]{(byte) 0x9f, (byte) 0xE2},
                    new byte[]{(byte) 0x9f, (byte) 0xE3},
                    new byte[]{(byte) 0x9f, (byte) 0xE4},
                    new byte[]{(byte) 0x9f, (byte) 0xE5},
                    new byte[]{(byte) 0x9f, (byte) 0xE6},
                    new byte[]{(byte) 0x9f, (byte) 0xED}
            );
            for (byte[] bytes : errors) {
                // execute
                boolean success = new ResponseMessage(MPI, false, bytes).isSuccess();

                //verify
                assertThat(success, is(false));
            }
        }

        @Test
        public void getStatusCodeSuccess() {
            // setup
            ResponseMessage response = new ResponseMessage(MPI, false,
                    new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, (byte) 0x90, (byte) 0x00});

            // execute
            boolean success = response.isSuccess();
            int statusCode = response.getStatusCode();

            //verify
            assertThat(success, is(true));
            assertThat(statusCode, is(equalTo(0x9000)));
        }

        @Test
        public void getStatusCodeFailed() {
            // setup
            ResponseMessage response = new ResponseMessage(MPI, false,
                    new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, (byte) 0x9f, (byte) 0xE0});

            // execute
            boolean success = response.isSuccess();
            int statusCode = response.getStatusCode();

            //verify
            assertThat(success, is(false));
            assertThat(statusCode, is(equalTo(0x9fe0)));
        }

        @Test
        public void getSw12Success() {
            // setup
            ResponseMessage response = new ResponseMessage(MPI, false,
                    new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, (byte) 0x90, (byte) 0x00});

            // execute
            boolean success = response.isSuccess();
            int statusCode = response.getStatusCode();
            byte sw1 = response.getSw1();
            byte sw2 = response.getSw2();

            //verify
            assertThat(success, is(true));
            assertThat(statusCode, is(equalTo(0x9000)));
            assertThat(sw1, is((byte) 0x90));
            assertThat(sw2, is((byte) 0x00));
        }

        @Test
        public void getSw12Failed() {
            // setup
            ResponseMessage response = new ResponseMessage(MPI, false,
                    new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, (byte) 0x9f, (byte) 0xE0});

            // execute
            boolean success = response.isSuccess();
            int statusCode = response.getStatusCode();
            byte sw1 = response.getSw1();
            byte sw2 = response.getSw2();

            //verify
            assertThat(success, is(false));
            assertThat(statusCode, is(equalTo(0x9fe0)));
            assertThat(sw1, is((byte) 0x9f));
            assertThat(sw2, is((byte) 0xe0));
        }


        @Test
        public void getBodyEmpty() {
            // setup
            ResponseMessage response = new ResponseMessage(MPI, false,
                    new byte[]{(byte) 0x90, (byte) 0x00});

            // execute
            byte[] body = response.getBody();

            //verify
            assertThat(body.length, is(equalTo(0)));
            assertThat(body, is(equalTo(new byte[0])));
        }

        @Test
        public void getBodyMin() {
            // setup
            ResponseMessage response = new ResponseMessage(MPI, false,
                    new byte[]{0x5a, (byte) 0x90, (byte) 0x00});

            // execute
            byte[] body = response.getBody();

            //verify
            assertThat(body.length, is(equalTo(1)));
            assertThat(body, is(equalTo(new byte[]{0x5a})));
        }

        @Test
        public void getBodyNormal() {
            // setup
            ResponseMessage response = new ResponseMessage(MPI, false,
                    new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, (byte) 0x90, (byte) 0x00});

            // execute
            byte[] body = response.getBody();


            //verify
            assertThat(body.length, is(equalTo(5)));
            assertThat(body, is(equalTo(new byte[]{0x1, 0x2, 0x3, 0x4, 0x5})));
        }

        @Test
        public void getBodyLarge() {
            // setup
            byte[] bytes = new byte[4096];
            bytes[0] = 0x1;
            bytes[510] = 0x2;
            bytes[1020] = 0x3;
            bytes[2040] = 0x4;
            bytes[3090] = 0x5;
            bytes[4093] = 0x5;
            bytes[4094] = (byte) 0x90;
            bytes[4095] = (byte) 0x00;
            ResponseMessage response = new ResponseMessage(MPI, false, bytes);

            // execute
            byte[] body = response.getBody();

            //verify
            assertThat(body.length, is(equalTo(4094)));
            byte[] actual = new byte[4094];
            actual[0] = 0x1;
            actual[510] = 0x2;
            actual[1020] = 0x3;
            actual[2040] = 0x4;
            actual[3090] = 0x5;
            actual[4093] = 0x5;
            assertThat(body, is(equalTo(actual)));
        }

        @Test
        public void packetProperties() {
            List<Boolean> unsolicitedData = Arrays.asList(true, false);
            List<InterfaceType> addressData = Arrays.asList(MPI, RPI);


            for (boolean expectedUnsolicited : unsolicitedData) {
                for (InterfaceType expectedAddress : addressData) {
                    // setup
                    ResponseMessage response = new ResponseMessage(
                            expectedAddress, expectedUnsolicited,
                            new byte[]{0x1, 0x2, 0x3, 0x4, 0x5, (byte) 0x90, (byte) 0x00});

                    // execute
                    boolean success = response.isSuccess();
                    int statusCode = response.getStatusCode();
                    boolean unsolicited = response.isUnsolicited();
                    InterfaceType address = response.getNodeAddress();

                    //verify
                    assertThat(success, is(true));
                    assertThat(statusCode, is(equalTo(0x9000)));
                    assertThat(unsolicited, is(equalTo(expectedUnsolicited)));
                    assertThat(address, is(equalTo(expectedAddress)));
                }
            }
        }
    }
}


