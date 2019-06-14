/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import com.miurasystems.miuralibrary.Pair;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

@RunWith(Enclosed.class)
@SuppressWarnings("unchecked")
public class HexUtilTest {

    public static class hexToStringTest {
        @Test
        public void hexToString() throws Exception {
            List<Pair<String, String>> data = Arrays.asList(
                new Pair<String, String>("", ""),
                new Pair<String, String>("00", "\0"),
                new Pair<String, String>("01", "\u0001"),
                new Pair<String, String>("0102", "\u0001\u0002"),
                new Pair<String, String>("1a50", "\u001AP"), //\u0050 = P
                new Pair<String, String>("202122", " !\""),
                new Pair<String, String>("48656C6C6F20576F726C6421", "Hello World!"),

                // note: These aren't ascii
                new Pair<String, String>("7EffFF7e", "~ÿÿ~")
                //new Pair<String, String>("3a33a83ab", "ΣΨΫ")
                //new Pair<String, String>("3a33a83ab110", "ΣΨΫĐ")
            );
            for (Pair<String, String> pair : data) {
                String result = HexUtil.hexToString(pair.first);
                assertThat(result, is(equalTo(pair.second)));
            }
        }

        @Test
        public void oddNumberOfChars() throws Exception {
            try {
                HexUtil.hexToString("4F44440"); // "Odd<0>"
                Assert.fail();
            } catch (StringIndexOutOfBoundsException ignore) {
            }
        }

        @Test
        public void prefix0x() throws Exception {
            try {
                HexUtil.hexToString("0x01");
                Assert.fail();
            } catch (NumberFormatException ignore) {
            }
        }

        @Test
        public void alpha() throws Exception {
            try {
                HexUtil.hexToString("ROFL");
                Assert.fail();
            } catch (NumberFormatException ignore) {
            }
        }

        @Test
        public void nullInput() throws Exception {
            try {
                HexUtil.hexToString(null);
                Assert.fail();
            } catch (NullPointerException ignore) {
            }
        }
    }

    public static class asciiToHexTest {

        @Test
        public void asciiToHex() {
            List<Pair<String, String>> data = Arrays.asList(
                new Pair<String, String>("", ""),
                new Pair<String, String>("\0", "0"),
                new Pair<String, String>("\u0001", "1"),
                new Pair<String, String>("\u0001\u0002", "12"),
                new Pair<String, String>("\u001A\u0050", "1a50"), //\u0050 = P
                new Pair<String, String>("01234", "3031323334"),
                new Pair<String, String>(" !\"", "202122"),
                new Pair<String, String>("Hello World!", "48656c6c6f20576f726c6421"),

                // note: These aren't ascii
                new Pair<String, String>("~ÿÿ~", "7effff7e"),
                new Pair<String, String>("ΣΨΫĐ", "3a33a83ab110")
            );
            for (Pair<String, String> pair : data) {
                String result = HexUtil.asciiToHex(pair.first);
                assertThat(result, is(equalTo(pair.second)));
            }
        }

        @Test
        public void nullInput() throws Exception {
            try {
                HexUtil.asciiToHex(null);
                Assert.fail();
            } catch (NullPointerException ignore) {
            }
        }
    }

    public static class hexToASCIITest {
        @Test
        public void hexToASCII() throws Exception {
            List<Pair<String, String>> data = Arrays.asList(
                new Pair<String, String>("", ""),
                new Pair<String, String>("00", "\0"),
                new Pair<String, String>("01", "\u0001"),
                new Pair<String, String>("0102", "\u0001\u0002"),
                new Pair<String, String>("202122", " !\""),
                new Pair<String, String>("48656C6C6F20576F726C6421", "Hello World!"),

                // note: These aren't ascii
                new Pair<String, String>("7EffFF7e", "~ÿÿ~")
                //new Pair<String, String>("3a33a83ab", "ΣΨΫ")
                //new Pair<String, String>("3a33a83ab110", "ΣΨΫĐ")
            );
            for (Pair<String, String> pair : data) {
                String result = HexUtil.hexToASCII(pair.first);
                assertThat(result, is(equalTo(pair.second)));
            }
        }

        @Test
        public void odd() throws Exception {
            try {
                HexUtil.hexToASCII("4F44440"); // "Odd<0>"
                Assert.fail();
            } catch (StringIndexOutOfBoundsException ignore) {
            }
        }

        @Test
        public void prefix0x() throws Exception {
            try {
                HexUtil.hexToASCII("0x01");
                Assert.fail();
            } catch (NumberFormatException ignore) {
            }
        }

        @Test
        public void alpha() throws Exception {
            try {
                HexUtil.hexToASCII("ROFL");
                Assert.fail();
            } catch (NumberFormatException ignore) {
            }
        }

        @Test
        public void nullInput() throws Exception {
            try {
                HexUtil.hexToASCII(null);
                Assert.fail();
            } catch (NullPointerException ignore) {
            }
        }
    }

    public static class hexToASCIIToHexTest {
        @Test
        public void work() {
            List<String> data = Arrays.asList(
                "",
                //"0",
                //"00",
                //"01",
                //"1",
                //"0102",
                "202122",
                //"7EffFF7e",
                "48656c6c6f20576f726c6421",
                "7effff7e",
                //"3a33a83ab"
                "3a33a83ab110"
            );
            for (String startHex : data) {
                String asciiString = HexUtil.hexToASCII(startHex);
                String endHex = HexUtil.asciiToHex(asciiString);
                assertThat(endHex, is(equalTo(startHex)));
            }
        }

        @Test
        public void crash() {
            List<String> data = Arrays.asList(
                "0",
                "1",
                "3a33a83ab"
            );
            for (String startHex : data) {
                try {
                    HexUtil.asciiToHex(HexUtil.hexToASCII(startHex));
                    Assert.fail(startHex);
                } catch (StringIndexOutOfBoundsException ignore) {

                }
            }
        }

        @Test
        public void dontMatch() {
            List<String> data = Arrays.asList(
                "00",
                "01",
                "0102",
                "7EffFF7e" // <-- just a caps problem.
            );
            for (String startHex : data) {
                String endHex = HexUtil.asciiToHex(HexUtil.hexToASCII(startHex));
                assertThat(endHex, is(not(equalTo(startHex))));
            }
        }
    }

    public static class asciiToHexToASCIITest {
        @Test
        public void work() {
            List<String> data = Arrays.asList(
                "",
                "\u001A\u0050",
                "1",
                "a",
                "ab",
                "abc",
                "00",
                "01",
                "12",
                " !\"",
                "~ÿÿ~",
                "0102",
                "1a50",
                "01234",
                "202122",
                "Hello World!",
                "7effff7e",
                "7EffFF7e",
                "3a33a83ab",
                "3031323334",
                "3a33a83ab110",
                "Hello World!",
                "48656c6c6f20576f726c6421",
                "48656C6C6F20576F726C6421"
            );
            for (String startAscii : data) {
                String hexString = HexUtil.asciiToHex(startAscii);
                String endAscii = HexUtil.hexToASCII(hexString);
                assertThat(endAscii, is(equalTo(startAscii)));
            }
        }

        @Test
        public void crash() {
            List<String> data = Arrays.asList(
                "\0",
                "\u0001",
                "ΣΨΫ"
            );
            for (String startAscii : data) {
                try {
                    String hexString = HexUtil.asciiToHex(startAscii);
                    String endAscii = HexUtil.hexToASCII(hexString);
                    Assert.fail(startAscii);
                } catch (StringIndexOutOfBoundsException ignore) {

                }
            }
        }

        @Test
        public void dontMatch() {
            List<String> data = Arrays.asList(
                "\u0001\u0002",
                "ΣΨΫĐ"
            );
            for (String startAscii : data) {
                String hexString = HexUtil.asciiToHex(startAscii);
                String endAscii = HexUtil.hexToASCII(hexString);
                assertThat(endAscii, is(not(equalTo(startAscii))));
            }
        }
    }

    public static class bytesToHexStringsTest {

        @Test
        public void nullInput() throws Exception {
            try {
                HexUtil.bytesToHexStrings(null);
                Assert.fail();
            } catch (NullPointerException ignored) {
            }
        }

        @Test
        public void bytesToHexStrings() throws Exception {
            List<Pair<byte[], String>> data = Arrays.asList(
                new Pair<byte[], String>(new byte[0], ""),
                new Pair<byte[], String>(new byte[]{0x0, 0x1, 0x2, -2}, "000102FE"),
                new Pair<byte[], String>(new byte[]{0}, "00"),
                new Pair<byte[], String>(new byte[]{1}, "01"),
                new Pair<byte[], String>(new byte[]{0x5A}, "5A"),
                new Pair<byte[], String>(new byte[]{127}, "7F"),
                new Pair<byte[], String>(new byte[]{-128}, "80"),
                new Pair<byte[], String>(new byte[]{-127}, "81"),
                new Pair<byte[], String>(new byte[]{-1}, "FF")
            );
            for (Pair<byte[], String> pair : data) {
                String s = HexUtil.bytesToHexStrings(pair.first);
                assertThat(s, is(equalTo(pair.second)));
            }
        }
    }

    public static class bytesToStringTest {
        @Test
        public void bytesToString() throws Exception {
            List<Pair<byte[], String>> data = Arrays.asList(
                new Pair<byte[], String>(null, ""),
                new Pair<byte[], String>(new byte[0], ""),


                new Pair<byte[], String>(new byte[]{0x00}, "\0"),
                new Pair<byte[], String>(new byte[]{0x01}, "\u0001"),
                new Pair<byte[], String>(new byte[]{0x01, 0x2}, "\u0001\u0002"),
                new Pair<byte[], String>(new byte[]{0x1a, 0x50}, "\u001AP"),
                new Pair<byte[], String>(new byte[]{0x4D, 0x53}, "MS"),
                new Pair<byte[], String>(new byte[]{0x20, 0x21, 0x22}, " !\""),
                new Pair<byte[], String>(
                    new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x20,
                        0x57, 0x6F, 0x72, 0x6C, 0x64, 0x21},
                    "Hello World!"),

                new Pair<byte[], String>(
                    new byte[]{0x7e, (byte) 0xff, (byte) 0xff, 0x7e}, "~ÿÿ~")
            );
            for (Pair<byte[], String> pair : data) {
                String result = HexUtil.bytesToString(pair.first);
                assertThat(result, is(equalTo(pair.second)));
            }
        }
    }

}
