/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.intToUbyte;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.ubyteToInt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import android.support.annotation.NonNull;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

public class ConnectorTest {
    private static final int NAD_PED = 0x1;
    private static final int PCB = 0x0;
    private static final int SW_1_OK = 0x90;
    private static final int SW_2_OK = 0x00;

    /*
        This requires API level 19... let's hope the string lookup also works. It's just
        in test code so we don't care.
    */
    // Charset utf8 = StandardCharsets.UTF_8;
    private static final Charset UTF_8 = Charset.forName("utf-8");

    private StubConnector mStubConnector;
    private UnsolicitedResponseCallback mMockUnsolicitedCallback;
    private ConnectionStateCallback mConnectionStateCallback;
    private MockOutputStream mMockOutputStream;
    private MockInputStream mMockInputStream;

    @Before
    public void setup() throws InterruptedException {

        mMockInputStream = new MockInputStream(new int[]{MockInputStream.PAUSE});
        mMockOutputStream = new MockOutputStream(32);
        mStubConnector = new StubConnector(mMockInputStream, mMockOutputStream);
        mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
        mConnectionStateCallback = mock(ConnectionStateCallback.class);
    }

    @After
    public void teardown() {
    }

    @Test
    public void openAndCloseOk() throws IOException {
        // setup
        // Also tests session, but meh

        // execute
        MpiProtocolSession session = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        boolean connectorIsConnectedBeforeClose = mStubConnector.isConnected();
        boolean sessionIsConnectedBeforeClose = session.isConnected();
        boolean sessionActiveBeforeClose = session.isActive();
        // session.send...
        // session.receive...
        mStubConnector.closeSession();
        boolean connectorIsConnectedAfterClose = mStubConnector.isConnected();
        boolean sessionIsConnectedAfterClose = session.isConnected();
        boolean sessionActiveAfterClose = session.isActive();


        // verify
        assertThat(sessionIsConnectedBeforeClose, is(true));
        assertThat(connectorIsConnectedBeforeClose, is(true));
        assertThat(sessionActiveBeforeClose, is(true));
        assertThat(connectorIsConnectedAfterClose, is(false));
        assertThat(sessionIsConnectedAfterClose, is(false));
        assertThat(sessionActiveAfterClose, is(false));

        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    @Test
    public void throwOnConnect() {
        // setup
        mStubConnector.setThrowOnConnect(true);

        // execute
        try {
            mStubConnector.openSession(mMockUnsolicitedCallback, mConnectionStateCallback);
            Assert.fail();
        } catch (IOException ignore) {

        }

        // verify
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(false));
    }

    @Test
    public void openTwice() throws Exception {
        //setup

        // execute
        MpiProtocolSession session = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        try {
            mStubConnector.openSession(mMockUnsolicitedCallback, mConnectionStateCallback);
            Assert.fail();
        } catch (IOException exception) {
            // verify
            assertThat(exception.getMessage().toLowerCase(),
                    containsString("session already open"));
        }

        // verify
        assertThat(session, is(notNullValue()));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    @Test
    public void sessionCloseSession() throws Exception {
        //setup
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        // execute
        session1.close();

        // verify
        assertThat(session1, is(not(nullValue())));
        assertThat(session1.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    @Test
    public void connectorCloseSession() throws Exception {
        //setup
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        // execute
        mStubConnector.closeSession();

        // verify
        assertThat(session1, is(not(nullValue())));
        assertThat(session1.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    @Test
    public void connectorCloseSessionMultiple() throws Exception {
        //setup
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        // execute
        mStubConnector.closeSession();
        mStubConnector.closeSession();
        mStubConnector.closeSession();

        // verify
        assertThat(session1, is(not(nullValue())));
        assertThat(session1.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    @Test
    public void connectorCloseSessionMultipleMixedClose() throws Exception {
        //setup
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        // execute
        mStubConnector.closeSession();
        session1.close();
        mStubConnector.closeSession();
        session1.close();
        mStubConnector.closeSession();

        // verify
        assertThat(session1, is(not(nullValue())));
        assertThat(session1.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    @Test
    public void sessionCloseSessionMultipleMixedClose() throws Exception {
        //setup
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        // execute
        session1.close();
        mStubConnector.closeSession();
        session1.close();
        mStubConnector.closeSession();

        // verify
        assertThat(session1, is(not(nullValue())));
        assertThat(session1.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    /**
     * This function is the reason the weird function
     * {@link Connector#sessionIsClosing(MpiProtocolSession)} exists, rather than the
     * MpiProtocolSession calling {@link Connector#disconnect(MpiProtocolSession)} directly.
     *
     * It might look weird at first, e.g. if someone opens a session on a connector surely
     * they should close it on the connector, rather than closing the session directly.
     * But nothing outside of the comms package can call session.close.
     *
     * Really it's here because a session can close itself, and it would be tiresome for the
     * user of a Connector if the Connector <i>required</i> the user to closeSession,
     * even though they know it just broke (e.g. because they got an IOException)
     */
    @Test
    public void closeThenReopen() throws Exception {

        //setup

        // execute
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);
        Thread.sleep(30L);
        session1.close();

        MpiProtocolSession session2 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);
        Thread.sleep(30L);
        mStubConnector.closeSession();

        MpiProtocolSession session3 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);
        Thread.sleep(30L);
        session3.close();

        // verify
        assertThat(session1, is(not(sameInstance(session2))));
        assertThat(session1.isActive(), is(false));
        assertThat(session2.isActive(), is(false));
        assertThat(session3.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }

    @Test
    public void sendsDuringClose() throws Exception {
        // setup
        // ---------------------------------------------------------------------

        CommandApdu mockApdu = mock(CommandApdu.class);
        when(mockApdu.getBytes())
                .thenReturn("CLOSE SESSION 1".getBytes())
                .thenReturn("CLOSE SESSION 2".getBytes())
                .thenReturn("CLOSE SESSION 3".getBytes());

        mStubConnector.setUseSessionInDisconnect(MPI, mockApdu);

        // execute
        // ---------------------------------------------------------------------
        mMockInputStream.setData(makeResponseInputStream("SESSION CLOSED 1", "!BLOCK"));
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);
        Thread.sleep(30L);
        session1.close();  //read SESSION CLOSED 1

        byte[] outputBytes = mMockOutputStream.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(outputBytes);
        String command1 = decodeCommandApdu(bais);


        mMockInputStream.setData(makeResponseInputStream("SESSION CLOSED 2", "!BLOCK"));
        MpiProtocolSession session2 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);
        Thread.sleep(30L);
        mStubConnector.closeSession();  //read SESSION CLOSED 2
        outputBytes = mMockOutputStream.toByteArray();
        bais = new ByteArrayInputStream(outputBytes);
        String command2 = decodeCommandApdu(bais);


        mMockInputStream.setData(makeResponseInputStream("SESSION CLOSED 3", "!BLOCK"));
        MpiProtocolSession session3 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);
        Thread.sleep(30L);
        session3.close(); //read SESSION CLOSED 3
        outputBytes = mMockOutputStream.toByteArray();
        bais = new ByteArrayInputStream(outputBytes);
        String command3 = decodeCommandApdu(bais);

        // verify
        // -----------------------------------------------------------------
        assertThat(command1, equalTo("CLOSE SESSION 1"));
        assertThat(command2, equalTo("CLOSE SESSION 2"));
        assertThat(command3, equalTo("CLOSE SESSION 3"));

        assertThat(session1, is(not(sameInstance(session2))));
        assertThat(session1.isActive(), is(false));
        assertThat(session2.isActive(), is(false));
        assertThat(session3.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));

    }


    @Test
    public void sendsDuringCloseInputBreaks() throws Exception {
        // setup
        // ---------------------------------------------------------------------

        CommandApdu mockApdu = mock(CommandApdu.class);
        when(mockApdu.getBytes())
                .thenReturn("CLOSE SESSION 1".getBytes())
                .thenReturn("CLOSE SESSION 2".getBytes())
                .thenReturn("CLOSE SESSION 3".getBytes());

        mStubConnector.setUseSessionInDisconnect(MPI, mockApdu);

        // execute
        // ---------------------------------------------------------------------

        // -- 1
        mMockInputStream.setData(makeResponseInputStream("!PAUSE", "!IO_EXCEPTION"));
        MpiProtocolSession session1 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);
        mMockInputStream.close();
        session1.close();

        byte[] outputBytes = mMockOutputStream.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(outputBytes);
        String command1 = decodeCommandApdu(bais);

        // -- 2
        mMockInputStream.setData(makeResponseInputStream("!PAUSE", "!IO_EXCEPTION"));
        MpiProtocolSession session2 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        mMockInputStream.close();
        mStubConnector.closeSession();

        outputBytes = mMockOutputStream.toByteArray();
        bais = new ByteArrayInputStream(outputBytes);
        String command2 = decodeCommandApdu(bais);

        // -- 3
        mMockInputStream.setData(makeResponseInputStream("!PAUSE", "!IO_EXCEPTION"));
        MpiProtocolSession session3 = mStubConnector.openSession(
                mMockUnsolicitedCallback, mConnectionStateCallback);

        mMockInputStream.close();
        session3.close();

        outputBytes = mMockOutputStream.toByteArray();
        bais = new ByteArrayInputStream(outputBytes);
        String command3 = decodeCommandApdu(bais);

        // verify
        // -----------------------------------------------------------------
        assertThat(command1, equalTo("CLOSE SESSION 1"));
        assertThat(command2, equalTo("CLOSE SESSION 2"));
        assertThat(command3, equalTo("CLOSE SESSION 3"));

        assertThat(session1, is(not(sameInstance(session2))));
        assertThat(session1.isActive(), is(false));
        assertThat(session2.isActive(), is(false));
        assertThat(session3.isActive(), is(false));
        assertThat(mStubConnector.isConnected(), is(false));
        assertThat(mStubConnector.wasDisconnectCalled(), is(true));
    }


    @NonNull
    private static String decodeCommandApdu(ByteArrayInputStream bais) {
        // Note that these are command packets and so require fiddling with
        MpiPacket packet = MpiPacket.readFromStream(bais);
        if (packet == null) throw new AssertionError();

        // nad, pcb, len, <command apdu>, LRC
        byte[] bytes = packet.getBytes();
        byte[] stringBytes = Arrays.copyOfRange(bytes, 3, bytes.length - 1);
        return new String(stringBytes);
    }

    public static int[] makeResponseInputStream(String... strings) {

        int[] intStream = new int[0];
        for (String s : strings) {

            if (s.startsWith("!")) {

                int cmd;
                switch (s) {
                    case "!EOF":
                        cmd = MockInputStream.EOF;
                        break;
                    case "!IO_EXCEPTION":
                        cmd = MockInputStream.THROW_IO_EXCEPTION;
                        break;
                    case "!CUT":
                        cmd = MockInputStream.CUT_STREAM;
                        break;
                    case "!ASSERT":
                        cmd = MockInputStream.ASSERT;
                        break;
                    case "!PAUSE":
                        cmd = MockInputStream.PAUSE;
                        break;
                    case "!BLOCK":
                        cmd = MockInputStream.BLOCK;
                        break;
                    default:
                        throw new AssertionError("Unknown command");
                }
                int writeOffset = intStream.length;
                intStream = Arrays.copyOf(intStream, intStream.length + 1);
                intStream[writeOffset] = cmd;
            } else {

                byte[] bodyBytes = s.getBytes(UTF_8);
                byte[] apdu = Arrays.copyOf(bodyBytes, bodyBytes.length + 2);
                apdu[bodyBytes.length] = intToUbyte(SW_1_OK);
                apdu[bodyBytes.length + 1] = intToUbyte(SW_2_OK);

                MpiPacket packet = new MpiPacket(NAD_PED, PCB, apdu);
                byte[] packetBytes = packet.getBytes();

                // System.out.println(s + " became:" + HexUtil.bytesToHexStrings(packetBytes));

                // grow intStream to accommodate new packet.
                int writeOffset = intStream.length;
                intStream = Arrays.copyOf(intStream, intStream.length + packetBytes.length);
                for (byte b : packetBytes) {
                    intStream[writeOffset++] = ubyteToInt(b);
                }
            }
        }
        return intStream;
    }
}
