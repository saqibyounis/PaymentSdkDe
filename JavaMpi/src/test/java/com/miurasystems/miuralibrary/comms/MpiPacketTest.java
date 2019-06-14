/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import static com.miurasystems.miuralibrary.tlv.BinaryUtil.intToUbyte;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.miurasystems.miuralibrary.Pair;
import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nl.jqno.equalsverifier.EqualsVerifier;

@RunWith(Enclosed.class)
public final class MpiPacketTest {

    private static final int NAD_PED = 0x1;
    private static final int NAD_RPI = 0x2;
    private static final int PCB = 0x0;
    private static final int PCB_CHAINED = 0x1;
    private static final int PCB_UNSOLICITED = 0x40;
    private static final int SW_1_OK = 0x90;
    private static final int SW_2_OK = 0x00;

    public static class Constructors {
        @Test
        public void ctorInterfaceApdu_interfaceTypes() {
            // setup
            List<Pair<InterfaceType, Integer>> data = Arrays.asList(
                    new Pair<>(InterfaceType.MPI, 1),
                    new Pair<>(InterfaceType.RPI, 2)
            );

            for (Pair<InterfaceType, Integer> pair : data) {
                InterfaceType nad = pair.first;

                // execute
                MpiPacket packet = new MpiPacket(nad, new byte[]{0x0, 0x1, 0x2});

                //verify

                // It's valid to test the byte value, as the MPI protocol specifically wants 1 and
                // 2.
                assertThat(packet.getBytes()[0], is(equalTo(pair.second.byteValue())));
                assertThat(packet.getNodeAddress(), is(equalTo(pair.first)));
                assertThat(packet.isChained(), is(false));
                assertThat(packet.isUnsolicited(), is(false));
            }
        }

        @Test
        public void ctorInterfaceApdu_apduGood() {
            // setup
            List<byte[]> data = Arrays.asList(
                    new byte[2],
                    new byte[3],
                    new byte[4],
                    new byte[127],
                    new byte[128],
                    new byte[254],
                    new byte[]{-1, -1, -1}
            );

            for (byte[] apdu : data) {
                // execute
                MpiPacket packet = new MpiPacket(InterfaceType.MPI, apdu);

                //verify
                assertThat(packet, is(notNullValue()));
                assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
                assertThat(packet.isChained(), is(false));
                assertThat(packet.isUnsolicited(), is(false));
            }
        }

        @Test
        public void ctorInterfaceApdu_apduBadSize() {
            // setup
            List<byte[]> data = Arrays.asList(
                    new byte[0],
                    new byte[1],
                    new byte[255],
                    new byte[256],
                    new byte[1024]
            );

            for (byte[] apdu : data) {
                // execute
                try {
                    MpiPacket packet = new MpiPacket(InterfaceType.MPI, apdu);
                    Assert.fail();
                    assert packet != null;
                } catch (IllegalArgumentException exception) {
                    //verify
                    assertThat(exception.getMessage(), containsString("Invalid apduBytes"));
                }
            }
        }

        @Test
        public void ctorByteArray_NormalSized() {
            // setup
            byte[] bytes = new byte[]{
                    NAD_PED, PCB, 6, 0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK, (byte) 0x97,
            };

            // execute
            MpiPacket packet = new MpiPacket(bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorByteArray_Chained() {
            // setup
            byte[] bytes = new byte[]{
                    NAD_PED, PCB_CHAINED, 6, 0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK,
                    (byte) 0x96,
            };

            // execute
            MpiPacket packet = new MpiPacket(bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(true));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorByteArray_Unsolicited() {
            // setup
            byte[] bytes = new byte[]{
                    NAD_PED, PCB_UNSOLICITED,
                    6, 0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK, (byte) 0xd7,
            };

            // execute
            MpiPacket packet = new MpiPacket(bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(true));
        }

        @Test
        public void ctorByteArray_UnsolicitedChained() {
            // setup
            byte[] bytes = new byte[]{
                    NAD_PED, PCB_UNSOLICITED | PCB_CHAINED,
                    6, 0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK, (byte) 0xd6,
            };

            // execute
            MpiPacket packet = new MpiPacket(bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(true));
            assertThat(packet.isUnsolicited(), is(true));
        }

        @Test
        public void ctorByteArray_MinSized() {
            // setup
            byte[] bytes = new byte[]{NAD_PED, PCB, 2, 0x50, 0x60, 0x33,};

            // execute
            MpiPacket packet = new MpiPacket(bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorByteArray_MaxSized() {
            // setup
            byte[] bytes = new byte[258];
            bytes[0] = NAD_PED;
            bytes[1] = PCB;
            bytes[2] = (byte) 254; // len
            for (int i = 0; i < 254; i++) {
                bytes[i + 3] = (byte) i;
            }
            bytes[257] = (byte) 0xfe;

            // execute
            MpiPacket packet = new MpiPacket(bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorByteArray_badNad() {
            // setup
            byte[] bytes = new byte[]{0x0, PCB, 2, 0x50, 0x60, 0x32,};

            // execute
            try {
                MpiPacket packet = new MpiPacket(bytes);
                Assert.fail();
                assert packet != null;
            } catch (IllegalArgumentException exception) {
                //verify
                assertThat(exception.getMessage().toUpperCase(), containsString("NAD"));
            }
        }

        @Test
        public void ctorByteArray_badPcb() {
            // setup
            byte[] bytes = new byte[]{NAD_PED, (byte) 0xFF, 2, 0x50, 0x60, (byte) 0xCC,};

            // execute
            try {
                MpiPacket packet = new MpiPacket(bytes);
                Assert.fail();
                assert packet != null;
            } catch (IllegalArgumentException exception) {
                //verify
                assertThat(exception.getMessage().toUpperCase(), containsString("PCB"));
            }
        }

        @Test
        public void ctorByteArray_badLen() {
            // setup
            List<Integer> data = Arrays.asList(0, 1, 255);
            for (int len : data) {
                try {
                    // more setup
                    byte[] bytes = new byte[]{NAD_PED, PCB, (byte) len, 0x50, 0x60, 0x31,};

                    // execute
                    MpiPacket packet = new MpiPacket(bytes);
                    Assert.fail();
                    assert packet != null;
                } catch (IllegalArgumentException exception) {
                    //verify
                    assertThat(exception.getMessage().toUpperCase(), containsString("LEN"));
                }
            }
        }

        @Test
        public void ctorByteArray_badLenMismatch() {
            // setup
            List<Integer> data = Arrays.asList(3, 4, 5, 127, 128, 129, 254);
            for (int len : data) {
                try {
                    // more setup
                    byte[] bytes = new byte[]{NAD_PED, PCB, (byte) len, 0x50, 0x60, 0x31,};

                    // execute
                    MpiPacket packet = new MpiPacket(bytes);
                    Assert.fail();
                    assert packet != null;
                } catch (IllegalArgumentException exception) {
                    //verify
                    assertThat(exception.getMessage().toUpperCase(), containsString("MATCH"));
                    assertThat(exception.getMessage().toUpperCase(), containsString("LEN"));
                    assertThat(exception.getMessage().toUpperCase(),
                            containsString(Integer.toHexString(len).toUpperCase()));
                    assertThat(exception.getMessage().toUpperCase(), containsString("6"));
                }
            }
        }

        @Test
        public void ctorByteArray_badLrc() {
            // setup
            byte[] bytes = new byte[]{
                    NAD_PED, PCB, 6, 0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK, 0x0,
            };

            // execute
            try {
                MpiPacket packet = new MpiPacket(bytes);
                Assert.fail();
                assert packet != null;
            } catch (IllegalArgumentException exception) {
                //verify
                assertThat(exception.getMessage().toUpperCase(), containsString("LRC"));
            }
        }

        @Test
        public void ctorByteArray_TooSmall() {
            try {
                // setup
                byte[] bytes = new byte[1];
                bytes[0] = NAD_PED;

                // execute
                //noinspection Range
                MpiPacket packet = new MpiPacket(bytes);
                Assert.fail();
                assert packet != null;
            } catch (IllegalArgumentException exception) {
                //verify
                assertThat(exception.getMessage().toLowerCase(), containsString("byte[] length"));
            }
        }

        @Test
        public void ctorByteArray_TooLarge() {
            try {
                // setup
                byte[] bytes = new byte[500];
                bytes[0] = NAD_PED;
                bytes[1] = PCB;
                bytes[2] = (byte) (500 - 4);

                // execute
                //noinspection Range
                MpiPacket packet = new MpiPacket(bytes);
                Assert.fail();
                assert packet != null;
            } catch (IllegalArgumentException exception) {
                //verify
                assertThat(exception.getMessage().toLowerCase(), containsString("byte[] length"));
            }
        }

        @Test
        public void ctorIntIntApdu_NormalSized() {
            // setup
            byte[] bytes = new byte[]{
                    0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK,
            };

            // execute
            MpiPacket packet = new MpiPacket(NAD_PED, PCB, bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorIntIntApdu_Chained() {
            // setup
            byte[] bytes = new byte[]{
                    0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK,
            };

            // execute
            MpiPacket packet = new MpiPacket(NAD_PED, PCB_CHAINED, bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(true));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorIntIntApdu_Unsolicited() {
            // setup
            byte[] bytes = new byte[]{
                    0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK,
            };

            // execute
            MpiPacket packet = new MpiPacket(NAD_PED, PCB_UNSOLICITED, bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(true));
        }

        @Test
        public void ctorIntIntApdu_UnsolicitedChained() {
            // setup
            byte[] bytes = new byte[]{
                    0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK,
            };

            // execute
            MpiPacket packet = new MpiPacket(NAD_PED, PCB_UNSOLICITED | PCB_CHAINED, bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(true));
            assertThat(packet.isUnsolicited(), is(true));
        }

        @Test
        public void ctorIntIntApdu_MinSized() {
            // setup
            byte[] bytes = new byte[]{0x50, 0x60};

            // execute
            MpiPacket packet = new MpiPacket(NAD_PED, PCB, bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorIntIntApdu_MaxSized() {
            // setup
            byte[] bytes = new byte[254];
            for (int i = 0; i < 254; i++) {
                bytes[i] = (byte) i;
            }

            // execute
            MpiPacket packet = new MpiPacket(NAD_PED, PCB, bytes);

            // verify
            assertThat(packet, is(notNullValue()));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void ctorIntIntApdu_apduBadSize() {
            // setup
            List<byte[]> data = Arrays.asList(
                    new byte[0],
                    new byte[1],
                    new byte[255],
                    new byte[256],
                    new byte[1024]
            );

            for (byte[] apdu : data) {
                // execute
                try {
                    MpiPacket packet = new MpiPacket(NAD_PED, PCB, apdu);
                    Assert.fail();
                    assert packet != null;
                } catch (IllegalArgumentException exception) {
                    //verify
                    assertThat(exception.getMessage(), containsString("Invalid apduBytes"));
                }
            }
        }

        @Test
        public void ctorIntIntApdu_BadNad() {
            // setup
            byte[] bytes = new byte[]{0x10, 0x20};

            // execute
            try {
                MpiPacket packet = new MpiPacket(0x0, PCB, bytes);
                Assert.fail();
                assert packet != null;
            } catch (IllegalArgumentException exception) {
                //verify
                assertThat(exception.getMessage().toUpperCase(), containsString("NAD"));
            }
        }

        @Test
        public void ctorIntIntApdu_BadPcb() {
            // setup
            byte[] bytes = new byte[]{0x10, 0x20};

            // execute
            try {
                MpiPacket packet = new MpiPacket(NAD_PED, 0xFF, bytes);
                Assert.fail();
                assert packet != null;
            } catch (IllegalArgumentException exception) {
                //verify
                assertThat(exception.getMessage().toUpperCase(), containsString("PCB"));
            }
        }
    }

    public static class GetBytes {
        @Test
        public void getBytes_smallestPacket() {
            // setup
            byte[] apduBytes = {(byte) SW_1_OK, SW_2_OK};
            MpiPacket packet = new MpiPacket(InterfaceType.MPI, apduBytes);

            // execute
            byte[] actualBytes = packet.getBytes();

            // verify
            byte[] expectedBytes =
                    new byte[]{NAD_PED, PCB, 2, (byte) SW_1_OK, SW_2_OK, (byte) 0x93};
            assertThat(actualBytes, is(equalTo(expectedBytes)));
        }

        @Test
        public void getBytes_normalPacket() {
            // setup
            byte[] apduBytes = {0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60, (byte) SW_1_OK, SW_2_OK};
            MpiPacket packet = new MpiPacket(InterfaceType.MPI, apduBytes);

            // execute
            byte[] actualBytes = packet.getBytes();

            // verify
            byte[] expectedBytes = new byte[]{
                    NAD_PED, PCB, 9,
                    0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60, (byte) SW_1_OK, SW_2_OK,
                    (byte) 0xf9
            };
            assertThat(actualBytes, is(equalTo(expectedBytes)));
        }

        @Test
        public void getBytes_largestPacket() {
            // setup
            byte[] apduBytes = new byte[254];
            apduBytes[0] = 0x00;
            apduBytes[10] = 0x11;
            apduBytes[100] = 0x22;
            apduBytes[107] = 0x33;
            apduBytes[123] = 0x44;
            apduBytes[150] = 0x55;
            apduBytes[175] = 0x66;
            apduBytes[199] = 0x77;
            apduBytes[201] = (byte) 0x88;
            apduBytes[253] = (byte) 0x99;
            MpiPacket packet = new MpiPacket(InterfaceType.MPI, apduBytes);

            // execute
            byte[] actualBytes = packet.getBytes();

            // verify

            byte[] expectedBytes = new byte[258];
            expectedBytes[0] = NAD_PED;
            expectedBytes[1] = PCB;
            expectedBytes[2] = (byte) 254;
            expectedBytes[3] = 0x00;
            expectedBytes[3 + 10] = 0x11;
            expectedBytes[3 + 100] = 0x22;
            expectedBytes[3 + 107] = 0x33;
            expectedBytes[3 + 123] = 0x44;
            expectedBytes[3 + 150] = 0x55;
            expectedBytes[3 + 175] = 0x66;
            expectedBytes[3 + 199] = 0x77;
            expectedBytes[3 + 201] = (byte) 0x88;
            expectedBytes[3 + 253] = (byte) 0x99;
            expectedBytes[257] = (byte) 0xee;

            assertThat(actualBytes, is(equalTo(expectedBytes)));
        }
    }

    public static class Equals {
        @Test
        public void equalsContract() {
            EqualsVerifier.forClass(MpiPacket.class).verify();
        }
    }

    public static class ReadPacketFromStreamSingle {

        @Test
        public void normalSized() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 6, 0x10, 0x11, 0x12, 0x13, SW_1_OK, SW_2_OK, 0x97,
                    // this exception should never happen, as the stream should never get here
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assert packet != null;
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
            byte[] expectedBytes = new byte[]{
                    NAD_PED, PCB, 6, 0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK, (byte) 0x97,
            };
            assertThat(packet.getBytes(), is(equalTo(expectedBytes)));
        }

        @Test
        public void minSized() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 2, SW_1_OK, SW_2_OK, 0x93, MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assert packet != null;
            byte[] expectedBytes = new byte[]{
                    NAD_PED, PCB, 2, (byte) SW_1_OK, SW_2_OK, (byte) 0x93,
            };
            assertThat(packet.getBytes(), is(equalTo(expectedBytes)));
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
        }

        @Test
        public void maxSized() {
            // setup
            int[] stream = new int[259];
            stream[0] = NAD_PED;
            stream[1] = PCB;
            stream[2] = 254; // len
            for (int i = 0; i < 254; i++) {
                stream[i + 3] = 255 - i;
            }
            stream[257] = 0xfe;
            stream[258] = MockInputStream.ASSERT;
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assert packet != null;
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));

            byte[] expectedBytes = new byte[258];
            expectedBytes[0] = NAD_PED;
            expectedBytes[1] = PCB;
            expectedBytes[2] = (byte) 254; // len
            for (int i = 0; i < 254; i++) {
                expectedBytes[i + 3] = (byte) (255 - i);
            }
            expectedBytes[257] = (byte) 0xfe;
            assertThat(packet.getBytes(), is(equalTo(expectedBytes)));
        }

        /**
         * MpiPacket.readFromStream shouldn't care about chained/unchained.
         */
        @Test
        public void chained() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB_CHAINED, 4, 0xc0, 0xc1, 0xc2, 0xc3, 0x04, MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assert packet != null;
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(true));
            assertThat(packet.isUnsolicited(), is(false));
            byte[] expectedBytes = new byte[]{
                    NAD_PED, PCB_CHAINED, 4, (byte) 0xc0, (byte) 0xc1, (byte) 0xc2, (byte) 0xc3,
                    0x04,
            };
            assertThat(packet.getBytes(), is(equalTo(expectedBytes)));
        }

        /**
         * MpiPacket.readFromStream shouldn't care about un/solicited
         */
        @Test
        public void unsolicited() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB_UNSOLICITED, 2, 0x50, 0x51, 0x42, MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assert packet != null;
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(true));
            byte[] expectedBytes = new byte[]{
                    NAD_PED, PCB_UNSOLICITED, 2, 0x50, 0x51, 0x42,
            };
            assertThat(packet.getBytes(), is(equalTo(expectedBytes)));
        }

        /**
         * MpiPacket.readFromStream shouldn't care about unsolicited or chained
         */
        @Test
        public void unsolicitedChained() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, 2, 0x50, 0x51, 0x43,
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assert packet != null;
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(true));
            assertThat(packet.isUnsolicited(), is(true));
            byte[] expectedBytes = new byte[]{
                    NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, 2, 0x50, 0x51, 0x43,
            };
            assertThat(packet.getBytes(), is(equalTo(expectedBytes)));
        }

        @Test
        public void emptyStreamEOF() {
            // setup
            int[] stream = new int[]{MockInputStream.EOF};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
        }

        @Test
        public void emptyStreamIOException() {
            // setup
            int[] stream = new int[]{MockInputStream.THROW_IO_EXCEPTION};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
        }

        @Test
        public void incomplete_nadEOF() throws IOException {
            // setup
            int[] stream = new int[]{NAD_PED, MockInputStream.EOF};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(2)));
            assertThat(baInput.read(), is(equalTo(NAD_PED)));
        }

        @Test
        public void incomplete_nadIOException() throws IOException {
            // setup
            int[] stream = new int[]{NAD_PED, MockInputStream.THROW_IO_EXCEPTION};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(2)));
            assertThat(baInput.read(), is(equalTo(NAD_PED)));
        }

        @Test
        public void incomplete_nadPcbEOF() throws IOException {
            // setup
            int[] stream = new int[]{NAD_PED, PCB, MockInputStream.EOF};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(3)));
            assertThat(baInput.read(), is(equalTo(NAD_PED)));
            assertThat(baInput.read(), is(equalTo(PCB)));
        }

        @Test
        public void incomplete_nadPcbIOException() throws IOException {
            // setup
            int[] stream = new int[]{NAD_PED, PCB, MockInputStream.THROW_IO_EXCEPTION};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(3)));
            assertThat(baInput.read(), is(equalTo(NAD_PED)));
            assertThat(baInput.read(), is(equalTo(PCB)));
        }

        @Test
        public void incomplete_nadPcbLenEOF() throws IOException {
            // setup
            int[] stream = new int[]{NAD_PED, PCB, 12, MockInputStream.EOF};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // MpiPacket only claims to mark/reset a header's worth of data, which we've read,
            // so can't check for mark/reset here/
        }

        @Test
        public void incomplete_nadPcbLenIOException() throws IOException {
            // setup
            int[] stream = new int[]{NAD_PED, PCB, 12, MockInputStream.THROW_IO_EXCEPTION};
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // MpiPacket only claims to mark/reset a header's worth of data, which we've read,
            // so can't check for mark/reset here/
        }

        @Test
        public void badNad() throws IOException {
            // setup
            int[] stream = new int[]{
                    0xF0, PCB, 6, 0x10, 0x11, 0x12, 0x13, SW_1_OK, SW_2_OK, 0x66,
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(11)));
            assertThat(baInput.read(), is(equalTo(0xF0)));
        }

        @Test
        public void badPCB() throws IOException {
            // setup
            int[] stream = new int[]{
                    NAD_PED, 0xFe, 6, 0x10, 0x11, 0x12, 0x13, 0xf9, MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(9)));
            assertThat(baInput.read(), is(equalTo(NAD_PED)));
            assertThat(baInput.read(), is(equalTo(0xFe)));
        }

        @Test
        public void badLenTooSmall() throws IOException {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB_UNSOLICITED, 0x1, 0x10, 0x43, MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(6)));
            assertThat(baInput.read(), is(equalTo(NAD_PED)));
            assertThat(baInput.read(), is(equalTo(PCB_UNSOLICITED)));
            assertThat(baInput.read(), is(equalTo(0x1)));
        }

        @Test
        public void badLenTooLarge() throws IOException {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB_UNSOLICITED, 0xFF, 0x10, 0x11, 0x12, 0x13, 0xbe,
                    MockInputStream.EOF,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // make sure we 'put back' the bytes if the stream handled it
            assertThat(baInput.available(), is(equalTo(9)));
            assertThat(baInput.read(), is(equalTo(NAD_PED)));
            assertThat(baInput.read(), is(equalTo(PCB_UNSOLICITED)));
            assertThat(baInput.read(), is(equalTo(0xFF)));
        }

        @Test
        public void packetTooShortEOF() throws IOException {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 16,
                    0x10, 0x11, 0x12, 0x13,
                    0x20, 0x21, 0x22, 0x23,
                    0x30, 0x31, 0x32, 0x33,
                    MockInputStream.EOF,
                    0x40, 0x41, 0x42, 0x43,
                    0x7d,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // MpiPacket only claims to mark/reset a header's worth of data, which we've read,
            // so can't check for mark/reset here/
        }

        @Test
        public void packetTooShortIOException() throws IOException {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 16,
                    0x10, 0x11, 0x12, 0x13,
                    0x20, 0x21, 0x22, 0x23,
                    0x30, 0x31, 0x32, 0x33,
                    MockInputStream.THROW_IO_EXCEPTION,
                    0x40, 0x41, 0x42, 0x43,
                    0x7d,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
            // MpiPacket only claims to mark/reset a header's worth of data, which we've read,
            // so can't check for mark/reset here/
        }

        @Test
        public void noLrcEof() throws IOException {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 6,
                    0x10, 0x11, 0x12, 0x13, SW_1_OK, SW_2_OK,
                    MockInputStream.EOF,
                    0x97,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
        }

        @Test
        public void NoLrcIOException() throws IOException {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 6,
                    0x10, 0x11, 0x12, 0x13, SW_1_OK, SW_2_OK,
                    MockInputStream.THROW_IO_EXCEPTION,
                    0x97,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
        }

        @Test
        public void badLrc() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 6, 0x10, 0x11, 0x12, 0x13, SW_1_OK, SW_2_OK, 0xFF,
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assertThat(packet, is(nullValue()));
        }

        /**
         * Ensure that reading from a stream still works even if the stream is terrible and the
         * {@link InputStream#read(byte[])} calls keep returning less than asked for.
         */
        @Test
        public void cutStream() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, MockInputStream.CUT_STREAM,
                    PCB, 6, MockInputStream.CUT_STREAM,
                    0x10, 0x11, MockInputStream.CUT_STREAM,
                    0x12, 0x13, MockInputStream.CUT_STREAM,
                    SW_1_OK, SW_2_OK, MockInputStream.CUT_STREAM,
                    0x97,
                    // this exception should never happen, as the stream should never get here
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packet = MpiPacket.readFromStream(baInput);

            // verify
            assert packet != null;
            assertThat(packet.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packet.isChained(), is(false));
            assertThat(packet.isUnsolicited(), is(false));
            byte[] expectedBytes = new byte[]{
                    NAD_PED, PCB, 6, 0x10, 0x11, 0x12, 0x13, (byte) SW_1_OK, SW_2_OK, (byte) 0x97,
            };
            assertThat(packet.getBytes(), is(equalTo(expectedBytes)));
        }

    }

    public static class ReadPacketFromStreamMulti {

        @Test
        public void unchainedPackets() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB, 3, 0x10, 0x11, 0x12, 0x11,
                    NAD_PED, PCB, 20,
                    0x20, 0x21, 0x22, 0x23, 0x24,
                    0x25, 0x26, 0x27, 0x28, 0x29,
                    0x2a, 0x2b, 0x2c, 0x2d, 0x2e,
                    0x2f, 0x30, 0x31, 0x32, 0x33,
                    0x15,
                    // this exception should never happen, as the stream should never get here
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packetOne = MpiPacket.readFromStream(baInput);
            MpiPacket packetTwo = MpiPacket.readFromStream(baInput);

            // verify
            assert packetOne != null;
            assertThat(packetOne.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packetOne.isChained(), is(false));
            assertThat(packetOne.isUnsolicited(), is(false));
            assertThat(packetOne.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 3, 0x10, 0x11, 0x12, 0x11,
            })));

            assert packetTwo != null;
            assertThat(packetTwo.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packetTwo.isChained(), is(false));
            assertThat(packetTwo.isUnsolicited(), is(false));
            assertThat(packetTwo.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 20,
                    0x20, 0x21, 0x22, 0x23, 0x24,
                    0x25, 0x26, 0x27, 0x28, 0x29,
                    0x2a, 0x2b, 0x2c, 0x2d, 0x2e,
                    0x2f, 0x30, 0x31, 0x32, 0x33,
                    0x15,
            })));
        }

        /**
         * MpiPacket.readFromStream shouldn't care about solicited/chained, but test it anyway
         */
        @Test
        public void chainedPackets() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB_CHAINED, 3, 0x10, 0x11, 0x12, 0x10,
                    NAD_PED, PCB_CHAINED, 20,
                    0x20, 0x21, 0x22, 0x23, 0x24,
                    0x25, 0x26, 0x27, 0x28, 0x29,
                    0x2a, 0x2b, 0x2c, 0x2d, 0x2e,
                    0x2f, 0x30, 0x31, 0x32, 0x33,
                    0x14,
                    // this exception should never happen, as the stream should never get here
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket packetOne = MpiPacket.readFromStream(baInput);
            MpiPacket packetTwo = MpiPacket.readFromStream(baInput);

            // verify
            assert packetOne != null;
            assertThat(packetOne.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packetOne.isChained(), is(true));
            assertThat(packetOne.isUnsolicited(), is(false));
            assertThat(packetOne.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB_CHAINED, 3, 0x10, 0x11, 0x12, 0x10,
            })));

            assert packetTwo != null;
            assertThat(packetTwo.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(packetTwo.isChained(), is(true));
            assertThat(packetTwo.isUnsolicited(), is(false));
            assertThat(packetTwo.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB_CHAINED, 20,
                    0x20, 0x21, 0x22, 0x23, 0x24,
                    0x25, 0x26, 0x27, 0x28, 0x29,
                    0x2a, 0x2b, 0x2c, 0x2d, 0x2e,
                    0x2f, 0x30, 0x31, 0x32, 0x33,
                    0x14,
            })));
        }

        /**
         * MpiPacket.readFromStream shouldn't care about solicited/chained, but test it anyway
         */
        @Test
        public void mixedPackets() {
            // setup
            int[] stream = new int[]{
                    NAD_PED, PCB_CHAINED, 3, 0x10, 0x11, 0x12, 0x10,
                    NAD_PED, PCB_UNSOLICITED, 4, 0x20, 0x21, 0x22, 0x23, 0x45,
                    NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, 5, 0x30, 0x31, 0x32, 0x33, 0x34, 0x71,
                    NAD_PED, PCB, 2, 0x40, 0x41, 0x02,
                    // this exception should never happen, as the stream should never get here
                    MockInputStream.ASSERT,
            };
            InputStream baInput = new MockInputStream(stream);

            // execute
            MpiPacket one = MpiPacket.readFromStream(baInput);
            MpiPacket two = MpiPacket.readFromStream(baInput);
            MpiPacket three = MpiPacket.readFromStream(baInput);
            MpiPacket four = MpiPacket.readFromStream(baInput);


            // verify
            assert one != null;
            assertThat(one.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(one.isChained(), is(true));
            assertThat(one.isUnsolicited(), is(false));
            assertThat(one.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB_CHAINED, 3, 0x10, 0x11, 0x12, 0x10,
            })));

            assert two != null;
            assertThat(two.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(two.isChained(), is(false));
            assertThat(two.isUnsolicited(), is(true));
            assertThat(two.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB_UNSOLICITED, 4, 0x20, 0x21, 0x22, 0x23, 0x45,
            })));

            assert three != null;
            assertThat(three.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(three.isChained(), is(true));
            assertThat(three.isUnsolicited(), is(true));
            assertThat(three.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, 5, 0x30, 0x31, 0x32, 0x33, 0x34, 0x71,
            })));

            assert four != null;
            assertThat(four.getNodeAddress(), is(equalTo(InterfaceType.MPI)));
            assertThat(four.isChained(), is(false));
            assertThat(four.isUnsolicited(), is(false));
            assertThat(four.getBytes(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 2, 0x40, 0x41, 0x02,
            })));
        }
    }

    public static class ReconstructAPDU {

        @Test
        public void emptyList() {
            try {
                MpiPacket.reconstructApdu(new ArrayList<MpiPacket>(0));
                Assert.fail();
            } catch (IllegalArgumentException ignore) {
            }
        }

        @Test
        public void singlePacket_min() {
            // setup
            List<MpiPacket> packets = Collections.singletonList(
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x5a, 0x55}));

            // execute
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // verify
            assertThat(bytes, is(equalTo(new byte[]{0x5a, 0x55})));
        }

        @Test
        public void singlePacket_normal() {
            // setup
            List<MpiPacket> packets = Collections.singletonList(
                    new MpiPacket(NAD_PED, PCB,
                            new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88})
            );

            // execute
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // verify
            assertThat(bytes, is(equalTo(
                    new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88})));
        }

        @Test
        public void singlePacket_max() {
            // setup
            List<MpiPacket> packets = Collections.singletonList(
                    new MpiPacket(NAD_PED, PCB, new byte[254]));

            // execute
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // verify
            assertThat(bytes, is(equalTo(new byte[254])));
        }

        @Test
        public void singlePacket_unsolicited() {
            // setup
            List<MpiPacket> packets = Collections.singletonList(
                    new MpiPacket(NAD_PED, PCB_UNSOLICITED,
                            new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88}));

            // execute
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // verify
            assertThat(bytes, is(equalTo(
                    new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88})));
        }

        /**
         * Can't have a list of chained packets without a terminated unchained one
         */
        @Test
        public void singlePacket_chained() {
            // setup
            List<MpiPacket> packets = Collections.singletonList(
                    new MpiPacket(NAD_PED, PCB_CHAINED,
                            new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88}));

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException ignore) {
                //verify
            }
        }

        @Test
        public void multiPacket_min() {
            // setup

            final byte[] a = new byte[]{0x11, 0x22};
            final byte[] b = new byte[]{0x33, 0x44};

            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED, a),
                    new MpiPacket(NAD_PED, PCB, b)
            );

            // execute
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // verify
            assertThat(bytes, is(equalTo(new byte[]{0x11, 0x22, 0x33, 0x44})));
        }

        /**
         * Max here means some number of max sized packets, rather than a max ADPU spread across
         * as many packets as it takes.
         * A response ADPU seems to be unlimited and command is limited to a single Lc byte
         */
        @Test
        public void multiPacket_max() throws Exception {
            // -----------------------------------------------------
            // setup
            // -----------------------------------------------------
            final byte[] message = "Hello World!".getBytes("US-ASCII");

            // Interleave "Hello World!" amongst the packets
            final byte[] hw = new byte[254];
            final byte[] eo = new byte[254];
            final byte[] lr = new byte[254];
            final byte[] ll = new byte[254];
            final byte[] od = new byte[254];
            final byte[] spex = new byte[254];

            hw[0] = message[0];
            eo[0] = message[1];
            lr[0] = message[2];
            ll[0] = message[3];
            od[0] = message[4];
            spex[0] = message[5];
            hw[127] = message[6];
            eo[127] = message[7];
            lr[127] = message[8];
            ll[127] = message[9];
            od[127] = message[10];
            spex[127] = message[11];

            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED, hw),
                    new MpiPacket(NAD_PED, PCB_CHAINED, eo),
                    new MpiPacket(NAD_PED, PCB_CHAINED, lr),
                    new MpiPacket(NAD_PED, PCB_CHAINED, ll),
                    new MpiPacket(NAD_PED, PCB_CHAINED, od),
                    new MpiPacket(NAD_PED, PCB, spex)
            );

            // -----------------------------------------------------
            // execute
            // -----------------------------------------------------
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // -----------------------------------------------------
            // verify
            // -----------------------------------------------------
            byte[] outMessage = new byte[12];
            int writeOffset = 0;
            for (int readOffset = 0; readOffset < 254 * 6; readOffset += 254) {
                outMessage[writeOffset] = bytes[readOffset];
                outMessage[writeOffset + 6] = bytes[readOffset + 127];
                writeOffset++;
            }
            String s = new String(outMessage, "US-ASCII");
            assertThat(s, is("Hello World!"));
        }

        @Test
        public void multiPacket_badListNoUnchained() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException exc) {
                //verify
                assertThat(exc.getMessage(), containsString("unchained"));
            }
        }

        @Test
        public void multiPacket_badListNoChained() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException exc) {
                //verify
                assertThat(exc.getMessage(), containsString("unchained"));
            }
        }

        @Test
        public void multiPacket_badListUnchainedNotAtEnd() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException exc) {
                //verify
                assertThat(exc.getMessage(), containsString("unchained"));
            }
        }

        @Test
        public void multiPacket_Unsolicited() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB_UNSOLICITED, new byte[]{0x55, 0x66})
            );

            // execute
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // verify
            assertThat(bytes, is(equalTo(new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66})));
        }

        @Test
        public void multiPacket_badListMixedSolicited() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException exc) {
                //verify
                assertThat(exc.getMessage(), containsString("solicited"));
            }
        }

        @Test
        public void multiPacket_badListMixedSolicitedLast() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB_CHAINED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB_UNSOLICITED, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException exc) {
                //verify
                assertThat(exc.getMessage(), containsString("solicited"));
            }
        }

        @Test
        public void multiPacket_badListMixedSolicitedInverse() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException exc) {
                //verify
                assertThat(exc.getMessage(), containsString("solicited"));
            }
        }

        @Test
        public void multiPacket_InterfaceRPI() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_RPI, PCB_CHAINED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_RPI, PCB_CHAINED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_RPI, PCB, new byte[]{0x55, 0x66})
            );

            // execute
            byte[] bytes = MpiPacket.reconstructApdu(packets);

            // verify
            assertThat(bytes, is(equalTo(new byte[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66})));
        }

        @Test
        public void multiPacket_badListInterfaceMixed() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_RPI, PCB_CHAINED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_RPI, PCB_CHAINED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_PED, PCB, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException exc) {
                //verify
                assertThat(exc.getMessage(), containsString("node address"));
            }
        }

        @Test
        public void multiPacket_badListInterfaceUnsolicitedMixed() {
            // setup
            List<MpiPacket> packets = Arrays.asList(
                    new MpiPacket(NAD_RPI, PCB_CHAINED, new byte[]{0x11, 0x22}),
                    new MpiPacket(NAD_PED, PCB_CHAINED | PCB_UNSOLICITED, new byte[]{0x33, 0x44}),
                    new MpiPacket(NAD_RPI, PCB, new byte[]{0x55, 0x66})
            );

            // execute
            try {
                MpiPacket.reconstructApdu(packets);
                Assert.fail();
            } catch (IllegalArgumentException ignore) {
                //verify
            }
        }
    }

    public static class WritePacketToStream {

        @Test
        public void writeToStream_smallestApdu() {
            MockOutputStream mockOutputStream = new MockOutputStream();
            byte[] apduBytes = new byte[]{0x11, 0x22};

            // execute
            boolean ok = MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);

            // verify
            assertThat(ok, is(true));
            assertThat(mockOutputStream.toByteArray(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 2, 0x11, 0x22, 0x30,
            })));
        }

        @Test
        public void writeToStream_normalApdu() {
            // setup
            MockOutputStream mockOutputStream = new MockOutputStream();
            byte[] apduBytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,};

            // execute
            boolean ok = MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);

            // verify
            assertThat(ok, is(true));
            assertThat(mockOutputStream.toByteArray(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 16,
                    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                    0x11,
            })));
        }

        @Test
        public void writeToStream_largestApdu() {
            // setup
            MockOutputStream mockOutputStream = new MockOutputStream();
            byte[] apduBytes = new byte[254];
            apduBytes[0] = -1;
            apduBytes[100] = 0x01;
            apduBytes[200] = 0x02;
            apduBytes[253] = 0x03;

            // execute
            boolean ok = MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);

            // verify
            assertThat(ok, is(true));
            byte[] actual = mockOutputStream.toByteArray();
            byte[] expected = new byte[254 + 4];
            expected[0] = 0x1; //nad
            expected[1] = 0x0; //pcb
            expected[2] = intToUbyte(254); //len

            expected[3 + 0] = -1;
            expected[3 + 100] = 0x01;
            expected[3 + 200] = 0x02;
            expected[3 + 253] = 0x03;
            expected[3 + 253 + 1] = 0x0;
            assertThat(actual, is(equalTo(expected)));
        }

        @Test
        public void writeToStream_badApduTooSmall() {
            MockOutputStream mockOutputStream = new MockOutputStream();
            byte[] apduBytes = new byte[]{11};

            // execute
            try {
                //noinspection Range
                MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);
                Assert.fail();
            } catch (IllegalArgumentException ignore) {

            }
        }

        @Test
        public void writeToStream_badApduTooLarge() {
            MockOutputStream mockOutputStream = new MockOutputStream();
            byte[] apduBytes = new byte[255];

            // execute
            try {
                //noinspection Range
                MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);
                Assert.fail();
            } catch (IllegalArgumentException ignore) {

            }
        }

        @Test
        public void writeToStream_badStreamThrow0Bytes() {
            // setup
            MockOutputStream mockOutputStream = new MockOutputStream(
                    new MockOutputStreamCommand(MockOutputStreamCommands.THROW_ON_NTH_BYTE, 0));
            byte[] apduBytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,};

            // execute
            boolean ok = MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);

            // verify
            assertThat(ok, is(false));
            assertThat(mockOutputStream.toByteArray(), is(equalTo(new byte[0])));
        }

        @Test
        public void writeToStream_badStreamThrow10Bytes() {
            // setup
            MockOutputStream mockOutputStream = new MockOutputStream(
                    new MockOutputStreamCommand(MockOutputStreamCommands.THROW_ON_NTH_BYTE, 10));
            byte[] apduBytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,};

            // execute
            boolean ok = MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);

            // verify
            assertThat(ok, is(false));
            assertThat(mockOutputStream.toByteArray(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 16, 0, 1, 2, 3, 4, 5, 6,
            })));
        }

        @Test
        public void writeToStream_exactWrite() {
            // setup
            MockOutputStream mockOutputStream = new MockOutputStream(
                    new MockOutputStreamCommand(
                            MockOutputStreamCommands.THROW_ON_NTH_BYTE,
                            16 + 4));
            byte[] apduBytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,};

            // execute
            boolean ok = MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);

            // verify
            assertThat(ok, is(true));
            assertThat(mockOutputStream.toByteArray(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 16,
                    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                    0x11,
            })));
        }

        @Test
        public void writeToStream_badStreamThrowOnFlush() {
            // setup
            MockOutputStream mockOutputStream = new MockOutputStream(
                    new MockOutputStreamCommand(MockOutputStreamCommands.THROW_ON_FLUSH));
            byte[] apduBytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,};

            // execute
            boolean ok = MpiPacket.writeToStream(InterfaceType.MPI, apduBytes, mockOutputStream);

            // verify
            assertThat(ok, is(false));
            assertThat(mockOutputStream.toByteArray(), is(equalTo(new byte[]{
                    NAD_PED, PCB, 16,
                    0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
                    0x11,
            })));
        }
    }
}


