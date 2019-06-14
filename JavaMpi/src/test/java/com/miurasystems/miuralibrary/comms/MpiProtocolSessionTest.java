/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import static com.miurasystems.miuralibrary.comms.MpiProtocolSession.makeMpiProtocolSession;
import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;
import static com.miurasystems.miuralibrary.enums.InterfaceType.RPI;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.intToUbyte;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.ubyteToInt;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.ignoreStubs;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import com.miurasystems.miuralibrary.comms.PollerStatusCallback.PollerStatus;
import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;


@RunWith(Enclosed.class)
public class MpiProtocolSessionTest {

    private static final int ID_NO_REPLIES_YET = PollerMessage.INITIAL_RESPONSE_ID;
    private static final int ID_FIRST_REPLY = 0;
    private static final int ID_FIRST_COMMAND = 0;

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

    /** exists because otherwise `(byte) 1` would be spammed everywhere */
    public static byte[] byteArray(int... ints) {
        byte[] bytes = new byte[ints.length];
        for (int i = 0; i < ints.length; i++) {
            bytes[i] = (byte) ints[i];
        }
        return bytes;
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

    @RunWith(JUnitParamsRunner.class)
    public static class PollerStatusTest {

        private StubConnector mConnector;
        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;


        @Before
        public void setup() throws Exception {
            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }

            mConnector = new StubConnector(new MockInputStream(), new MockOutputStream());
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();
        }

        @After
        public void teardown() {
            mSession.close();
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));
        }

        @SuppressWarnings("TestMethodWithIncorrectSignature")
        @Test
        @Parameters({
                "Running, true",
                "StoppedQueuePostTimedOut, false",
                "StoppedQueuePostInterrupted, false",
                "StoppedStreamBroken, false",
                "StoppedCallbackError, false",
        })
        public void test(PollerStatus status, boolean expectedIsConnected)
                throws Exception {
            // setup

            // execute
            mSession.updatePollerStatus(status, 6666);
            boolean actualIsConnected = mSession.isConnected();

            // verify
            assertThat(actualIsConnected, is(equalTo(expectedIsConnected)));
        }
    }

    /**
     * These tests are there to ensure the "open" sequence is obeyed properly.
     */
    public static class OpenTest {

        private StubConnector mConnector;
        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;

        @Before
        public void setup() throws Exception {
            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }
            mConnector = new StubConnector();
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);
        }

        @After
        public void teardown() {
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));
        }


        @Test
        public void notConnected() {
            // setup
            // ----------------------------
            // skip mSession.startInputPollerThread();

            // execute
            // ----------------------------
            boolean beforeIsActive = mSession.isActive();
            boolean openedOk = mSession.open();
            boolean afterIsActive = mSession.isActive();

            // verify
            // ---------------------------------------------------------------------
            assertThat(beforeIsActive, is(equalTo(false)));
            assertThat(openedOk, is(equalTo(false)));
            assertThat(afterIsActive, is(equalTo(false)));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void noResponseFromPoller() {
            // setup
            // ---------------------------------------------------------------------

            // execute
            // ---------------------------------------------------------------------
            // skip mSession.startInputPollerThread();
            boolean beforeIsActive = mSession.isActive();
            boolean openedOk = mSession.open();
            boolean afterIsActive = mSession.isActive();

            // verify
            // ---------------------------------------------------------------------
            assertThat(beforeIsActive, is(equalTo(false)));
            assertThat(openedOk, is(equalTo(false)));
            assertThat(afterIsActive, is(equalTo(false)));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void sessionOpensOk() {
            // setup
            // ---------------------------------------------------------------------

            // execute
            // ---------------------------------------------------------------------
            // fake startInputPollerThread by sending message
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            boolean beforeIsActive = mSession.isActive();
            boolean openedOk = mSession.open();
            boolean afterIsActive = mSession.isActive();

            // verify
            // ---------------------------------------------------------------------
            assertThat(beforeIsActive, is(equalTo(false)));
            assertThat(openedOk, is(equalTo(true)));
            assertThat(afterIsActive, is(equalTo(true)));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(0)).handle(false);
        }

        @Test
        public void pollerShutdownBeforeOpen() {
            // setup
            // ---------------------------------------------------------------------
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);

            // execute
            // ---------------------------------------------------------------------
            mSession.updatePollerStatus(PollerStatus.StoppedStreamBroken, ID_NO_REPLIES_YET);
            boolean beforeIsActive = mSession.isActive();
            boolean openedOk = mSession.open();
            boolean afterIsActive = mSession.isActive();

            // verify
            // ---------------------------------------------------------------------
            assertThat(beforeIsActive, is(equalTo(false)));
            assertThat(openedOk, is(equalTo(false)));
            assertThat(afterIsActive, is(equalTo(false)));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void openAfterOpen() {
            // setup
            // ---------------------------------------------------------------------
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);


            // execute
            // ---------------------------------------------------------------------
            boolean statusA = mSession.open();
            boolean beforeIsActive = mSession.isActive();
            boolean statusB = mSession.open();
            boolean afterIsActive = mSession.isActive();

            // verify
            // ---------------------------------------------------------------------
            assertThat(beforeIsActive, is(equalTo(true)));
            assertThat(statusA, is(equalTo(true)));
            assertThat(afterIsActive, is(equalTo(false)));
            assertThat(statusB, is(equalTo(false)));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }
    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({MpiPacket.class})
    public static class CloseTest {

        private StubConnector mConnector;
        private MockInputStream mMockInputStream;
        private MockOutputStream mMockOutputStream;

        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;

        @Before
        public void setup() throws Exception {
            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }

            mMockOutputStream = new MockOutputStream();
            mMockInputStream = new MockInputStream(new int[]{MockInputStream.BLOCK});

            mConnector = new StubConnector(mMockInputStream, mMockOutputStream);
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);
        }

        @After
        public void teardown() {
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));

            assertThat(mConnector.wasDisconnectCalled(), is(true));
        }

        @Test
        public void closeButNotOpen() {

            // setup
            // mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            // mSession.open();

            // execute
            boolean beforeIsActive = mSession.isActive();
            mSession.close();
            boolean afterIsActive = mSession.isActive();

            // verify
            assertThat(beforeIsActive, is(false));
            assertThat(afterIsActive, is(false));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void connectNoPollerOrOpenThenClose() throws IOException {

            // setup
            // mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            // mSession.open();

            // execute
            boolean beforeIsActive = mSession.isActive();
            mSession.close();
            boolean afterIsActive = mSession.isActive();

            // verify
            assertThat(beforeIsActive, is(false));
            assertThat(afterIsActive, is(false));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void connectPollerNoOpenThenClose() throws IOException {

            // setup
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            // mSession.open();

            // execute
            boolean beforeIsActive = mSession.isActive();
            mSession.close();
            boolean afterIsActive = mSession.isActive();

            // verify
            assertThat(beforeIsActive, is(false));
            assertThat(afterIsActive, is(false));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void openThenClose() {

            // setup
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();

            // execute
            boolean beforeIsActive = mSession.isActive();
            mSession.close();
            boolean afterIsActive = mSession.isActive();

            // verify
            assertThat(beforeIsActive, is(true));
            assertThat(afterIsActive, is(false));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void idempotentClose() {

            // setup
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();

            // execute
            boolean beforeIsActive = mSession.isActive();
            mSession.close();
            mSession.close();
            mSession.close();
            mSession.close();
            boolean afterIsActive = mSession.isActive();

            // verify
            assertThat(beforeIsActive, is(true));
            assertThat(afterIsActive, is(false));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void openThenCloseThenOpen() {
            // setup
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);

            // execute
            boolean statusA = mSession.open();
            mSession.close();
            boolean statusB = mSession.open();

            assertThat(statusA, is(true));
            assertThat(statusB, is(false));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void errorsInClose() throws IOException {

            mConnector.setThrowOnDisconnect(true);

            // setup
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();

            // execute
            boolean beforeIsActive = mSession.isActive();
            mSession.close();
            boolean afterIsActive = mSession.isActive();

            // verify
            assertThat(beforeIsActive, is(true));
            assertThat(afterIsActive, is(false));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);

            // duplicated from teardown...
            assertThat(mConnector.wasDisconnectCalled(), is(true));
        }

        @Test
        public void sendAfterClose() throws IOException {

            // setup
            // ---------------------------------------------------------------------
            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();

            CommandApdu mockApdu1 = mock(CommandApdu.class);

            // execute
            // ---------------------------------------------------------------------
            mSession.close();
            try {
                mSession.sendCommandAPDU(MPI, mockApdu1);
                Assert.fail();
            } catch (IOException exception) {
                // verify
                assertThat(exception.getMessage().toLowerCase(), containsString("closed"));
            }
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void connectorSendsDuringClose() throws InterruptedException {
            // setup
            // ---------------------------------------------------------------------
            mockStatic(MpiPacket.class); // for MpiPacket.writeToStream

            final byte[] bytes = "CLOSE".getBytes();
            final InterfaceType nad = MPI;

            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(bytes);
            when(MpiPacket.writeToStream(nad, bytes, mMockOutputStream))
                    .thenReturn(true);
            mConnector.setUseSessionInDisconnect(nad, mockApdu);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(nad).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenThrow(AssertionError.class);

            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();

            // execute
            // ---------------------------------------------------------------------
            mSession.close();

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false));
            assertThat(mConnector.isConnected(), is(false));
            assertThat(mConnector.wasDisconnectCalled(), is(true));

            verifyStatic(times(1));
            MpiPacket.writeToStream(nad, bytes, mMockOutputStream);
        }


        @Test
        public void connectorSendsDuringCloseInputCloses() throws InterruptedException {
            // setup
            // ---------------------------------------------------------------------
            mockStatic(MpiPacket.class); // for MpiPacket.writeToStream

            final byte[] bytes = "CLOSE".getBytes();
            final InterfaceType nad = MPI;

            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(bytes);
            when(MpiPacket.writeToStream(nad, bytes, mMockOutputStream))
                    .thenReturn(true);
            mConnector.setUseSessionInDisconnect(nad, mockApdu);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(nad).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenThrow(AssertionError.class);

            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();

            // execute
            // ---------------------------------------------------------------------
            mSession.updatePollerStatus(PollerStatus.StoppedStreamBroken, ID_NO_REPLIES_YET);
            mSession.close();

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false));
            assertThat(mConnector.isConnected(), is(false));
            assertThat(mConnector.wasDisconnectCalled(), is(true));

            verifyStatic(times(1));
            MpiPacket.writeToStream(nad, bytes, mMockOutputStream);
        }
    }

    /**
     * These tests all ensure that the checks for an open and active session
     * work fine when sending and receiving.
     */
    @RunWith(PowerMockRunner.class)
    @PowerMockRunnerDelegate(value = JUnitParamsRunner.class)
    @PrepareForTest({MpiPacket.class})
    public static class NotOpenedYetSendAndReceiveTest {

        private StubConnector mConnector;
        private MockOutputStream mMockOutputStream;

        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;

        @Before
        public void setup() throws IOException, InterruptedException {
            mockStatic(MpiPacket.class); // for MpiPacket.writeToStream

            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }

            mMockOutputStream = new MockOutputStream();
            mConnector = new StubConnector(new MockInputStream(), mMockOutputStream);

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);
        }

        @After
        public void teardown() {
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));
        }


        @SuppressWarnings("TestMethodWithIncorrectSignature")
        @Test
        @Parameters({
                "send, error",
                "connect, send, error",
                "connect, poller, send, error",
                "connect, poller, open, send, ok",

                // technically all these an errors due to reading a response without
                // first sending a command...
                "response, error",
                "connect, response, error",
                "connect, poller, response, error",
                "connect, open, response, error",
                //"connect, poller, open, response, ok",

                "connect, poller, open, send, response, ok",
        })
        public void test(String... strings) throws Exception {

            List<String> params = Arrays.asList(strings);
            if (!(params.contains("error") || params.contains("ok"))) {
                Assert.fail();
            }

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenThrow(AssertionError.class);

            if (params.contains("connect")) {
                mConnector.connect();
            }
            if (params.contains("poller")) {
                mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            }
            if (params.contains("open")) {
                mSession.open();
            }

            boolean beforeExecuteIsActive = mSession.isActive();

            // execute
            // ---------------------------------------------------------------------
            try {
                if (params.contains("send")) {
                    mSession.sendCommandAPDU(MPI, mockApdu);
                }
                if (params.contains("response")) {
                    ResponseMessage rm = mSession.receiveResponse(MPI);
                    assertThat(rm, is(notNullValue()));
                }
                if (params.contains("error")) {
                    Assert.fail("Test is meant to throw exception, but doesn't");
                }
            } catch (IOException exception) {
                if (params.contains("ok")) {
                    Assert.fail("Test is meant to pass, but throws exception");
                }

                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, anyOf(containsString("open"), containsString("closed")));
                assertThat(msg, containsString("session"));
            }

            boolean beforeCloseIsActive = mSession.isActive();
            mSession.close();
            boolean afterCloseIsActive = mSession.isActive();

            // verify 2
            // ---------------------------------------------------------------------
            boolean expectedBeforeExecuteIsActive = params.contains("connect") &&
                    params.contains("poller") && params.contains("open");
            boolean expectedCloseIsActive = params.contains("open") && !params.contains("error");

            assertThat(beforeExecuteIsActive, is(expectedBeforeExecuteIsActive));
            assertThat(beforeCloseIsActive, is(expectedCloseIsActive));
            assertThat(afterCloseIsActive, is(false));

            int numCallback = beforeExecuteIsActive ? 1 : 0;
            verify(mConnectionStateCallback, times(numCallback)).handle(true);
            verify(mConnectionStateCallback, times(numCallback)).handle(false);
        }

    }

    /**
     * These tests all work on the assumption that the session is 'opened' correctly
     * and the other end of the queue sent a 'running' message to mSession,
     * as it expects to happen in startInputPollerThread. They also automatically close
     * the session in the @After
     */
    @RunWith(PowerMockRunner.class)
    @PrepareForTest({MpiPacket.class})
    public static class SendAndReceiveTest {

        private StubConnector mConnector;
        private MockOutputStream mMockOutputStream;

        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;


        @Before
        public void setup() throws Exception {
            mockStatic(MpiPacket.class); // for MpiPacket.writeToStream

            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }

            mMockOutputStream = new MockOutputStream();
            mConnector = new StubConnector(new MockInputStream(), mMockOutputStream);
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);

            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();
        }

        @After
        public void teardown() {
            mSession.close();
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void sendSingleCommandButNoReadResponse() throws IOException {
            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            // execute
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id, is(ID_FIRST_COMMAND));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
        }

        @Test
        public void sendMultipleCommandButNoReadResponse() throws IOException {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(0x11, 0x12, 0x13, 0x14));

            CommandApdu mockApdu2 = mock(CommandApdu.class);
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x21, 0x22, 0x23, 0x24));

            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x31, 0x32, 0x33, 0x34));

            when(MpiPacket.writeToStream(
                    eq(MPI), any(byte[].class), eq(mMockOutputStream)))
                    .thenReturn(true);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));

            verifyStatic(times(3));
            ArgumentCaptor<byte[]> args = ArgumentCaptor.forClass(byte[].class);
            MpiPacket.writeToStream(eq(MPI), args.capture(), eq(mMockOutputStream));

            List<byte[]> allBodies = args.getAllValues();
            assertThat(allBodies.get(0), is(equalTo(byteArray(0x11, 0x12, 0x13, 0x14))));
            assertThat(allBodies.get(1), is(equalTo(byteArray(0x21, 0x22, 0x23, 0x24))));
            assertThat(allBodies.get(2), is(equalTo(byteArray(0x31, 0x32, 0x33, 0x34))));
        }


        @Test
        public void sendSingleCommandAndReadSingleResponse()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);
            ResponseMessage rm = mSession.receiveResponse(MPI);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id, is(ID_FIRST_COMMAND));
            assertThat(rm, is(sameInstance(mockResponse)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
        }

        @Test
        public void multipleInterleavedCommandsAndResponses()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x9, 0xA, 0xB, 0xC));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            ResponseMessage rm1 = mSession.receiveResponse(MPI);

            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            ResponseMessage rm2 = mSession.receiveResponse(MPI);

            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);
            ResponseMessage rm3 = mSession.receiveResponse(MPI);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));

            assertThat(rm1, is(sameInstance(mockResponse1)));
            assertThat(rm2, is(sameInstance(mockResponse2)));
            assertThat(rm3, is(sameInstance(mockResponse3)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x5, 0x6, 0x7, 0x8), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x9, 0xA, 0xB, 0xC), mMockOutputStream);
        }

        @Test
        public void multipleBatchedCommandsAndResponses() throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x9, 0xA, 0xB, 0xC));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);

            ResponseMessage rm1 = mSession.receiveResponse(MPI);
            ResponseMessage rm2 = mSession.receiveResponse(MPI);
            ResponseMessage rm3 = mSession.receiveResponse(MPI);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));

            assertThat(rm1, is(sameInstance(mockResponse1)));
            assertThat(rm2, is(sameInstance(mockResponse2)));
            assertThat(rm3, is(sameInstance(mockResponse3)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x5, 0x6, 0x7, 0x8), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x9, 0xA, 0xB, 0xC), mMockOutputStream);
        }

        @Test
        public void sendSameObject() throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            ResponseMessage mockResponse4 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 3, mockResponse4))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu);
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu);
            int id3 = mSession.sendCommandAPDU(MPI, mockApdu);
            int id4 = mSession.sendCommandAPDU(MPI, mockApdu);

            ResponseMessage rm1 = mSession.receiveResponse(MPI);
            ResponseMessage rm2 = mSession.receiveResponse(MPI);
            ResponseMessage rm3 = mSession.receiveResponse(MPI);
            ResponseMessage rm4 = mSession.receiveResponse(MPI);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));
            assertThat(id4, is(ID_FIRST_COMMAND + 3));

            assertThat(rm1, is(sameInstance(mockResponse1)));
            assertThat(rm2, is(sameInstance(mockResponse2)));
            assertThat(rm3, is(sameInstance(mockResponse3)));
            assertThat(rm4, is(sameInstance(mockResponse4)));

            verifyStatic(times(4));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
        }

        @Test
        public void noCommandSentButResponseRead() throws Exception {

            // setup
            // ---------------------------------------------------------------------
            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {
                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("solicited response"));
                assertThat(msg, containsString("no outstanding command"));
            }

            assertThat(mSession.isActive(), is(false)); // errors, no longer active
        }

        @Test
        public void multipleInterleavedCommandsAndResponsesWithExtraneousReceiveResponse()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x9, 0xA, 0xB, 0xC));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            ResponseMessage rm1 = mSession.receiveResponse(MPI);

            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            ResponseMessage rm2 = mSession.receiveResponse(MPI);

            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);
            ResponseMessage rm3 = mSession.receiveResponse(MPI);

            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {
                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("solicited response"));
                assertThat(msg, containsString("no outstanding command"));
            }

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false)); // errors, no longer active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));

            assertThat(rm1, is(sameInstance(mockResponse1)));
            assertThat(rm2, is(sameInstance(mockResponse2)));
            assertThat(rm3, is(sameInstance(mockResponse3)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x5, 0x6, 0x7, 0x8), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x9, 0xA, 0xB, 0xC), mMockOutputStream);
        }

        @Test
        public void multipleBatchedCommandsAndResponsesWithExtraneousReceiveResponse()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x9, 0xA, 0xB, 0xC));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);

            ResponseMessage rm1 = mSession.receiveResponse(MPI);
            ResponseMessage rm2 = mSession.receiveResponse(MPI);
            ResponseMessage rm3 = mSession.receiveResponse(MPI);
            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {
                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("solicited response"));
                assertThat(msg, containsString("no outstanding command"));
            }

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false)); // errors, no longer active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));

            assertThat(rm1, is(sameInstance(mockResponse1)));
            assertThat(rm2, is(sameInstance(mockResponse2)));
            assertThat(rm3, is(sameInstance(mockResponse3)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x5, 0x6, 0x7, 0x8), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x9, 0xA, 0xB, 0xC), mMockOutputStream);
        }

        @Test
        public void pollerStopsBeforeSendAndReceive() throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            when(mQueues.get(MPI).take())
                    .thenThrow(AssertionError.class);
            when(mQueues.get(MPI).poll())
                    .thenReturn(null);
            when(mQueues.get(MPI).peek())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, null));

            // execute
            // ---------------------------------------------------------------------
            mSession.updatePollerStatus(PollerStatus.StoppedQueuePostTimedOut, ID_NO_REPLIES_YET);
            try {
                mSession.sendCommandAPDU(MPI, mockApdu);
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {

                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("connector"));
                assertThat(msg, containsString("not connected"));
            }

            assertThat(mSession.isActive(), is(false)); // errors, no longer active
        }

        @Test
        public void connectorBreaksBeforeSendAndReceive() throws Exception {

            /* Connector breaks but signal from poller has yet to be seen, or was missed */

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));

            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(false);

            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, null))
                    .thenThrow(AssertionError.class);
            when(mQueues.get(MPI).poll())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, null));

            // execute
            // ---------------------------------------------------------------------
            mConnector.forceDisconnect();
            try {
                mSession.sendCommandAPDU(MPI, mockApdu);
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {

                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("stream"));
            }

            assertThat(mSession.isActive(), is(false)); // errors, no longer active
        }

        @Test
        public void pollerStopsBetweenSendAndReceive() throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            when(mQueues.get(MPI).peek())
                    .thenReturn(null);

            // execute
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);
            mSession.updatePollerStatus(PollerStatus.StoppedQueuePostTimedOut, id);
            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {

                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("connector"));
                assertThat(msg, containsString("not connected"));
            }

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false)); // errors, no longer active
            assertThat(id, is(ID_FIRST_COMMAND));
        }

        @Test
        public void connectorBreaksBetweenSendAndReceive() throws Exception {

            /* Connector breaks but signal from poller has yet to be seen, or was missed,
             * but it managed to post the terminal to the queue */

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            when(mQueues.get(MPI).peek())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, null));

            // execute
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);
            mConnector.forceDisconnect();

            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {

                // verify
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("connector"));
                assertThat(msg, containsString("not connected"));
            }

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false)); // errors, no longer active
            assertThat(id, is(ID_FIRST_COMMAND));
        }

        @Test
        public void readsFromClosedQueue() throws Exception {

            /* Queue closes for whatever reason, signal from poller not yet received, and
             * connector still thinks it's open when asked */

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, null));

            // execute 1
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);
            ResponseMessage rm1 = mSession.receiveResponse(MPI);

            // verify 1
            // ---------------------------------------------------------------------
            assertThat(id, is(ID_FIRST_COMMAND));
            assertThat(rm1, is(sameInstance(mockResponse)));


            // execute 2
            // ---------------------------------------------------------------------
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu);
            assertThat(id2, is(ID_FIRST_COMMAND + 1));

            // now the connector breaks and the queue is closed. But perhaps we haven't received
            // those signals yet, only the connector breakage... so these haven't run:
            // mConnector.disconnect()
            // mSession.updatePollerStatus(PollerStatus.StoppedStreamBroken, 0);


            boolean beforeIsActive = mSession.isActive();
            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {
                // verify 2
                // ---------------------------------------------------------------------
                String msg = exception.getMessage().toLowerCase();
                assertThat(msg, containsString("queue closed"));
            }
            boolean afterIsActive = mSession.isActive();

            // verify 3
            // ---------------------------------------------------------------------
            assertThat(beforeIsActive, is(true));
            assertThat(afterIsActive, is(false));

        }

        @Test
        public void sendCommandError()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(0xee, 0xee, 0xee, 0xee));

            CommandApdu mockApdu2 = mock(CommandApdu.class);
            when(mockApdu2.getBytes()).thenReturn(byteArray(0xee, 0xee, 0xee, 0xee));

            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true)
                    .thenReturn(false);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            try {
                mSession.sendCommandAPDU(MPI, mockApdu2);
                Assert.fail();
            } catch (IOException exception) {
                // verify
                // ---------------------------------------------------------------------
                assertThat(exception.getMessage().toLowerCase(), containsString("write"));
            }
            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false)); // errors, no longer active
            assertThat(id1, is(equalTo(ID_FIRST_COMMAND)));

            verifyStatic(times(2));
            MpiPacket.writeToStream(MPI, byteArray(0xee, 0xee, 0xee, 0xee), mMockOutputStream);
        }

        @Test
        public void inconsistentQueueIDs()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 60, mockResponse2))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            mSession.sendCommandAPDU(MPI, mockApdu1);
            mSession.receiveResponse(MPI);
            mSession.sendCommandAPDU(MPI, mockApdu2);
            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (IOException exception) {
                // verify
                // ---------------------------------------------------------------------
                assertThat(exception.getMessage().toLowerCase(), containsString("inconsistent"));
            }

            assertThat(mSession.isActive(), is(false)); // errors, no longer active
        }

        @Test
        public void interruptExceptionDuringReceiveResponse()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            when(mQueues.get(MPI).take())
                    .thenThrow(InterruptedException.class);

            // execute
            // ---------------------------------------------------------------------
            mSession.sendCommandAPDU(MPI, mockApdu);
            try {
                mSession.receiveResponse(MPI);
                Assert.fail();
            } catch (InterruptedException ignore) {

            }

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false));
        }
    }

    /**
     * These tests all work on the assumption that the session is 'opened' correctly
     * and the other end of the queue sent a 'running' message to mSession,
     * as it expects to happen in startInputPollerThread. They all close the session at
     * the end in @After.
     *
     * This doesn't test everything, as it expects SendAndReceiveNoTimeoutTest to
     * do that. It simply tests timeout feature.
     */
    @RunWith(PowerMockRunner.class)
    @PrepareForTest({MpiPacket.class})
    public static class SendAndReceiveTimeoutTest {

        private StubConnector mConnector;
        private MockOutputStream mMockOutputStream;

        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;


        @Before
        public void setup() throws Exception {
            mockStatic(MpiPacket.class); // for MpiPacket.writeToStream

            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }

            mMockOutputStream = new MockOutputStream();
            mConnector = new StubConnector(new MockInputStream(), mMockOutputStream);
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);

            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();
        }

        @After
        public void teardown() {
            mSession.close();
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void readResponseWithinTimeoutPeriod()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(MPI).poll(anyLong(), any(TimeUnit.class)))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);
            ResponseMessage rm = mSession.receiveResponseTimeout(MPI, 3000L);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id, is(ID_FIRST_COMMAND));
            assertThat(rm, is(sameInstance(mockResponse)));
        }

        @Test
        public void readResponseTimesout()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            when(mQueues.get(MPI).poll(anyLong(), any(TimeUnit.class)))
                    .thenReturn(null)
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);
            ResponseMessage rm = mSession.receiveResponseTimeout(MPI, 50L);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id, is(ID_FIRST_COMMAND));
            assertThat(rm, is(nullValue()));
        }

        @Test
        public void readResponseAfterATimedOutResponse()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mQueues.get(MPI).poll(anyLong(), any(TimeUnit.class)))
                    .thenReturn(null)
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            mSession.sendCommandAPDU(MPI, mockApdu);
            ResponseMessage rm1 = mSession.receiveResponseTimeout(MPI, 50L);

            // try again
            ResponseMessage rm2 = mSession.receiveResponseTimeout(MPI, 5000L);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(rm1, is(nullValue()));
            assertThat(rm2, is(sameInstance(mockResponse)));
        }

        @Test
        public void interruptExceptionDuringReceiveResponse()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            when(mQueues.get(MPI).poll(anyLong(), any(TimeUnit.class)))
                    .thenThrow(InterruptedException.class);

            // execute
            // ---------------------------------------------------------------------
            mSession.sendCommandAPDU(MPI, mockApdu);
            try {
                mSession.receiveResponseTimeout(MPI, 3000L);
                Assert.fail();
            } catch (InterruptedException ignore) {

            }

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false));
        }

    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({MpiPacket.class})
    public static class SendAndReceiveIdTest {

        private StubConnector mConnector;
        private MockOutputStream mMockOutputStream;

        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;


        @Before
        public void setup() throws Exception {
            mockStatic(MpiPacket.class); // for MpiPacket.writeToStream

            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }

            mMockOutputStream = new MockOutputStream();
            mConnector = new StubConnector(new MockInputStream(), mMockOutputStream);
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);

            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();
        }

        @After
        public void teardown() {
            mSession.close();
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void multipleInterleavedCommandsAndResponses()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x9, 0xA, 0xB, 0xC));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            ResponseMessage rm1 = mSession.receiveResponseId(MPI, id1);

            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            ResponseMessage rm2 = mSession.receiveResponse(MPI);
            //ResponseMessage rm2 = mSession.receiveResponseId(MPI, id2);

            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);
            ResponseMessage rm3 = mSession.receiveResponseId(MPI, id3);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));

            assertThat(rm1, is(sameInstance(mockResponse1)));
            assertThat(rm2, is(sameInstance(mockResponse2)));
            assertThat(rm3, is(sameInstance(mockResponse3)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x5, 0x6, 0x7, 0x8), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x9, 0xA, 0xB, 0xC), mMockOutputStream);
        }

        @Test
        public void multipleBatchedCommandsAndResponses() throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x9, 0xA, 0xB, 0xC));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);

            ResponseMessage rm1 = mSession.receiveResponseId(MPI, id1);
            // spice it up a bit
            // ResponseMessage rm2 = mSession.receiveResponseId(MPI, id2);
            ResponseMessage rm2 = mSession.receiveResponse(MPI);
            ResponseMessage rm3 = mSession.receiveResponseId(MPI, id3);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));

            assertThat(rm1, is(sameInstance(mockResponse1)));
            assertThat(rm2, is(sameInstance(mockResponse2)));
            assertThat(rm3, is(sameInstance(mockResponse3)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x5, 0x6, 0x7, 0x8), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(0x9, 0xA, 0xB, 0xC), mMockOutputStream);
        }

        @Test
        public void multipleBatchedCommandsAndResponsesWithSkippedIds() throws Exception {

            // skipping IDs is currently not supported.

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(0x5, 0x6, 0x7, 0x8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(0x9, 0xA, 0xB, 0xC));
            when(MpiPacket.writeToStream(eq(MPI), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockResponse3 = mock(ResponseMessage.class);
            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockResponse2))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockResponse3))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            mSession.sendCommandAPDU(MPI, mockApdu1);
            mSession.sendCommandAPDU(MPI, mockApdu2);
            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);

            // ResponseMessage rm1 = mSession.receiveResponse(MPI, id1);
            // ResponseMessage rm2 = mSession.receiveResponse(MPI, id2);
            try {
                mSession.receiveResponseId(MPI, id3);
                Assert.fail();
            } catch (IOException exception) {
                // verify
                // ---------------------------------------------------------------------
                assertThat(exception.getMessage().toLowerCase(), containsString("id"));
            }

            assertThat(mSession.isActive(), is(false)); // errors, no longer active

        }

        @Test
        public void interruptExceptionDuringReceiveResponse()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu = mock(CommandApdu.class);
            when(mockApdu.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream))
                    .thenReturn(true);

            when(mQueues.get(MPI).take())
                    .thenThrow(InterruptedException.class);

            // execute
            // ---------------------------------------------------------------------
            int id = mSession.sendCommandAPDU(MPI, mockApdu);
            try {
                mSession.receiveResponseId(MPI, id);
                Assert.fail();
            } catch (InterruptedException ignore) {

            }

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(false));
        }


    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({MpiPacket.class, ResponseMessage.class})
    public static class SendAndReceiveCrossNadTest {

        private StubConnector mConnector;
        private MockOutputStream mMockOutputStream;

        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;
        private EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;


        @Before
        public void setup() throws Exception {
            mockStatic(MpiPacket.class);

            mQueues = new EnumMap<>(InterfaceType.class);

            for (InterfaceType nad : InterfaceType.values()) {
                mQueues.put(nad, mock(MockPollerQueue.class));
            }

            mMockOutputStream = new MockOutputStream();
            mConnector = new StubConnector(new MockInputStream(), mMockOutputStream);
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = new MpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback, mQueues);

            mSession.updatePollerStatus(PollerStatus.Running, ID_NO_REPLIES_YET);
            mSession.open();
        }

        @After
        public void teardown() {
            mSession.close();
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(MPI)));
            verifyNoMoreInteractions(ignoreStubs(mQueues.get(RPI)));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void multipleInterleavedCommandsAndResponses()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockMpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockMpiCommand2 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand2 = mock(CommandApdu.class);
            when(mockMpiCommand1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockMpiCommand2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));
            when(mockRpiCommand1.getBytes()).thenReturn(byteArray(0xd1, 0xd2, 0xd3, 0xd4));
            when(mockRpiCommand2.getBytes()).thenReturn(byteArray(0xd5, 0xd6, 0xd7, 0xd8));

            when(MpiPacket.writeToStream(
                    any(InterfaceType.class), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockMpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockMpiResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse2 = mock(ResponseMessage.class);

            when(mockMpiResponse1.getNodeAddress()).thenReturn(MPI);
            when(mockMpiResponse2.getNodeAddress()).thenReturn(MPI);
            when(mockRpiResponse1.getNodeAddress()).thenReturn(RPI);
            when(mockRpiResponse2.getNodeAddress()).thenReturn(RPI);

            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockMpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockMpiResponse2))
                    .thenThrow(AssertionError.class);
            when(mQueues.get(RPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockRpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 3, mockRpiResponse2))
                    .thenThrow(AssertionError.class);


            // execute
            // ---------------------------------------------------------------------
            int mpiId1 = mSession.sendCommandAPDU(MPI, mockMpiCommand1);
            ResponseMessage mpiResponse1 = mSession.receiveResponse(MPI);

            int rpiId1 = mSession.sendCommandAPDU(RPI, mockRpiCommand1);
            ResponseMessage rpiResponse1 = mSession.receiveResponse(RPI);

            int mpiId2 = mSession.sendCommandAPDU(MPI, mockMpiCommand2);
            ResponseMessage mpiResponse2 = mSession.receiveResponse(MPI);

            int rpiId2 = mSession.sendCommandAPDU(RPI, mockRpiCommand2);
            ResponseMessage rpiResponse2 = mSession.receiveResponse(RPI);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(mpiId1, is(ID_FIRST_COMMAND));
            assertThat(rpiId1, is(ID_FIRST_COMMAND + 1));
            assertThat(mpiId2, is(ID_FIRST_COMMAND + 2));
            assertThat(rpiId2, is(ID_FIRST_COMMAND + 3));

            assertThat(mpiResponse1, is(sameInstance(mpiResponse1)));
            assertThat(mpiResponse2, is(sameInstance(mpiResponse2)));
            assertThat(rpiResponse1, is(sameInstance(rpiResponse1)));
            assertThat(rpiResponse2, is(sameInstance(rpiResponse2)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(RPI, byteArray(0xd1, 0xd2, 0xd3, 0xd4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(5, 6, 7, 8), mMockOutputStream);
            MpiPacket.writeToStream(RPI, byteArray(0xd5, 0xd6, 0xd7, 0xd8), mMockOutputStream);
        }

        @Test
        public void multipleBatchedCommandsAndResponses()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockMpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockMpiCommand2 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand2 = mock(CommandApdu.class);
            when(mockMpiCommand1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockMpiCommand2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));
            when(mockRpiCommand1.getBytes()).thenReturn(byteArray(0xd1, 0xd2, 0xd3, 0xd4));
            when(mockRpiCommand2.getBytes()).thenReturn(byteArray(0xd5, 0xd6, 0xd7, 0xd8));

            when(MpiPacket.writeToStream(
                    any(InterfaceType.class), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockMpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockMpiResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse2 = mock(ResponseMessage.class);

            when(mockMpiResponse1.getNodeAddress()).thenReturn(MPI);
            when(mockMpiResponse2.getNodeAddress()).thenReturn(MPI);
            when(mockRpiResponse1.getNodeAddress()).thenReturn(RPI);
            when(mockRpiResponse2.getNodeAddress()).thenReturn(RPI);

            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockMpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockMpiResponse2))
                    .thenThrow(AssertionError.class);
            when(mQueues.get(RPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockRpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 3, mockRpiResponse2))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int mpiId1 = mSession.sendCommandAPDU(MPI, mockMpiCommand1);
            int rpiId1 = mSession.sendCommandAPDU(RPI, mockRpiCommand1);
            int mpiId2 = mSession.sendCommandAPDU(MPI, mockMpiCommand2);
            int rpiId2 = mSession.sendCommandAPDU(RPI, mockRpiCommand2);

            ResponseMessage mpiResponse1 = mSession.receiveResponse(MPI);
            ResponseMessage rpiResponse1 = mSession.receiveResponse(RPI);
            ResponseMessage mpiResponse2 = mSession.receiveResponse(MPI);
            ResponseMessage rpiResponse2 = mSession.receiveResponse(RPI);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(mpiId1, is(ID_FIRST_COMMAND));
            assertThat(rpiId1, is(ID_FIRST_COMMAND + 1));
            assertThat(mpiId2, is(ID_FIRST_COMMAND + 2));
            assertThat(rpiId2, is(ID_FIRST_COMMAND + 3));

            assertThat(mpiResponse1, is(sameInstance(mpiResponse1)));
            assertThat(mpiResponse2, is(sameInstance(mpiResponse2)));
            assertThat(rpiResponse1, is(sameInstance(rpiResponse1)));
            assertThat(rpiResponse2, is(sameInstance(rpiResponse2)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(RPI, byteArray(0xd1, 0xd2, 0xd3, 0xd4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(5, 6, 7, 8), mMockOutputStream);
            MpiPacket.writeToStream(RPI, byteArray(0xd5, 0xd6, 0xd7, 0xd8), mMockOutputStream);
        }

        @Test
        public void multipleInterleavedCommandsAndResponsesWithIds()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockMpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockMpiCommand2 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand2 = mock(CommandApdu.class);
            when(mockMpiCommand1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockMpiCommand2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));
            when(mockRpiCommand1.getBytes()).thenReturn(byteArray(0xd1, 0xd2, 0xd3, 0xd4));
            when(mockRpiCommand2.getBytes()).thenReturn(byteArray(0xd5, 0xd6, 0xd7, 0xd8));

            when(MpiPacket.writeToStream(
                    any(InterfaceType.class), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockMpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockMpiResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse2 = mock(ResponseMessage.class);

            when(mockMpiResponse1.getNodeAddress()).thenReturn(MPI);
            when(mockMpiResponse2.getNodeAddress()).thenReturn(MPI);
            when(mockRpiResponse1.getNodeAddress()).thenReturn(RPI);
            when(mockRpiResponse2.getNodeAddress()).thenReturn(RPI);

            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockMpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockMpiResponse2))
                    .thenThrow(AssertionError.class);
            when(mQueues.get(RPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockRpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 3, mockRpiResponse2))
                    .thenThrow(AssertionError.class);


            // execute
            // ---------------------------------------------------------------------
            int mpiId1 = mSession.sendCommandAPDU(MPI, mockMpiCommand1);
            ResponseMessage mpiResponse1 = mSession.receiveResponseId(MPI, mpiId1);

            int rpiId1 = mSession.sendCommandAPDU(RPI, mockRpiCommand1);
            ResponseMessage rpiResponse1 = mSession.receiveResponseId(RPI, rpiId1);

            int mpiId2 = mSession.sendCommandAPDU(MPI, mockMpiCommand2);
            ResponseMessage mpiResponse2 = mSession.receiveResponseId(MPI, mpiId2);

            int rpiId2 = mSession.sendCommandAPDU(RPI, mockRpiCommand2);
            ResponseMessage rpiResponse2 = mSession.receiveResponseId(RPI, rpiId2);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(mpiId1, is(ID_FIRST_COMMAND));
            assertThat(rpiId1, is(ID_FIRST_COMMAND + 1));
            assertThat(mpiId2, is(ID_FIRST_COMMAND + 2));
            assertThat(rpiId2, is(ID_FIRST_COMMAND + 3));

            assertThat(mpiResponse1, is(sameInstance(mpiResponse1)));
            assertThat(mpiResponse2, is(sameInstance(mpiResponse2)));
            assertThat(rpiResponse1, is(sameInstance(rpiResponse1)));
            assertThat(rpiResponse2, is(sameInstance(rpiResponse2)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(RPI, byteArray(0xd1, 0xd2, 0xd3, 0xd4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(5, 6, 7, 8), mMockOutputStream);
            MpiPacket.writeToStream(RPI, byteArray(0xd5, 0xd6, 0xd7, 0xd8), mMockOutputStream);
        }

        @Test
        public void multipleBatchedCommandsAndResponsesWithIds()
                throws Exception {

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockMpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockMpiCommand2 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand1 = mock(CommandApdu.class);
            CommandApdu mockRpiCommand2 = mock(CommandApdu.class);
            when(mockMpiCommand1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockMpiCommand2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));
            when(mockRpiCommand1.getBytes()).thenReturn(byteArray(0xd1, 0xd2, 0xd3, 0xd4));
            when(mockRpiCommand2.getBytes()).thenReturn(byteArray(0xd5, 0xd6, 0xd7, 0xd8));

            when(MpiPacket.writeToStream(
                    any(InterfaceType.class), any(byte[].class), same(mMockOutputStream)))
                    .thenReturn(true);

            ResponseMessage mockMpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockMpiResponse2 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse1 = mock(ResponseMessage.class);
            ResponseMessage mockRpiResponse2 = mock(ResponseMessage.class);

            when(mockMpiResponse1.getNodeAddress()).thenReturn(MPI);
            when(mockMpiResponse2.getNodeAddress()).thenReturn(MPI);
            when(mockRpiResponse1.getNodeAddress()).thenReturn(RPI);
            when(mockRpiResponse2.getNodeAddress()).thenReturn(RPI);

            when(mQueues.get(RPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY, mockRpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 2, mockRpiResponse2))
                    .thenThrow(AssertionError.class);

            when(mQueues.get(MPI).take())
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 1, mockMpiResponse1))
                    .thenReturn(new PollerMessage(ID_FIRST_REPLY + 3, mockMpiResponse2))
                    .thenThrow(AssertionError.class);

            // execute
            // ---------------------------------------------------------------------
            int rpiId1 = mSession.sendCommandAPDU(RPI, mockRpiCommand1);
            int mpiId1 = mSession.sendCommandAPDU(MPI, mockMpiCommand1);
            int rpiId2 = mSession.sendCommandAPDU(RPI, mockRpiCommand2);
            int mpiId2 = mSession.sendCommandAPDU(MPI, mockMpiCommand2);

            ResponseMessage rpiResponse1 = mSession.receiveResponseId(RPI, rpiId1);
            ResponseMessage mpiResponse1 = mSession.receiveResponseId(MPI, mpiId1);
            ResponseMessage rpiResponse2 = mSession.receiveResponseId(RPI, rpiId2);
            ResponseMessage mpiResponse2 = mSession.receiveResponseId(MPI, mpiId2);

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active

            assertThat(rpiId1, is(ID_FIRST_COMMAND));
            assertThat(mpiId1, is(ID_FIRST_COMMAND + 1));
            assertThat(rpiId2, is(ID_FIRST_COMMAND + 2));
            assertThat(mpiId2, is(ID_FIRST_COMMAND + 3));

            assertThat(mpiResponse1, is(sameInstance(mpiResponse1)));
            assertThat(mpiResponse2, is(sameInstance(mpiResponse2)));
            assertThat(rpiResponse1, is(sameInstance(rpiResponse1)));
            assertThat(rpiResponse2, is(sameInstance(rpiResponse2)));

            verifyStatic(times(1));
            MpiPacket.writeToStream(RPI, byteArray(0xd1, 0xd2, 0xd3, 0xd4), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(1, 2, 3, 4), mMockOutputStream);
            MpiPacket.writeToStream(RPI, byteArray(0xd5, 0xd6, 0xd7, 0xd8), mMockOutputStream);
            MpiPacket.writeToStream(MPI, byteArray(5, 6, 7, 8), mMockOutputStream);
        }


    }

    public static class makeMpiProtocolSessionTestErrors {

        private StubConnector mConnector;
        private MockOutputStream mMockOutputStream;
        private MockInputStream mMockInput;
        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;

        @Before
        public void setup() {
            mMockOutputStream = new MockOutputStream();
            mMockInput = new MockInputStream(new int[]{MockInputStream.PAUSE});
            mConnector = new StubConnector(mMockInput, mMockOutputStream);

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
        }

        @After
        public void teardown() {

            mConnector = null;
            mMockOutputStream = null;
            mMockInput = null;
            mMockUnsolicitedCallback = null;
            mConnectionStateCallback = null;
        }

        @Test
        public void noErrors() throws IOException {
            // this is more of a test of the tests than anything!

            // setup
            mConnector.connect();

            // execute
            MpiProtocolSession session = makeMpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback);
            assert session != null;
            session.close();

            // verify
            assertThat(mConnector.wasDisconnectCalled(), is(true));
            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);
        }

        @Test
        public void connectorNotConnected() throws IOException {
            // setup
            // mConnector.connect();

            // execute
            MpiProtocolSession session = makeMpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback);

            // verify
            assertThat(session, is(nullValue()));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void getInputStreamThrows() throws IOException {
            // setup
            mConnector.connect();
            mConnector.setThrowOnGetInputStream(true);

            // execute
            MpiProtocolSession session = makeMpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback);

            // verify
            assertThat(session, is(nullValue()));
            assertThat(mConnector.wasDisconnectCalled(), is(true));
            verify(mConnectionStateCallback, times(0)).handle(anyBoolean());
        }

        @Test
        public void runtimeErr() throws IOException {
            // bit of a fragile test

            // setup
            ConnectionStateCallback connectionStateCallback = new ConnectionStateCallback() {
                @Override
                public void handle(boolean state) {
                    if (state) {
                        // just throw one on open
                        //noinspection ProhibitedExceptionThrown
                        throw new RuntimeException("Throwing a runtime error in callback");
                    }
                }
            };
            mConnector.connect();

            // execute
            MpiProtocolSession session = makeMpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, connectionStateCallback);

            // verify
            assertThat(session, is(nullValue()));
        }
    }

    public static class makeMpiProtocolSessionTest {

        private StubConnector mConnector;
        private MockOutputStream mMockOutputStream;
        private MockInputStream mMockInput;
        private UnsolicitedResponseCallback mMockUnsolicitedCallback;
        private ConnectionStateCallback mConnectionStateCallback;
        private MpiProtocolSession mSession;

        /*
         *  Junit timeout mechanisms are flaky when it comes to setup/teardown, so
         *  just call them manually.
         */
        public void setup() throws IOException {
            mMockOutputStream = new MockOutputStream();
            mMockInput = new MockInputStream(new int[]{MockInputStream.PAUSE});
            mConnector = new StubConnector(mMockInput, mMockOutputStream);
            mConnector.connect();

            mMockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mConnectionStateCallback = mock(ConnectionStateCallback.class);
            mSession = makeMpiProtocolSession(
                    mConnector, mMockUnsolicitedCallback, mConnectionStateCallback);
            if (mSession == null) throw new AssertionError("null session?");
        }

        /*
         *  Junit timeout mechanisms are flaky when it comes to setup/teardown, so
         *  just call them manually.
         */
        public void teardown() {
            mSession.close();

            verify(mConnectionStateCallback, times(1)).handle(true);
            verify(mConnectionStateCallback, times(1)).handle(false);

            mConnector = null;
            mMockOutputStream = null;
            mMockInput = null;
            mMockUnsolicitedCallback = null;
            mConnectionStateCallback = null;
            mSession = null;
        }

        @Test(timeout = 2000L)
        public void simpleTest() throws Exception {

            setup();

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));
            when(mockApdu3.getBytes()).thenReturn(byteArray(9, 10, 11, 12));

            int[] bytes = makeResponseInputStream(
                    "!PAUSE", "one", "!PAUSE", "two", "!PAUSE", "three", "!BLOCK"
            );
            mMockInput.waitForPauseAndSetData(bytes);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            mMockInput.waitAndSignalPausedThreads(); // "!PAUSE"
            ResponseMessage rm1 = mSession.receiveResponse(MPI); // "one"

            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);
            mMockInput.waitAndSignalPausedThreads(); // "!PAUSE"
            ResponseMessage rm2 = mSession.receiveResponse(MPI); // "two"

            int id3 = mSession.sendCommandAPDU(MPI, mockApdu3);
            mMockInput.waitAndSignalPausedThreads(); // "!PAUSE"
            ResponseMessage rm3 = mSession.receiveResponse(MPI); // "three"

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active
            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(id3, is(ID_FIRST_COMMAND + 2));
            assertThat(new String(rm1.getBody(), UTF_8), is(equalTo("one")));
            assertThat(new String(rm2.getBody(), UTF_8), is(equalTo("two")));
            assertThat(new String(rm3.getBody(), UTF_8), is(equalTo("three")));

            teardown();
        }

        @Test(timeout = 2000L)
        public void manyCommandsInterleaves() throws Exception {

            setup();

            // setup
            // ---------------------------------------------------------------------
            int numCommands = 200;
            ArrayList<String> responseStrings = new ArrayList<>(numCommands);
            ArrayList<CommandApdu> commands = new ArrayList<>(numCommands);

            String responseDataPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv";
            assertThat(responseDataPool.length() % 8, is(equalTo(0)));

            for (int i = 0; i < numCommands; i++) {
                CommandApdu mockApdu = mock(CommandApdu.class);
                int base = i * 8;
                byte[] bytes = byteArray(base, base + 1, base + 2, base + 3, base + 4, base + 5);
                when(mockApdu.getBytes()).thenReturn(bytes);
                commands.add(mockApdu);


                int stringBase = base % 48;
                int stringEnd = stringBase + 8;
                responseStrings.add("!PAUSE");
                responseStrings.add(responseDataPool.substring(stringBase, stringEnd));
            }

            int[] bytes = makeResponseInputStream(responseStrings.toArray(new String[0]));
            mMockInput.waitForPauseAndSetData(bytes);

            for (int i = 0; i < numCommands; i++) {

                // execute
                // ---------------------------------------------------------------------
                int id = mSession.sendCommandAPDU(MPI, commands.get(i));
                mMockInput.waitAndSignalPausedThreads();
                ResponseMessage rm = mSession.receiveResponse(InterfaceType.MPI);

                // verify
                // ---------------------------------------------------------------------
                assertThat(mSession.isActive(), is(true)); // no errors, still active
                assertThat(id, is(equalTo(i)));

                String actual = new String(rm.getBody(), UTF_8);
                int idx = responseDataPool.indexOf(actual);
                assertThat(idx, is(equalTo((i * 8) % 48)));
            }

            teardown();
        }

        @Test(timeout = 2000L)
        public void manyCommandsBatched() throws Exception {

            setup();


            // setup
            // ---------------------------------------------------------------------
            int numCommands = 200;
            ArrayList<String> responseStrings = new ArrayList<>(numCommands);
            ArrayList<CommandApdu> commands = new ArrayList<>(numCommands);

            String responseDataPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuv";
            assertThat(responseDataPool.length() % 8, is(equalTo(0)));

            for (int i = 0; i < numCommands; i++) {
                CommandApdu mockApdu = mock(CommandApdu.class);
                int base = i * 8;
                byte[] bytes = byteArray(base, base + 1, base + 2, base + 3, base + 4, base + 5);
                when(mockApdu.getBytes()).thenReturn(bytes);
                commands.add(mockApdu);


                int stringBase = base % 48;
                int stringEnd = stringBase + 8;
                responseStrings.add("!PAUSE");
                responseStrings.add(responseDataPool.substring(stringBase, stringEnd));
            }
            responseStrings.add("!BLOCK");


            int[] bytes = makeResponseInputStream(responseStrings.toArray(new String[0]));
            mMockInput.waitForPauseAndSetData(bytes);

            for (int i = 0; i < numCommands; i++) {
                // execute commands
                // ---------------------------------------------------------------------
                int id = mSession.sendCommandAPDU(MPI, commands.get(i));

                // verify commands
                // ---------------------------------------------------------------------
                assertThat(mSession.isActive(), is(true)); // no errors, still active
                assertThat(id, is(equalTo(i)));
            }

            for (int i = 0; i < numCommands; i++) {
                // execute responses
                // ---------------------------------------------------------------------
                mMockInput.waitAndSignalPausedThreads();
                ResponseMessage rm = mSession.receiveResponseId(InterfaceType.MPI, i);

                // verify responses
                // ---------------------------------------------------------------------
                assertThat(mSession.isActive(), is(true)); // no errors, still active

                String actual = new String(rm.getBody(), UTF_8);
                int idx = responseDataPool.indexOf(actual);
                assertThat(idx, is(equalTo((i * 8) % 48)));
            }

            teardown();
        }

        /**
         * The input poller hits EOF before the client bothers to receive the response and
         * therefore take from the queue.
         */
        @Test(timeout = 2000L)
        public void earlyEOF() throws Exception {

            setup();

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));

            int[] bytes = makeResponseInputStream(
                    "one", "!PAUSE", "two", "!EOF"
            );
            mMockInput.waitForPauseAndSetData(bytes);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            ResponseMessage rm1 = mSession.receiveResponse(MPI); // "one"
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);

            mMockInput.waitAndSignalPausedThreads(); // "!PAUSE"
            // We shouldn't expect any errors -- the response was in the stream,
            // and should have been read by the InputPoller, so we should be able to read it.
            ResponseMessage rm2 = mSession.receiveResponse(MPI); // "two" and "!EOF"

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true)); // no errors, still active
            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(new String(rm1.getBody(), UTF_8), is(equalTo("one")));
            assertThat(new String(rm2.getBody(), UTF_8), is(equalTo("two")));

            teardown();
        }

        /**
         * The input poller explodes before the client bothers to receive the response and
         * therefore take from the queue.
         */
        @Test(timeout = 2000L)
        public void earlyIOException() throws Exception {

            setup();

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));

            int[] bytes = makeResponseInputStream(
                    "one", "!PAUSE", "two", "!IO_EXCEPTION"
            );
            mMockInput.waitForPauseAndSetData(bytes);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            ResponseMessage rm1 = mSession.receiveResponse(MPI); // "one"
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);

            mMockInput.waitAndSignalPausedThreads(); // "!PAUSE"

            // We shouldn't expect any errors -- the response was in the stream,
            // and should have been read by the InputPoller, so we should be able to read it.
            ResponseMessage rm2 = mSession.receiveResponse(MPI); // "two" and "!IO_EXCEPTION"

            // verify
            // ---------------------------------------------------------------------
            assertThat(mSession.isActive(), is(true));

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(new String(rm1.getBody(), UTF_8), is(equalTo("one")));
            assertThat(new String(rm2.getBody(), UTF_8), is(equalTo("two")));

            teardown();
        }

        /**
         * The input poller hits EOF whilst the client is trying to read it
         */
        @Test(timeout = 3000L)
        public void timeoutThenEOF() throws Exception {

            setup();

            // setup
            // ---------------------------------------------------------------------
            CommandApdu mockApdu1 = mock(CommandApdu.class);
            CommandApdu mockApdu2 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));
            when(mockApdu2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));

            int[] bytes = makeResponseInputStream(
                    "one", "!PAUSE", "!EOF"
            );
            mMockInput.waitForPauseAndSetData(bytes);

            // execute
            // ---------------------------------------------------------------------
            int id1 = mSession.sendCommandAPDU(MPI, mockApdu1);
            ResponseMessage rm1 = mSession.receiveResponse(MPI); // "one"
            int id2 = mSession.sendCommandAPDU(MPI, mockApdu2);

            // input poller should hit !PAUSE now. receiveResponse should timeout.
            ResponseMessage timeoutMsg = mSession.receiveResponseTimeout(MPI, 1500L);


            mMockInput.waitAndSignalPausedThreads(); // "!PAUSE"
            boolean activeBefore = mSession.isActive();
            IOException exception;
            try {
                // This should hit EOF which causes IOException.
                mSession.receiveResponse(MPI);
                Assert.fail();
                return;
            } catch (IOException e) {
                exception = e;
            }
            boolean activeAfter = mSession.isActive();

            // verify
            // ---------------------------------------------------------------------
            assertThat(activeBefore, is(true));
            assertThat(activeAfter, is(false));

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND + 1));
            assertThat(new String(rm1.getBody(), UTF_8), is(equalTo("one")));

            assertThat(timeoutMsg, is(nullValue()));
            assertThat(exception.getMessage(), containsString("queue closed"));

            teardown();
        }

    }

    public static class makeMpiProtocolSessionOpenMultipleTest {

        @Test(timeout = 3000L)
        public void simpleTest() throws Exception {

            // setup connector 1
            // ---------------------------------------------------------------------
            MockOutputStream mockOutputStream1 = new MockOutputStream();
            MockInputStream mockInputStream1 = new MockInputStream(
                    makeResponseInputStream("one", "!BLOCK"));
            StubConnector connector1 = new StubConnector(
                    mockInputStream1, mockOutputStream1);
            connector1.connect();
            UnsolicitedResponseCallback mockUnsolicitedCallback1 = mock(
                    UnsolicitedResponseCallback.class);
            ConnectionStateCallback connectionStateCallback1 = mock(
                    ConnectionStateCallback.class);

            CommandApdu mockApdu1 = mock(CommandApdu.class);
            when(mockApdu1.getBytes()).thenReturn(byteArray(1, 2, 3, 4));

            // setup connector 2
            // ---------------------------------------------------------------------
            MockOutputStream mockOutputStream2 = new MockOutputStream();
            MockInputStream mockInputStream2 = new MockInputStream(
                    makeResponseInputStream("two", "!BLOCK"));
            StubConnector connector2 = new StubConnector(
                    mockInputStream2, mockOutputStream2);
            connector2.connect();
            UnsolicitedResponseCallback mockUnsolicitedCallback2 = mock(
                    UnsolicitedResponseCallback.class);
            ConnectionStateCallback connectionStateCallback2 = mock(
                    ConnectionStateCallback.class);

            CommandApdu mockApdu2 = mock(CommandApdu.class);
            when(mockApdu2.getBytes()).thenReturn(byteArray(5, 6, 7, 8));


            // setup connector 2
            // ---------------------------------------------------------------------
            MockOutputStream mockOutputStream3 = new MockOutputStream();
            MockInputStream mockInputStream3 = new MockInputStream(
                    makeResponseInputStream("three", "four", "!BLOCK"));
            StubConnector connector3 = new StubConnector(
                    mockInputStream3, mockOutputStream3);
            connector3.connect();
            UnsolicitedResponseCallback mockUnsolicitedCallback3 = mock(
                    UnsolicitedResponseCallback.class);
            ConnectionStateCallback connectionStateCallback3 = mock(
                    ConnectionStateCallback.class);

            CommandApdu mockApdu3 = mock(CommandApdu.class);
            when(mockApdu3.getBytes()).thenReturn(byteArray(9, 10, 11, 12));

            CommandApdu mockApdu4 = mock(CommandApdu.class);
            when(mockApdu4.getBytes()).thenReturn(byteArray(13, 14, 15, 16));


            // ---------------------------------------------------------------------
            // execute
            // ---------------------------------------------------------------------
            MpiProtocolSession session1 = makeMpiProtocolSession(
                    connector1, mockUnsolicitedCallback1, connectionStateCallback1);
            MpiProtocolSession session2 = makeMpiProtocolSession(
                    connector2, mockUnsolicitedCallback2, connectionStateCallback2);
            MpiProtocolSession session3 = makeMpiProtocolSession(
                    connector3, mockUnsolicitedCallback3, connectionStateCallback3);
            assert session1 != null;
            assert session2 != null;
            assert session3 != null;

            int id1 = session1.sendCommandAPDU(MPI, mockApdu1);
            int id2 = session2.sendCommandAPDU(MPI, mockApdu3);
            int id3_1 = session3.sendCommandAPDU(MPI, mockApdu3);
            int id3_2 = session3.sendCommandAPDU(MPI, mockApdu3);
            ResponseMessage rm1 = session1.receiveResponse(MPI);
            ResponseMessage rm2 = session2.receiveResponse(MPI);
            ResponseMessage rm3_1 = session3.receiveResponse(MPI);
            ResponseMessage rm3_2 = session3.receiveResponse(MPI);

            boolean active1Before = session1.isActive();
            boolean active2Before = session2.isActive();
            boolean active3Before = session3.isActive();
            session1.close();
            session2.close();
            session3.close();
            boolean active1After = session1.isActive();
            boolean active2After = session2.isActive();
            boolean active3After = session3.isActive();

            // verify
            // ---------------------------------------------------------------------
            assertThat(active1Before, is(true));
            assertThat(active2Before, is(true));
            assertThat(active3Before, is(true));
            assertThat(active1After, is(false));
            assertThat(active2After, is(false));
            assertThat(active3After, is(false));

            assertThat(id1, is(ID_FIRST_COMMAND));
            assertThat(id2, is(ID_FIRST_COMMAND));
            assertThat(id3_1, is(ID_FIRST_COMMAND));
            assertThat(id3_2, is(ID_FIRST_COMMAND + 1));
            assertThat(new String(rm1.getBody(), UTF_8), is(equalTo("one")));
            assertThat(new String(rm2.getBody(), UTF_8), is(equalTo("two")));
            assertThat(new String(rm3_1.getBody(), UTF_8), is(equalTo("three")));
            assertThat(new String(rm3_2.getBody(), UTF_8), is(equalTo("four")));

            verify(connectionStateCallback1, times(1)).handle(true);
            verify(connectionStateCallback1, times(1)).handle(false);
            verify(connectionStateCallback2, times(1)).handle(true);
            verify(connectionStateCallback2, times(1)).handle(false);
            verify(connectionStateCallback3, times(1)).handle(true);
            verify(connectionStateCallback3, times(1)).handle(false);
        }
    }

    /**
     * Exists to work around the fact that mocking a BlockingQueue<PollerMessage>
     * requires a cast, which causes "unchecked" warnings, that can only be suppressed...
     */
    class MockPollerQueue extends LinkedBlockingQueue<PollerMessage> {

    }

}

