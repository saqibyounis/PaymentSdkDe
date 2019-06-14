/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.miurasystems.examples.connectors.ClientSocketConnector;
import com.miurasystems.examples.connectors.JavaBluetoothConnector;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Locale;

import javax.bluetooth.RemoteDevice;

public class WifiStampedeTest {

    private static final Charset UTF_8 = Charset.forName("utf-8");
    private static final String[] BULL_FIELDS = {
            // @formatter:off
        "|       |            " +
        " \\ /#\\ /             " +
        "  |o o|              " +
        "   \\_/               ",

        " |       |           " +
        "  \\ /#\\ /            " +
        "   |o o|             " +
        "    \\_/              ",

        "  |       |          " +
        "   \\ /#\\ /           " +
        "E   |o o|            " +
        "     \\_/             ",

        "   |       |         " +
        "S   \\ /#\\ /          " +
        "CE   |o o|           " +
        "      \\_/            ",

        "    |       |        " +
        "TS   \\ /#\\ /         " +
        "ACE   |o o|          " +
        "       \\_/           ",

        "     |       |       " +
        "NTS   \\ /#\\ /        " +
        "FACE   |o o|         " +
        "        \\_/          ",

        "A     |       |      " +
        "ENTS   \\ /#\\ /       " +
        "RFACE   |o o|        " +
        "         \\_/         ",

        "RA     |       |     " +
        "MENTS   \\ /#\\ /      " +
        "ERFACE   |o o|       " +
        "T         \\_/        ",

        "URA     |       |    " +
        "YMENTS   \\ /#\\ /     " +
        "TERFACE   |o o|      " +
        "ST         \\_/       ",

        "IURA     |       |   " +
        "AYMENTS   \\ /#\\ /    " +
        "NTERFACE   |o o|     " +
        "EST         \\_/      ",

        "MIURA     |       |  " +
        "PAYMENTS   \\ /#\\ /   " +
        "INTERFACE   |o o|    " +
        "TEST         \\_/     ",

        " MIURA     |       | " +
        " PAYMENTS   \\ /#\\ /  " +
        " INTERFACE   |o o|   " +
        " TEST         \\_/    ",

        "  MIURA     |       |" +
        "  PAYMENTS   \\ /#\\ / " +
        "  INTERFACE   |o o|  " +
        "  TEST         \\_/   ",

        "|  MIURA     |       " +
        "   PAYMENTS   \\ /#\\ /" +
        "   INTERFACE   |o o| " +
        "   TEST         \\_/  ",

        " |  MIURA     |      " +
        "/   PAYMENTS   \\ /#\\ " +
        "    INTERFACE   |o o|" +
        "    TEST         \\_/ ",

        "  |  MIURA     |     " +
        " /   PAYMENTS   \\ /#\\" +
        "|    INTERFACE   |o o" +
        "     TEST         \\_/",

        "   |  MIURA     |    " +
        "\\ /   PAYMENTS   \\ /#" +
        "o|    INTERFACE   |o " +
        "/     TEST         \\_",

        "    |  MIURA     |   " +
        "#\\ /   PAYMENTS   \\ /" +
        " o|    INTERFACE   |o" +
        "_/     TEST         \\",

        "     |  MIURA     |  " +
        "/#\\ /   PAYMENTS   \\ " +
        "o o|    INTERFACE   |" +
        "\\_/     TEST         ",

        "      |  MIURA     | " +
        " /#\\ /   PAYMENTS   \\" +
        "|o o|    INTERFACE   " +
        " \\_/     TEST        ",

        "       |  MIURA     |" +
        "\\ /#\\ /   PAYMENTS   " +
        " |o o|    INTERFACE  " +
        "  \\_/     TEST       ",

        "|       |  MIURA     " +
        " \\ /#\\ /   PAYMENTS  " +
        "  |o o|    INTERFACE " +
        "   \\_/     TEST      ",

        " |       | MIURA     " +
        "  \\ /#\\ /  PAYMENTS  " +
        "   |o o|   INTERFACE " +
        "    \\_/    TEST      ",

        "  |       |MIURA     " +
        "   \\ /#\\ / PAYMENTS  " +
        "    |o o|  INTERFACE " +
        "     \\_/   TEST      ",

        "   |       |MIURA    " +
        "    \\ /#\\ /PAYMENTS  " +
        "     |o o| INTERFACE " +
        "      \\_/  TEST      ",

        "    |       |MIURA   " +
        "     \\ /#\\ /PAYMENTS " +
        "      |o o|INTERFACE " +
        "       \\_/ TEST      ",

        "     |       |MIURA  " +
        "      \\ /#\\ /PAYMENTS" +
        "       |o o|INTERFACE" +
        "        \\_/TEST      ",

        "      |       |MIURA " +
        "       \\ /#\\ /PAYMENT" +
        "        |o o|INTERFAC" +
        "         \\_/TEST     ",

        "       |       |MIURA" +
        "        \\ /#\\ /PAYMEN" +
        "         |o o|INTERFA" +
        "          \\_/TEST    ",

        "        |       |MIUR" +
        "         \\ /#\\ /PAYME" +
        "          |o o|INTERF" +
        "           \\_/TEST   ",

        "         |       |MIU" +
        "          \\ /#\\ /PAYM" +
        "           |o o|INTER" +
        "            \\_/TEST  ",

        "          |       |MI" +
        "           \\ /#\\ /PAY" +
        "            |o o|INTE" +
        "             \\_/TEST ",

        "           |       |M" +
        "            \\ /#\\ /PA" +
        "             |o o|INT" +
        "              \\_/TEST",

        "            |       |" +
        "             \\ /#\\ /P" +
        "              |o o|IN" +
        "               \\_/TES",

        "             |       " +
        "              \\ /#\\ /" +
        "               |o o|I" +
        "                \\_/TE",

        "              |      " +
        "               \\ /#\\ " +
        "                |o o|" +
        "                 \\_/T",

        "               |     " +
        "                \\ /#\\" +
        "                 |o o" +
        "                  \\_/",

        "               |     " +
        "                \\ /#\\" +
        "                 |o o" +
        "                  \\_/",

        "               |     " +
        "                \\ /#\\" +
        "                 |- o" +
        "                  \\_/",

        "               |     " +
        "                \\ /#\\" +
        "                 |o o" +
        "                  \\_/",

    // @formatter:on
    };

    /**
     * The device running this test needs to be paired with miura device 515.
     */
    @SuppressWarnings("JUnitTestMethodWithNoAssertions")
    @Ignore("Requires a specific PED to be paired")
    @Test
    public void bluetoothBullTest() throws Exception {

        System.out.println("bluetoothBullTest");

        RemoteDevice device = JavaBluetoothConnector.findDeviceForName("Miura 515");
        assert device != null;
        String url = JavaBluetoothConnector.getUrlForDevice(device);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        for (int i = 0; i < 3; i++) {
            doBullTest(connector, "wifi " + i);
        }
    }

    /**
     * The device running this test needs to be on the miura-wrt-belk network,
     * and a PED needs to be on and listening on 192.168.0.96 ...
     */
    @SuppressWarnings("JUnitTestMethodWithNoAssertions")
    @Ignore("Requires a specific PED and ip address")
    @Test
    public void wifiBullTest() throws Exception {

        System.out.println("wifiBullTest");

        InetAddress addr = InetAddress.getByName("192.168.0.96");
        ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);

        for (int i = 0; i < 3; i++) {
            doBullTest(connector, "wifi " + i);
        }
    }

    private static void doBullTest(Connector connector, String what) throws IOException {
        UnsolicitedResponseCallback unsolicitedResponseCallback =
                new UnsolicitedResponseCallback() {
                    @Override
                    public void handle(PollerMessage msg) {
                        if (msg.response == null) {
                            throw new AssertionError("msg.response == null?");
                        }
                        System.out.printf(
                                "UnsolicitedResponseCallback! %d response= %s %s %n",
                                msg.solicitedResponseId, msg.response,
                                Arrays.toString(msg.response.getBody()));
                    }
                };
        ConnectionStateCallback connectionStateCallback = new ConnectionStateCallback() {
            @Override
            public void handle(boolean state) {
                System.out.printf("connectionStateCallback! %b%n", state);
            }
        };

        System.out.println("connector.openSession for " + what + " ...");
        MpiProtocolSession session = connector.openSession(
                unsolicitedResponseCallback, connectionStateCallback);
        if (!session.isConnected()) {
            throw new AssertionError("!session.isConnected()?");
        }

        System.out.println("Starting bull test for " + what + " ...");
        try {
            ResponseMessage response;

            // reset device
            session.sendCommandAPDU(MPI, new CommandApdu(0xd0, 0x00, 0x00, 0x00));
            response = session.receiveResponseTimeout(MPI, 10000L);
            if (response == null) throw new AssertionError("ResetDevice response timed out");
            if (!response.isSuccess()) {
                throw new AssertionError(String.format(Locale.ENGLISH,
                        "ResetDevice SW12 not ok? 0x%08d %n", response.getStatusCode()));
            }
            System.out.println("ResetDevice worked! " + Arrays.toString(response.getBody()));

            // Display the bull!
            for (String display : BULL_FIELDS) {
                byte[] data = display.getBytes(UTF_8);
                session.sendCommandAPDU(MPI, new CommandApdu(0xD2, 0x01, 0x00, 0x01, data));

                response = session.receiveResponseTimeout(MPI, 500L);
                if (response == null) throw new AssertionError("DisplayText response timed out");
                assertThat(response.isSuccess(), is(true));

                //noinspection BusyWait
                // Thread.sleep(16L);
            }

            System.out.println("Test finished!");
        } catch (IOException | InterruptedException e) {
            System.out.println("IOException!");
            e.printStackTrace();
            throw new AssertionError(e);
        } finally {
            System.out.println("finally");
            session.close();
        }
    }

}

