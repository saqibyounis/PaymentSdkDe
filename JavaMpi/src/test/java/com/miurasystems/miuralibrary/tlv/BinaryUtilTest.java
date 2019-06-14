/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import com.miurasystems.miuralibrary.Pair;

import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;


/*
    re: @SuppressWarnings("unchecked")
    Java is a stupid language and if the following code is used:
        List<Pair<Integer, String>> data = Arrays.asList( new Pair<Integer,String>(1,"2")
    this will cause a compiler warning:
        "unchecked generic array creation for varargs parameter"
    There are various other ways to make a list of generic-things, but they're verbose, so just
    shut the warning up.
*/
@SuppressWarnings("unchecked")
public class BinaryUtilTest {

    @Test
    public void parseBinaryStringBytes() throws Exception {
        List<Pair<byte[], String>> data = Arrays.asList(
            new Pair<byte[], String>(null, ""),
            new Pair<byte[], String>(new byte[0], ""),
            new Pair<byte[], String>(new byte[]{0x0, 0x1, 0x2}, "000000000000000100000010"),
            new Pair<byte[], String>(new byte[]{127}, "01111111"),
            new Pair<byte[], String>(new byte[]{-128}, "10000000"),
            new Pair<byte[], String>(new byte[]{-32}, "11100000"),
            new Pair<byte[], String>(new byte[]{-1}, "11111111")
        );
        for (Pair<byte[], String> pair : data) {
            String s = BinaryUtil.parseBinaryString(pair.first);
            assertThat(s, is(equalTo(pair.second)));
        }
    }

    @Test
    public void parseBinaryStringInt() throws Exception {
        List<Pair<Integer, String>> data = Arrays.asList(
            new Pair<Integer, String>(0, "00000000"),
            new Pair<Integer, String>(1, "00000001"),
            new Pair<Integer, String>(0x5A, "01011010"),
            new Pair<Integer, String>(127, "01111111"),
            new Pair<Integer, String>(128, "10000000"),
            new Pair<Integer, String>(255, "11111111"),
            new Pair<Integer, String>(256, "00000000"),
            new Pair<Integer, String>(257, "00000001"),
            new Pair<Integer, String>(65535, "11111111"),
            new Pair<Integer, String>(65536, "00000000"),
            new Pair<Integer, String>(2147483647, "11111111"),

            new Pair<Integer, String>(-2147483648, "00000000"),
            new Pair<Integer, String>(-65536, "00000000"),
            new Pair<Integer, String>(-65535, "00000001"),
            new Pair<Integer, String>(-257, "11111111"),
            new Pair<Integer, String>(-256, "00000000"),
            new Pair<Integer, String>(-255, "00000001"),
            new Pair<Integer, String>(-128, "10000000"),
            new Pair<Integer, String>(-127, "10000001"),
            new Pair<Integer, String>(-1, "11111111")
        );
        for (Pair<Integer, String> pair : data) {
            String s = BinaryUtil.parseBinaryString(pair.first);
            assertThat(s, is(equalTo(pair.second)));
        }
    }

    @Test
    public void intToUbyte() throws Exception {
        List<Pair<Integer, Byte>> data = Arrays.asList(
            new Pair<Integer, Byte>(0, (byte) 0x00),
            new Pair<Integer, Byte>(1, (byte) 0x01),
            new Pair<Integer, Byte>(0x5A, (byte) 0x5A),
            new Pair<Integer, Byte>(127, (byte) 0x7f),
            new Pair<Integer, Byte>(128, (byte) 0x80),
            new Pair<Integer, Byte>(255, (byte) 0xff)
        );
        for (Pair<Integer, Byte> pair : data) {
            byte s = BinaryUtil.intToUbyte(pair.first);
            assertThat(s, is(equalTo(pair.second)));
        }
    }

    @Test
    public void intToUbyte_bad() throws Exception {
        List<Integer> data = Arrays.asList(
            256,
            257,
            65535,
            65536,
            0x80000000,
            2147483647,
            -2147483648,
            -65536,
            -65535,
            -257,
            -256,
            -255,
            -128,
            -127,
            -1
        );
        for (int s : data) {
            //noinspection ErrorNotRethrown
            try {
                BinaryUtil.intToUbyte(s);
                Assert.fail();
            } catch (AssertionError ignore) {

            }
        }
    }

    @Test
    public void ubyteToInt() throws Exception {
        List<Pair<Byte, Integer>> data = Arrays.asList(
            new Pair<Byte, Integer>((byte) 0, 0x00),
            new Pair<Byte, Integer>((byte) 1, 0x01),
            new Pair<Byte, Integer>((byte) 0x5A, 0x5a),
            new Pair<Byte, Integer>((byte) 127, 0x7f),
            new Pair<Byte, Integer>((byte) -128, 0x80),
            new Pair<Byte, Integer>((byte) -127, 0x81),
            new Pair<Byte, Integer>((byte) -1, 0xff),
            new Pair<Byte, Integer>((byte) 0xff, 0xff)
        );
        for (Pair<Byte, Integer> pair : data) {
            Integer s = BinaryUtil.ubyteToInt(pair.first);
            assertThat(s, is(equalTo(pair.second)));
        }
    }


    @Test
    public void parseHexStringByte() throws Exception {

        List<Pair<Byte, String>> data = Arrays.asList(
            new Pair<Byte, String>((byte) 0, "00"),
            new Pair<Byte, String>((byte) 1, "01"),
            new Pair<Byte, String>((byte) 0x5A, "5a"),
            new Pair<Byte, String>((byte) 127, "7f"),
            new Pair<Byte, String>((byte) -128, "80"),
            new Pair<Byte, String>((byte) -127, "81"),
            new Pair<Byte, String>((byte) -1, "ff")
        );
        for (Pair<Byte, String> pair : data) {
            String s = BinaryUtil.parseHexString(pair.first);
            assertThat(s, is(equalTo(pair.second)));
        }
    }

    @Test
    public void parseHexStringBytes() throws Exception {
        List<Pair<byte[], String>> data = Arrays.asList(
            new Pair<byte[], String>(null, ""),
            new Pair<byte[], String>(new byte[0], ""),
            new Pair<byte[], String>(new byte[]{0x0, 0x1, 0x2, -2}, "000102fe"),
            new Pair<byte[], String>(new byte[]{0}, "00"),
            new Pair<byte[], String>(new byte[]{1}, "01"),
            new Pair<byte[], String>(new byte[]{0x5A}, "5a"),
            new Pair<byte[], String>(new byte[]{127}, "7f"),
            new Pair<byte[], String>(new byte[]{-128}, "80"),
            new Pair<byte[], String>(new byte[]{-127}, "81"),
            new Pair<byte[], String>(new byte[]{-1}, "ff")
        );
        for (Pair<byte[], String> pair : data) {
            String s = BinaryUtil.parseHexString(pair.first);
            assertThat(s, is(equalTo(pair.second)));
        }
    }

    @Test
    public void parseHexStringInt() throws Exception {

        List<Pair<Integer, String>> data = Arrays.asList(
            new Pair<Integer, String>(0, "00"),
            new Pair<Integer, String>(1, "01"),
            new Pair<Integer, String>(0x5A, "5a"),
            new Pair<Integer, String>(127, "7f"),
            new Pair<Integer, String>(128, "80"),
            new Pair<Integer, String>(255, "ff"),
            new Pair<Integer, String>(256, "00"),
            new Pair<Integer, String>(257, "01"),
            new Pair<Integer, String>(65535, "ff"),
            new Pair<Integer, String>(65536, "00"),
            new Pair<Integer, String>(2147483647, "ff"),
            new Pair<Integer, String>(-2147483648, "00"),
            new Pair<Integer, String>(-65536, "00"),
            new Pair<Integer, String>(-65535, "01"),
            new Pair<Integer, String>(-257, "ff"),
            new Pair<Integer, String>(-256, "00"),
            new Pair<Integer, String>(-255, "01"),
            new Pair<Integer, String>(-128, "80"),
            new Pair<Integer, String>(-127, "81"),
            new Pair<Integer, String>(-1, "ff")
        );
        for (Pair<Integer, String> pair : data) {
            String s = BinaryUtil.parseHexString(pair.first);
            assertThat(s, is(equalTo(pair.second)));
        }
    }

    @Test
    public void parseHexBinaryStringNull() throws Exception {
        try {
            String s = null;
            BinaryUtil.parseHexBinary(s);
            Assert.fail();
        } catch (NullPointerException ignored) {
        }
    }

    @Test
    public void parseHexBinaryString() throws Exception {
        List<Pair<String, byte[]>> data = Arrays.asList(
            new Pair<String, byte[]>("", new byte[0]),
            new Pair<String, byte[]>("000102fe", new byte[]{0x0, 0x1, 0x2, -2}),
            new Pair<String, byte[]>("00", new byte[]{0}),
            new Pair<String, byte[]>("01", new byte[]{1}),
            new Pair<String, byte[]>("5a", new byte[]{0x5A}),
            new Pair<String, byte[]>("7f", new byte[]{127}),
            new Pair<String, byte[]>("80", new byte[]{-128}),
            new Pair<String, byte[]>("81", new byte[]{-127}),
            new Pair<String, byte[]>("ff", new byte[]{-1})
        );
        for (Pair<String, byte[]> pair : data) {
            byte[] b = BinaryUtil.parseHexBinary(pair.first);
            assertThat(b, is(equalTo(pair.second)));
        }
    }

    @Test
    public void parseHexBinaryDescription() throws Exception {
        List<Pair<Description, byte[]>> data = Arrays.asList(
            new Pair<Description, byte[]>(Description.Date, new byte[]{(byte) 0x9a}),
            new Pair<Description, byte[]>(Description.Time, new byte[]{(byte) 0x9f, (byte) 0x21}),
            new Pair<Description, byte[]>(Description.Battery_Status,
                new byte[]{(byte) 0xdf, (byte) 0xa2, (byte) 0x0a}),
            new Pair<Description, byte[]>(Description.Transaction_Information_Status_sale,
                new byte[]{(byte) 0x9c, 0x00}),

            //new Pair<Description, byte[]>(Description.UNKNOWN, new byte[] {-1, -1, -1, -1} )
            new Pair<Description, byte[]>(Description.UNKNOWN, new byte[]{}),

            new Pair<Description, byte[]>(null, null)
        );
        for (Pair<Description, byte[]> pair : data) {
            Description first = pair.first;
            byte[] b = BinaryUtil.parseHexBinary(first);
            assertThat(b, is(equalTo(pair.second)));
        }
    }

    @Test
    public void parseHexBinaryDescriptionList() throws Exception {

        List<Pair<List<Description>, byte[]>> data = Arrays.asList(
            new Pair<List<Description>, byte[]>(
                Arrays.asList(Description.Date, Description.Time, Description.UNKNOWN),
                new byte[]{(byte) 0x9a, (byte) 0x9f, (byte) 0x21}),

            new Pair<List<Description>, byte[]>(
                Collections.singletonList(Description.Battery_Status),
                new byte[]{(byte) 0xdf, (byte) 0xa2, (byte) 0x0a}),

            new Pair<List<Description>, byte[]>(
                Arrays.asList(Description.UNKNOWN, Description.UNKNOWN, Description.UNKNOWN),
                new byte[]{}),

            new Pair<List<Description>, byte[]>(null, null)

        );
        for (Pair<List<Description>, byte[]> pair : data) {
            List<Description> first = pair.first;
            byte[] b = BinaryUtil.parseHexBinary(first);
            assertThat(b, is(equalTo(pair.second)));
        }
    }

    @Test
    public void parseHexBinaryDescriptionListNPE() throws Exception {
        List<Description> descriptions = Arrays.asList(Description.Date, null);
        try {
            byte[] b = BinaryUtil.parseHexBinary(descriptions);
            Assert.fail();
        } catch (NullPointerException ignore) {
        }
    }

    @Test
    public void getBCD_Good() {
        List<GetByteRepGroup> groups = Arrays.asList(
                new GetByteRepGroup(0, 1, new byte[]{0x0}),
                new GetByteRepGroup(0, 2, new byte[]{0x0, 0x0}),
                new GetByteRepGroup(1, 2, new byte[]{0x0, 0x1}),
                new GetByteRepGroup(5, 1, new byte[]{0x05}),
                new GetByteRepGroup(5, 6, new byte[]{0x0, 0x0, 0x0, 0x0, 0x0, 0x05}),
                new GetByteRepGroup(10, 2, new byte[]{0x0, 0x10}),
                new GetByteRepGroup(10, 6, new byte[]{0x0, 0x0, 0x0, 0x0, 0x0, 0x10}),
                new GetByteRepGroup(500, 2, new byte[]{0x5, 0x00}),
                new GetByteRepGroup(12345, 6, new byte[]{0x00, 0x00, 0x00, 0x01, 0x23, 0x45}),
                new GetByteRepGroup(65535, 3, new byte[]{0x6, 0x55, 0x35}),
                new GetByteRepGroup(987654321, 5, new byte[]{0x09, (byte) 0x87, 0x65, 0x43, 0x21}),
                new GetByteRepGroup(2147483647, 5, new byte[]{0x21, 0x47, 0x48, 0x36, 0x47})
        );

        for (GetByteRepGroup data : groups) {
            byte[] actual = BinaryUtil.getBCD(data.value, data.numBytes);
            MatcherAssert.assertThat(actual, is(equalTo(data.expectedResult)));
        }
    }

    @Test
    public void getBCD_BadArguments() {

        List<GetByteRepGroup> groups = Arrays.asList(
                new GetByteRepGroup(5, -5, null),
                new GetByteRepGroup(5, 0, null),
                new GetByteRepGroup(1122334455, 3, null),
                new GetByteRepGroup(-1, 1, null),
                new GetByteRepGroup(-2147483648, 6, null)
        );

        for (GetByteRepGroup data : groups) {
            try {
                byte[] actual = BinaryUtil.getBCD(data.value, data.numBytes);
                Assert.fail();
            } catch (IllegalArgumentException ignore) {

            }
        }
    }

    private class GetByteRepGroup {
        private final byte[] expectedResult;
        private final int value;
        private final int numBytes;

        private GetByteRepGroup(int value, int numBytes, byte[] expectedResult) {
            this.expectedResult = expectedResult;
            this.value = value;
            this.numBytes = numBytes;
        }
    }

}
