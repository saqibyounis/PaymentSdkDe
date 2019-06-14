/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;
import static com.miurasystems.miuralibrary.enums.InterfaceType.RPI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.when;

import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.comms.PollerStatusCallback.PollerStatus;
import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@SuppressWarnings("TestMethodWithIncorrectSignature")
@RunWith(Enclosed.class)
public class InputResponsePollerTest {

    private static final boolean UNSOLICITED = true;
    private static final boolean SOLICITED = false;
    private static final byte SW_1_OK = (byte) 0x90;
    private static final byte SW_2_OK = (byte) 0x00;

    /**
     * Exists to work around the fact that mocking a BlockingQueue<PollerMessage>
     * requires a cast, which causes "unchecked" warnings, that can only be suppressed...
     */
    interface BlockingQueuePollerMessage extends BlockingQueue<PollerMessage> {

    }


    public static class ConstructorTest {
        @Test
        public void noNad() {
            // setup
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);
            mockQueues.put(MPI, mock(BlockingQueuePollerMessage.class));

            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(null);
            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            try {
                //noinspection unused
                InputResponsePoller poller = new InputResponsePoller(
                        mockReader, mockQueues,
                        mockUnsolicitedCallback, mockStatusCallback,
                        50L, TimeUnit.MILLISECONDS);
                Assert.fail();
            } catch (IllegalArgumentException ignore) {

            }
        }
    }

    @RunWith(JUnitParamsRunner.class)
    public static class PostResponseTest {

        static final private long DEFAULT_TIMEOUT = 20L;
        static final private TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLISECONDS;
        private InputResponsePoller mPoller;
        private ResponseReader mockReader;
        private EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues;
        private UnsolicitedResponseCallback mockUnsolicitedCallback;
        private PollerStatusCallback mockStatusCallback;

        @Before
        public void setup() {
            mockReader = mock(ResponseReader.class);

            mockQueues = new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                mockQueues.put(nad, mock(BlockingQueuePollerMessage.class));
            }
            mockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mockStatusCallback = mock(PollerStatusCallback.class);

            mPoller = new InputResponsePoller(
                    mockReader,
                    mockQueues,
                    mockUnsolicitedCallback,
                    mockStatusCallback,
                    DEFAULT_TIMEOUT,
                    DEFAULT_TIME_UNIT);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postResponse_normalSingleQueues(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {
            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(mainQueue)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);
            ResponseMessage mockResponse = mock(ResponseMessage.class);
            PollerMessage msg = new PollerMessage(26, mockResponse);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postResponseToQueue(mainQueue, msg);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.Ok)));

            verify(mockQueues.get(mainQueue)).offer(msg, DEFAULT_TIMEOUT, DEFAULT_TIME_UNIT);
            verifyNoMoreInteractions(mockQueues.get(mainQueue));

            verifyZeroInteractions(mockQueues.get(ignoredQueue));

            // check that nothing has changed in the msg.
            assertThat(msg.solicitedResponseId, is(equalTo(26)));
            assertThat(msg.response, is(mockResponse));
            verifyZeroInteractions(mockResponse);

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        public void postResponse_normalAllQueues() throws Exception {
            // setup
            // ------------------------------------------------------------------
            for (InterfaceType e : InterfaceType.values()) {
                when(mockQueues.get(e)
                        .offer(any(PollerMessage.class),
                                eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                        .thenReturn(true);
            }
            ResponseMessage mockResponse = mock(ResponseMessage.class);
            PollerMessage msg = new PollerMessage(26, mockResponse);

            // execute
            // ------------------------------------------------------------------
            PostingStatus mpiPostingStatus = mPoller.postResponseToQueue(MPI, msg);
            PostingStatus rpiPostingStatus = mPoller.postResponseToQueue(RPI, msg);

            // verify
            // ------------------------------------------------------------------
            assertThat(mpiPostingStatus, is(equalTo(PostingStatus.Ok)));
            assertThat(rpiPostingStatus, is(equalTo(PostingStatus.Ok)));

            for (InterfaceType e : InterfaceType.values()) {
                verify(mockQueues.get(e)).offer(msg, DEFAULT_TIMEOUT, DEFAULT_TIME_UNIT);
                verifyNoMoreInteractions(mockQueues.get(e));
            }

            // check that nothing has changed in the msg.
            assertThat(msg.solicitedResponseId, is(equalTo(26)));
            assertThat(msg.response, is(mockResponse));
            verifyZeroInteractions(mockResponse);

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postResponse_offerTimesOut(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {
            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(mainQueue)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(false);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            PollerMessage msg = new PollerMessage(13, mockResponse);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postResponseToQueue(mainQueue, msg);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.TimedOut)));

            verify(mockQueues.get(mainQueue)).offer(msg, DEFAULT_TIMEOUT, DEFAULT_TIME_UNIT);
            verifyNoMoreInteractions(mockQueues.get(mainQueue));
            verifyZeroInteractions(mockQueues.get(ignoredQueue));

            assertThat(msg.solicitedResponseId, is(equalTo(13)));
            assertThat(msg.response, is(mockResponse));

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postResponse_exception(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {
            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(mainQueue)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenThrow(InterruptedException.class);

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            PollerMessage msg = new PollerMessage(22, mockResponse);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postResponseToQueue(mainQueue, msg);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.InterruptedException)));

            verify(mockQueues.get(mainQueue)).offer(msg, DEFAULT_TIMEOUT, DEFAULT_TIME_UNIT);
            verifyNoMoreInteractions(mockQueues.get(mainQueue));
            verifyZeroInteractions(mockQueues.get(ignoredQueue));

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }
    }

    @RunWith(JUnitParamsRunner.class)
    public static class PostTerminalTest {

        static final private long DEFAULT_TIMEOUT = 20L;
        static final private TimeUnit DEFAULT_TIME_UNIT = TimeUnit.MILLISECONDS;
        private InputResponsePoller mPoller;
        private ResponseReader mockReader;
        private EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues;
        private UnsolicitedResponseCallback mockUnsolicitedCallback;
        private PollerStatusCallback mockStatusCallback;


        @Before
        public void setup() {
            mockReader = mock(ResponseReader.class);

            mockQueues = new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                mockQueues.put(nad, mock(BlockingQueuePollerMessage.class));
            }
            mockUnsolicitedCallback = mock(UnsolicitedResponseCallback.class);
            mockStatusCallback = mock(PollerStatusCallback.class);

            mPoller = new InputResponsePoller(
                    mockReader,
                    mockQueues,
                    mockUnsolicitedCallback,
                    mockStatusCallback,
                    DEFAULT_TIMEOUT,
                    DEFAULT_TIME_UNIT);
        }

        @Test
        public void postTerminalMessage_normal() throws Exception {
            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(MPI)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);
            when(mockQueues.get(RPI)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(6);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.Ok)));

            ArgumentCaptor<PollerMessage> mpiCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(MPI))
                    .offer(mpiCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage mpiTerminalMsg = mpiCaptor.getValue();
            assertThat(mpiTerminalMsg.response, is(nullValue()));
            assertThat(mpiTerminalMsg.solicitedResponseId, is(equalTo(6)));
            verifyNoMoreInteractions(mockQueues.get(MPI));

            ArgumentCaptor<PollerMessage> rpiCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(RPI))
                    .offer(rpiCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage rpiTerminalMsg = rpiCaptor.getValue();
            assertThat(rpiTerminalMsg.response, is(nullValue()));
            assertThat(rpiTerminalMsg.solicitedResponseId, is(equalTo(6)));
            verifyNoMoreInteractions(mockQueues.get(RPI));

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postTerminalMessage_timesOutFirstTime(
                InterfaceType bad, InterfaceType good) throws Exception {

            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(bad)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenReturn(true);
            when(mockQueues.get(good)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT),
                            eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(18);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.Ok)));

            ArgumentCaptor<PollerMessage> badCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(bad), times(2))
                    .offer(badCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage badTerminalMsg = badCaptor.getAllValues().get(1);
            assertThat(badTerminalMsg.response, is(nullValue()));
            assertThat(badTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(bad));

            ArgumentCaptor<PollerMessage> goodCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(good), times(1))
                    .offer(goodCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage goodTerminalMsg = goodCaptor.getValue();
            assertThat(goodTerminalMsg.response, is(nullValue()));
            assertThat(goodTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(good));

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        public void postTerminalMessage_bothTimeOutFirstTime() throws Exception {

            // setup
            // ------------------------------------------------------------------

            when(mockQueues.get(InterfaceType.RPI)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenReturn(true);
            when(mockQueues.get(InterfaceType.MPI)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenReturn(true);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(18);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.Ok)));

            ArgumentCaptor<PollerMessage> mpiCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(MPI), times(2))
                    .offer(mpiCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage mpiTerminalMsg = mpiCaptor.getAllValues().get(1);
            assertThat(mpiTerminalMsg.response, is(nullValue()));
            assertThat(mpiTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(MPI));

            ArgumentCaptor<PollerMessage> badCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(RPI), times(2))
                    .offer(badCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage rpiTerminalMsg = badCaptor.getAllValues().get(1);
            assertThat(rpiTerminalMsg.response, is(nullValue()));
            assertThat(rpiTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(RPI));

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postTerminalMessage_interruptsFirstTime(
                InterfaceType bad, InterfaceType good) throws Exception {

            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(bad)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenThrow(InterruptedException.class);
            when(mockQueues.get(good)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(15);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.InterruptedException)));

            ArgumentCaptor<PollerMessage> badCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(bad), times(1))
                    .offer(badCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage badTerminalMsg = badCaptor.getValue();
            assertThat(badTerminalMsg.response, is(nullValue()));
            assertThat(badTerminalMsg.solicitedResponseId, is(equalTo(15)));
            verifyNoMoreInteractions(mockQueues.get(bad));

            ArgumentCaptor<PollerMessage> goodCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(good))
                    .offer(goodCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage goodTerminalMsg = goodCaptor.getValue();
            assertThat(goodTerminalMsg.response, is(nullValue()));
            assertThat(goodTerminalMsg.solicitedResponseId, is(equalTo(15)));
            verifyNoMoreInteractions(mockQueues.get(good));

            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        public void postTerminalMessage_bothInterruptFirstTime() throws Exception {

            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(RPI)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenThrow(InterruptedException.class);
            when(mockQueues.get(MPI)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenThrow(InterruptedException.class);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(18);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.InterruptedException)));

            ArgumentCaptor<PollerMessage> mpiCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(MPI), times(1))
                    .offer(mpiCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage mpiTerminalMsg = mpiCaptor.getValue();
            assertThat(mpiTerminalMsg.response, is(nullValue()));
            assertThat(mpiTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(MPI));

            ArgumentCaptor<PollerMessage> rpiCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(RPI))
                    .offer(rpiCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage rpiTerminalMsg = rpiCaptor.getValue();
            assertThat(rpiTerminalMsg.response, is(nullValue()));
            assertThat(rpiTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(RPI));


            // this might seem like overkill that just makes the test fragile -- but we
            // explicitly don't want to touch the stream etc.
            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postTerminalMessage_timesOutTwice(
                InterfaceType bad, InterfaceType good) throws Exception {
            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(bad)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenReturn(false);
            when(mockQueues.get(good)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(12);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.TimedOut)));

            ArgumentCaptor<PollerMessage> badCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(bad), times(2))
                    .offer(badCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage badTerminalMsg = badCaptor.getAllValues().get(1);
            assertThat(badTerminalMsg.response, is(nullValue()));
            assertThat(badTerminalMsg.solicitedResponseId, is(equalTo(12)));
            verifyNoMoreInteractions(mockQueues.get(bad));

            ArgumentCaptor<PollerMessage> goodCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(good))
                    .offer(goodCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage goodTerminalMsg = goodCaptor.getValue();
            assertThat(goodTerminalMsg.response, is(nullValue()));
            assertThat(goodTerminalMsg.solicitedResponseId, is(equalTo(12)));
            verifyNoMoreInteractions(mockQueues.get(good));

            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        public void postTerminalMessage_bothTimeOutTwice() throws Exception {
            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(MPI)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenReturn(false);
            when(mockQueues.get(RPI)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(12);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.TimedOut)));

            ArgumentCaptor<PollerMessage> mpiCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(MPI), times(2))
                    .offer(mpiCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage mpiTerminalMsg = mpiCaptor.getAllValues().get(1);
            assertThat(mpiTerminalMsg.response, is(nullValue()));
            assertThat(mpiTerminalMsg.solicitedResponseId, is(equalTo(12)));
            verifyNoMoreInteractions(mockQueues.get(MPI));

            ArgumentCaptor<PollerMessage> rpiCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(RPI))
                    .offer(rpiCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage rpiTerminalMsg = rpiCaptor.getValue();
            assertThat(rpiTerminalMsg.response, is(nullValue()));
            assertThat(rpiTerminalMsg.solicitedResponseId, is(equalTo(12)));
            verifyNoMoreInteractions(mockQueues.get(RPI));

            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postTerminalMessage_timesOutThenInterrupts(
                InterfaceType bad, InterfaceType good) throws Exception {
            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(bad)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenThrow(InterruptedException.class);
            when(mockQueues.get(good)
                    .offer(any(PollerMessage.class), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT)))
                    .thenReturn(true);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(18);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.InterruptedException)));

            ArgumentCaptor<PollerMessage> badCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(bad), times(2))
                    .offer(badCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage badTerminalMsg = badCaptor.getAllValues().get(1);
            assertThat(badTerminalMsg.response, is(nullValue()));
            assertThat(badTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(bad));

            ArgumentCaptor<PollerMessage> goodCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(good))
                    .offer(goodCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage goodTerminalMsg = goodCaptor.getValue();
            assertThat(goodTerminalMsg.response, is(nullValue()));
            assertThat(goodTerminalMsg.solicitedResponseId, is(equalTo(18)));
            verifyNoMoreInteractions(mockQueues.get(good));

            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }

        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void postTerminalMessage_bothTimesOutThenInterrupt(
                InterfaceType a, InterfaceType b) throws Exception {
            // needs to be parametrised to ensure order doesn't matter.

            // setup
            // ------------------------------------------------------------------
            when(mockQueues.get(a)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenThrow(InterruptedException.class);
            when(mockQueues.get(b)
                    .offer(any(PollerMessage.class), anyLong(), any(TimeUnit.class)))
                    .thenReturn(false).thenThrow(InterruptedException.class);

            // execute
            // ------------------------------------------------------------------
            PostingStatus postingStatus = mPoller.postTerminalMessageToAllQueues(66);

            // verify
            // ------------------------------------------------------------------
            assertThat(postingStatus, is(equalTo(PostingStatus.InterruptedException)));

            ArgumentCaptor<PollerMessage> aCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(a), atLeastOnce())
                    .offer(aCaptor.capture(), anyLong(), any(TimeUnit.class));
            PollerMessage aTerminalMsg = aCaptor.getAllValues().get(0);
            assertThat(aTerminalMsg.response, is(nullValue()));
            assertThat(aTerminalMsg.solicitedResponseId, is(equalTo(66)));

            ArgumentCaptor<PollerMessage> bCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(b), atLeastOnce())
                    .offer(bCaptor.capture(), eq(DEFAULT_TIMEOUT), eq(DEFAULT_TIME_UNIT));
            PollerMessage bTerminalMsg = bCaptor.getAllValues().get(0);
            assertThat(bTerminalMsg.response, is(nullValue()));
            assertThat(bTerminalMsg.solicitedResponseId, is(equalTo(66)));

            verifyZeroInteractions(mockReader);
            verifyZeroInteractions(mockUnsolicitedCallback);
            verifyZeroInteractions(mockStatusCallback);
        }
    }

    @RunWith(JUnitParamsRunner.class)
    public static class NonThreadedLoops {

        /**
         * \@Test(timeout) runs \@After and \@Before on a different thread.. so don't use them!
         * \@Rule org.junit.rules.Timeout might not run \@After and applies to every method in
         * the class.
         */
        @Rule
        public Timeout globalTimeout = Timeout.seconds(2L);

        @Test
        public void run_empty() throws Exception {
            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(null);
            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                BlockingQueue<PollerMessage> mockQueue = mock(BlockingQueuePollerMessage.class);
                mockQueues.put(nad, mockQueue);
                when(mockQueue
                        .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                        .thenReturn(true);
            }

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            for (InterfaceType nad : InterfaceType.values()) {
                BlockingQueue<PollerMessage> mockQueue = mockQueues.get(nad);

                ArgumentCaptor<PollerMessage> captor = ArgumentCaptor.forClass(PollerMessage.class);
                verify(mockQueue).offer(captor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

                PollerMessage actual = captor.getValue();
                assertThat(actual.response, is(nullValue()));
                assertThat(actual.solicitedResponseId, is(equalTo(-1)));

                verifyNoMoreInteractions(mockQueue);
            }

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, -1);
        }

        /** msg; msg; msg; EOF; terminal -- queue works fine */
        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_normalEof(InterfaceType mainQueue, InterfaceType ignoredQueue)
                throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x2, SW_1_OK, SW_2_OK}),
                    null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                BlockingQueue<PollerMessage> mockQueue = mock(BlockingQueuePollerMessage.class);
                mockQueues.put(nad, mockQueue);
                when(mockQueue
                        .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                        .thenReturn(true);
            }

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            // check mainQueue for messages
            ArgumentCaptor<PollerMessage> mainCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(mainQueue), times(4))
                    .offer(mainCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allMainValues = mainCaptor.getAllValues();
            for (int i = 0; i < 3; i++) {
                assertThat(allMainValues.get(i).solicitedResponseId, is(equalTo(i)));
                assertThat(allMainValues.get(i).response, is(notNullValue()));
                //noinspection ConstantConditions
                assertThat(allMainValues.get(i).response.getBody()[0], is(equalTo((byte) i)));
            }

            // check for TERMINAL_MESSAGE
            assertThat(allMainValues.get(3).solicitedResponseId, is(equalTo(2)));
            assertThat(allMainValues.get(3).response, is(nullValue()));
            verifyNoMoreInteractions(mockQueues.get(mainQueue));

            // check for TERMINAL_MESSAGE on ignoredQueue
            ArgumentCaptor<PollerMessage> secondCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(ignoredQueue))
                    .offer(secondCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage rpiTermMessage = secondCaptor.getValue();
            assertThat(rpiTermMessage.solicitedResponseId, is(equalTo(2)));
            assertThat(rpiTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockQueues.get(ignoredQueue));

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 2);
        }

        /** msg; msg; msg; EOF; terminal -- queue works fine */
        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_normalEofMixed(InterfaceType queueA, InterfaceType queueB)
                throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(queueA, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(queueB, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(queueB, SOLICITED, new byte[]{0x2, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(queueA, SOLICITED, new byte[]{0x3, SW_1_OK, SW_2_OK}),
                    null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                BlockingQueue<PollerMessage> mockQueue = mock(BlockingQueuePollerMessage.class);
                mockQueues.put(nad, mockQueue);
                when(mockQueue
                        .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                        .thenReturn(true);
            }

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            // check queueA for messages
            ArgumentCaptor<PollerMessage> captorA = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(queueA), times(3))
                    .offer(captorA.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allValuesA = captorA.getAllValues();
            assertThat(allValuesA.get(0).solicitedResponseId, is(equalTo(0)));
            assertThat(allValuesA.get(0).response, is(notNullValue()));
            //noinspection ConstantConditions
            assertThat(allValuesA.get(0).response.getBody()[0], is(equalTo((byte) 0)));
            assertThat(allValuesA.get(1).solicitedResponseId, is(equalTo(3)));
            assertThat(allValuesA.get(1).response, is(notNullValue()));
            //noinspection ConstantConditions
            assertThat(allValuesA.get(1).response.getBody()[0], is(equalTo((byte) 3)));
            assertThat(allValuesA.get(2).solicitedResponseId, is(equalTo(3)));
            assertThat(allValuesA.get(2).response, is(nullValue()));

            verifyNoMoreInteractions(mockQueues.get(queueA));

            // check queueB for messages
            ArgumentCaptor<PollerMessage> captorB =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(queueB), times(3))
                    .offer(captorB.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allValuesB = captorB.getAllValues();

            assertThat(allValuesB.get(0).solicitedResponseId, is(equalTo(1)));
            //noinspection ConstantConditions
            assertThat(allValuesB.get(0).response.getBody()[0], is(equalTo((byte) 1)));
            assertThat(allValuesB.get(1).solicitedResponseId, is(equalTo(2)));
            //noinspection ConstantConditions
            assertThat(allValuesB.get(1).response.getBody()[0], is(equalTo((byte) 2)));
            assertThat(allValuesB.get(2).solicitedResponseId, is(equalTo(3)));
            assertThat(allValuesB.get(2).response, is(nullValue()));

            verifyNoMoreInteractions(mockQueues.get(queueB));

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 3);
        }

        /** msg; msg; msg but queue times out; queue works for terminal */
        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_timeoutInMsgPost(InterfaceType mainQueue, InterfaceType ignoredQueue)
                throws Exception {
            // setup
            // ---------------------------------------------9---------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x2, SW_1_OK, SW_2_OK})
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues. mainQueue gets messages + terminal. ignoredQueue gets terminal.
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);

            // true=msg | EOF, false=queue timesout
            BlockingQueue<PollerMessage> mockMainQueue = mock(BlockingQueuePollerMessage.class);
            when(mockMainQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true, true, false, true);
            mockQueues.put(mainQueue, mockMainQueue);

            BlockingQueue<PollerMessage> mockIgnoredQueue = mock(BlockingQueuePollerMessage.class);
            when(mockIgnoredQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(ignoredQueue, mockIgnoredQueue);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            ArgumentCaptor<PollerMessage> mainCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockMainQueue, times(4))
                    .offer(mainCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allMainValues = mainCaptor.getAllValues();
            for (int i = 0; i < 2; i++) {
                assertThat(allMainValues.get(i).solicitedResponseId, is(equalTo(i)));
                //noinspection ConstantConditions
                assertThat(allMainValues.get(i).response.getBody()[0], is(equalTo((byte) i)));
            }

            // This one got posted but timed out, so it is id 2
            assertThat(allMainValues.get(2).solicitedResponseId, is(equalTo(2)));
            //noinspection ConstantConditions
            assertThat(allMainValues.get(2).response.getBody()[0], is(equalTo((byte) 2)));

            // notice how id is 1 here, as that was the last successful id
            assertThat(allMainValues.get(3).solicitedResponseId, is(equalTo(1)));
            assertThat(allMainValues.get(3).response, is(nullValue()));

            verifyNoMoreInteractions(mockMainQueue);

            // check for TERMINAL_MESSAGE
            ArgumentCaptor<PollerMessage> ignoredCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockIgnoredQueue)
                    .offer(ignoredCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage ignoredTermMessage = ignoredCaptor.getValue();
            assertThat(ignoredTermMessage.solicitedResponseId, is(equalTo(1)));
            assertThat(ignoredTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockIgnoredQueue);

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedQueuePostTimedOut, 1);
        }

        /**
         * msg; msg; msg but queue times out; queue times out for terminal x2
         */
        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_timeoutInMsgPostAndTerminal(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x2, SW_1_OK, SW_2_OK})
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues. MPI gets messages + terminal. RPI gets terminal.
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);

            // true=msg | EOF, false=queue timesout
            BlockingQueue<PollerMessage> mockMainQueue = mock(BlockingQueuePollerMessage.class);
            when(mockMainQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true, true, false, false);
            mockQueues.put(mainQueue, mockMainQueue);

            BlockingQueue<PollerMessage> mockIgnoredQueue = mock(BlockingQueuePollerMessage.class);
            when(mockIgnoredQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(ignoredQueue, mockIgnoredQueue);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            ArgumentCaptor<PollerMessage> captor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockMainQueue, times(4))
                    .offer(captor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allValues = captor.getAllValues();
            for (int i = 0; i < 2; i++) {
                assertThat(allValues.get(i).solicitedResponseId, is(equalTo(i)));
                assertThat(allValues.get(i).response, is(notNullValue()));
                //noinspection ConstantConditions
                assertThat(allValues.get(i).response.getBody()[0], is(equalTo((byte) i)));
            }

            // This one got posted but timed out, so it is id 2
            assertThat(allValues.get(2).solicitedResponseId, is(equalTo(2)));
            assertThat(allValues.get(2).response, is(notNullValue()));
            //noinspection ConstantConditions
            assertThat(allValues.get(2).response.getBody()[0], is(equalTo((byte) 2)));

            // notice how id is 1 is here, as that was the last successful id
            // this times out
            assertThat(allValues.get(3).solicitedResponseId, is(equalTo(1)));
            assertThat(allValues.get(3).response, is(nullValue()));

            // it's attempted to be posted again, which fails again
            verify(mockMainQueue, times(1))
                    .offer(captor.capture(), not(eq(50L)), any(TimeUnit.class));
            assertThat(captor.getValue().solicitedResponseId, is(equalTo(1)));
            assertThat(captor.getValue().response, is(nullValue()));

            verifyNoMoreInteractions(mockMainQueue);

            // check for IGNORED's TERMINAL_MESSAGE
            ArgumentCaptor<PollerMessage> ignoredCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockIgnoredQueue)
                    .offer(ignoredCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage ignoredTermMessage = ignoredCaptor.getValue();
            assertThat(ignoredTermMessage.solicitedResponseId, is(equalTo(1)));
            assertThat(ignoredTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockIgnoredQueue);

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedQueuePostTimedOut, 1);
        }


        /** msg msg EOF; time out for terminal x1 */
        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_eofThenTerminalTimesOutOnce(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues. MAIN gets messages + terminal. IGNORED gets terminal.
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);

            // true=msg | EOF, false=queue timesout
            BlockingQueue<PollerMessage> mockMainQueue = mock(BlockingQueuePollerMessage.class);
            when(mockMainQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true, true, false, true);
            mockQueues.put(mainQueue, mockMainQueue);

            BlockingQueue<PollerMessage> mockIgnoredQueue = mock(BlockingQueuePollerMessage.class);
            when(mockIgnoredQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(ignoredQueue, mockIgnoredQueue);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            ArgumentCaptor<PollerMessage> captor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockMainQueue, times(3))
                    .offer(captor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allValues = captor.getAllValues();
            for (int i = 0; i < 2; i++) {
                assertThat(allValues.get(i).solicitedResponseId, is(equalTo(i)));
                assertThat(allValues.get(i).response, is(notNullValue()));
                //noinspection ConstantConditions
                assertThat(allValues.get(i).response.getBody()[0], is(equalTo((byte) i)));
            }

            // notice how id is 1 here, as that was the last successful id
            // this times out
            assertThat(allValues.get(2).solicitedResponseId, is(equalTo(1)));
            assertThat(allValues.get(2).response, is(nullValue()));

            // it's attempted to be posted again, which fails again
            verify(mockMainQueue, times(1))
                    .offer(captor.capture(), not(eq(50L)), any(TimeUnit.class));
            assertThat(captor.getValue().solicitedResponseId, is(equalTo(1)));
            assertThat(captor.getValue().response, is(nullValue()));

            verifyNoMoreInteractions(mockMainQueue);

            // check for IGNORED's TERMINAL_MESSAGE
            ArgumentCaptor<PollerMessage> ignoredCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockIgnoredQueue)
                    .offer(ignoredCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage ignoredTermMessage = ignoredCaptor.getValue();
            assertThat(ignoredTermMessage.solicitedResponseId, is(equalTo(1)));
            assertThat(ignoredTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockIgnoredQueue);

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 1);
        }


        /** msg; msg; msg but queue interrupts; queue works ok for terminal */
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_interruptInMsgPost(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {
            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x2, SW_1_OK, SW_2_OK})
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues. MAIN gets messages + terminal. IGNORED gets terminal.
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);

            // true=msg | EOF, false=queue timesout
            BlockingQueue<PollerMessage> mockMainQueue = mock(BlockingQueuePollerMessage.class);
            when(mockMainQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true, true)
                    .thenThrow(InterruptedException.class)
                    .thenReturn(true);
            mockQueues.put(mainQueue, mockMainQueue);

            BlockingQueue<PollerMessage> mockIgnoredQueue = mock(BlockingQueuePollerMessage.class);
            when(mockIgnoredQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(ignoredQueue, mockIgnoredQueue);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            ArgumentCaptor<PollerMessage> captor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockMainQueue, times(4))
                    .offer(captor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allValues = captor.getAllValues();
            for (int i = 0; i < 2; i++) {
                assertThat(allValues.get(i).solicitedResponseId, is(equalTo(i)));
                assertThat(allValues.get(i).response, is(notNullValue()));
                //noinspection ConstantConditions
                assertThat(allValues.get(i).response.getBody()[0], is(equalTo((byte) i)));
            }

            // This one got posted but interrupted
            assertThat(allValues.get(2).solicitedResponseId, is(equalTo(2)));
            assertThat(allValues.get(2).response, is(notNullValue()));
            //noinspection ConstantConditions
            assertThat(allValues.get(2).response.getBody()[0], is(equalTo((byte) 2)));

            // notice how id is 1 here, as that was the last successful id
            assertThat(allValues.get(3).solicitedResponseId, is(equalTo(1)));
            assertThat(allValues.get(3).response, is(nullValue()));

            verifyNoMoreInteractions(mockMainQueue);

            // check for IGNORED's TERMINAL_MESSAGE
            ArgumentCaptor<PollerMessage> ignoredCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockIgnoredQueue)
                    .offer(ignoredCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage ignoredTermMessage = ignoredCaptor.getValue();
            assertThat(ignoredTermMessage.solicitedResponseId, is(equalTo(1)));
            assertThat(ignoredTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockIgnoredQueue);

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1))
                    .handle(PollerStatus.StoppedQueuePostInterrupted, 1);
        }

        /** msg; msg; msg; EOF; queue interrupts for terminal */
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_interruptInTerminal(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {
            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x2, SW_1_OK, SW_2_OK}),
                    null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues. MAIN gets messages + terminal. IGNORED gets terminal.
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);

            // true=msg | EOF, false=queue timesout
            BlockingQueue<PollerMessage> mockMainQueue = mock(BlockingQueuePollerMessage.class);
            when(mockMainQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true, true, true) //msg, msg, msg
                    .thenThrow(InterruptedException.class); // terminal interrupted
            mockQueues.put(mainQueue, mockMainQueue);

            BlockingQueue<PollerMessage> mockIgnoredQueue = mock(BlockingQueuePollerMessage.class);
            when(mockIgnoredQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(ignoredQueue, mockIgnoredQueue);


            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            ArgumentCaptor<PollerMessage> captor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockMainQueue, times(4))
                    .offer(captor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allValues = captor.getAllValues();
            for (int i = 0; i < 3; i++) {
                assertThat(allValues.get(i).solicitedResponseId, is(equalTo(i)));
                assertThat(allValues.get(i).response, is(notNullValue()));
                //noinspection ConstantConditions
                assertThat(allValues.get(i).response.getBody()[0], is(equalTo((byte) i)));
            }

            // whilst this is actually interrupted, the post call still happened.
            assertThat(allValues.get(3).solicitedResponseId, is(equalTo(2)));
            assertThat(allValues.get(3).response, is(nullValue()));

            verifyNoMoreInteractions(mockMainQueue);

            // check for IGNORED's TERMINAL_MESSAGE
            ArgumentCaptor<PollerMessage> ignoredCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockIgnoredQueue)
                    .offer(ignoredCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage ignoredTermMessage = ignoredCaptor.getValue();
            assertThat(ignoredTermMessage.solicitedResponseId, is(equalTo(2)));
            assertThat(ignoredTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockIgnoredQueue);


            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 2);
        }

        /** msg, umsg, msg, umsg, EOF, terminal -- queue works fine */
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_normalUnsolicitedEof(
                InterfaceType mainQueue, InterfaceType ignoredQueue) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, UNSOLICITED, new byte[]{0x70, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, UNSOLICITED, new byte[]{0x71, SW_1_OK, SW_2_OK}),
                    null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues. MAIN gets messages + terminal. IGNORED gets terminal.
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);

            // true=msg | EOF, false=queue timesout
            BlockingQueue<PollerMessage> mockMainQueue = mock(BlockingQueuePollerMessage.class);
            when(mockMainQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(mainQueue, mockMainQueue);

            BlockingQueue<PollerMessage> mockIgnoredQueue = mock(BlockingQueuePollerMessage.class);
            when(mockIgnoredQueue
                    .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(ignoredQueue, mockIgnoredQueue);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            ArgumentCaptor<PollerMessage> captor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockMainQueue, times(3))
                    .offer(captor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allSolicitedMsgs = captor.getAllValues();
            for (int i = 0; i < 2; i++) {
                assertThat(allSolicitedMsgs.get(i).solicitedResponseId, is(equalTo(i)));
                assertThat(allSolicitedMsgs.get(i).response, is(notNullValue()));
                //noinspection ConstantConditions
                assertThat(allSolicitedMsgs.get(i).response.getBody()[0], is(equalTo((byte) i)));
            }
            assertThat(allSolicitedMsgs.get(2).solicitedResponseId, is(equalTo(1)));
            assertThat(allSolicitedMsgs.get(2).response, is(nullValue()));
            verifyNoMoreInteractions(mockMainQueue);

            // check for IGNORED's TERMINAL_MESSAGE
            ArgumentCaptor<PollerMessage> ignoredCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockIgnoredQueue)
                    .offer(ignoredCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage ignoredTermMessage = ignoredCaptor.getValue();
            assertThat(ignoredTermMessage.solicitedResponseId, is(equalTo(1)));
            assertThat(ignoredTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockIgnoredQueue);

            // check for unsolicited messages
            ArgumentCaptor<PollerMessage> msgCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockUnsolicitedCallback, times(2)).handle(msgCaptor.capture());
            List<PollerMessage> allUnsolicitedMsgs = msgCaptor.getAllValues();
            for (int i = 0; i < 2; i++) {
                assertThat(allUnsolicitedMsgs.get(i).solicitedResponseId, is(equalTo(i)));
                assertThat(allUnsolicitedMsgs.get(i).response, is(notNullValue()));
                byte body = (byte) (0x70 | i);
                //noinspection ConstantConditions
                assertThat(allUnsolicitedMsgs.get(i).response.getBody()[0], is(equalTo(body)));
            }

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 1);
        }


        /**
         * msg; bad cmd msg; umsg; msg; EOF; terminal -- queue works fine, bad command is treated
         * as a response to a normal command
         */
        @SuppressWarnings("ConstantConditions")
        @Test
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void run_normalBadCommandEof(InterfaceType mainQueue, InterfaceType ignoredQueue)
                throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x0, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, UNSOLICITED, new byte[]{0x6f, 0x00}),
                    new ResponseMessage(mainQueue, UNSOLICITED, new byte[]{0x71, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainQueue, SOLICITED, new byte[]{0x1, SW_1_OK, SW_2_OK}),
                    null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                BlockingQueue<PollerMessage> mockQueue = mock(BlockingQueuePollerMessage.class);
                mockQueues.put(nad, mockQueue);
                when(mockQueue
                        .offer(any(PollerMessage.class), eq(50L), eq(TimeUnit.MILLISECONDS)))
                        .thenReturn(true);
            }

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            // check mainQueue for messages
            ArgumentCaptor<PollerMessage> mainCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(mainQueue), times(4))
                    .offer(mainCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allMainValues = mainCaptor.getAllValues();
            PollerMessage firstSolicitedMsg = allMainValues.get(0);
            assertThat(firstSolicitedMsg.solicitedResponseId, is(equalTo(0)));
            assertThat(firstSolicitedMsg.response, is(notNullValue()));
            assertThat(firstSolicitedMsg.response.getBody()[0], is(equalTo((byte) 0)));
            assertThat(firstSolicitedMsg.response.isSuccess(), is(true));
            assertThat(firstSolicitedMsg.response.isUnsolicited(), is(false));

            // check for BAD MESSAGE!
            PollerMessage badCommand = allMainValues.get(1);
            assertThat(badCommand.solicitedResponseId, is(equalTo(1)));
            assertThat(badCommand.response, is(notNullValue()));
            assertThat(badCommand.response.getBody(), is(equalTo(new byte[0])));
            assertThat(badCommand.response.getStatusCode(), is(equalTo(0x6f00)));
            assertThat(badCommand.response.isUnsolicited(), is(true));

            PollerMessage secondSolicitedMsg = allMainValues.get(2);
            assertThat(secondSolicitedMsg.solicitedResponseId, is(equalTo(2)));
            assertThat(secondSolicitedMsg.response, is(notNullValue()));
            assertThat(secondSolicitedMsg.response.getBody()[0], is(equalTo((byte) 1)));
            assertThat(secondSolicitedMsg.response.isSuccess(), is(true));
            assertThat(secondSolicitedMsg.response.isUnsolicited(), is(false));

            // check for TERMINAL_MESSAGE
            PollerMessage terminalMsg = allMainValues.get(3);
            assertThat(terminalMsg.solicitedResponseId, is(equalTo(2)));
            assertThat(terminalMsg.response, is(nullValue()));
            verifyNoMoreInteractions(mockQueues.get(mainQueue));


            // check for unsolicited messages in callback
            ArgumentCaptor<PollerMessage> msgCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockUnsolicitedCallback, times(1)).handle(msgCaptor.capture());

            PollerMessage unsolicitedMsg = msgCaptor.getValue();
            assertThat(unsolicitedMsg.solicitedResponseId, is(equalTo(1)));
            assertThat(unsolicitedMsg.response, is(notNullValue()));
            assertThat(unsolicitedMsg.response.getBody()[0], is(equalTo((byte) (0x71))));

            // check for TERMINAL_MESSAGE on ignoredQueue
            ArgumentCaptor<PollerMessage> secondCaptor =
                    ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(ignoredQueue))
                    .offer(secondCaptor.capture(), eq(50L), eq(TimeUnit.MILLISECONDS));
            PollerMessage rpiTermMessage = secondCaptor.getValue();
            assertThat(rpiTermMessage.solicitedResponseId, is(equalTo(2)));
            assertThat(rpiTermMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockQueues.get(ignoredQueue));

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 2);
        }

    }

    @RunWith(JUnitParamsRunner.class)
    public static class Threaded {

        /**
         * Run a simple threaded test.
         *
         * Set a junit timeout, in-case everything goes haywire
         *
         * \@Test(timeout) runs \@After and \@Before on a different thread.. so don't use them!
         */
        @Test(timeout = 3000L)
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void simple(InterfaceType mainType, InterfaceType secondType) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x11, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x22, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x33, SW_1_OK, SW_2_OK}),
                    null
            );

            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> queueMap =
                    new EnumMap<>(InterfaceType.class);
            LinkedBlockingQueue<PollerMessage> mainQueue = new LinkedBlockingQueue<>(3);
            LinkedBlockingQueue<PollerMessage> secondQueue = new LinkedBlockingQueue<>(3);
            queueMap.put(mainType, mainQueue);
            queueMap.put(secondType, secondQueue);

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, queueMap,
                    mockUnsolicitedCallback, mockStatusCallback,
                    100L, TimeUnit.MILLISECONDS);
            Thread thread = new Thread(poller);

            // ------------------------------------------------------------------
            // execute! Threaded!
            // ------------------------------------------------------------------
            thread.start();

            // it's better to use "take" or poll(timeout) rather than "poll" for first one
            // as the thread might not have started up, which gets us out of "sync"
            PollerMessage msgA = mainQueue.take();
            PollerMessage msgB = mainQueue.take();
            PollerMessage msgC = mainQueue.take();
            PollerMessage mainTerminal = mainQueue.take();
            PollerMessage secondTerminal = secondQueue.take();

            // Stream should read EOF, we should get status callback, and it should exit.
            thread.join();

            // verify
            // ------------------------------------------------------------------
            verifyZeroInteractions(mockUnsolicitedCallback);

            assertThat(msgA.solicitedResponseId, is(equalTo(0)));
            assertThat(msgB.solicitedResponseId, is(equalTo(1)));
            assertThat(msgC.solicitedResponseId, is(equalTo(2)));
            assertThat(mainTerminal.solicitedResponseId, is(equalTo(2)));
            assertThat(secondTerminal.solicitedResponseId, is(equalTo(2)));

            assertThat(msgA.response, is(notNullValue()));
            assertThat(msgB.response, is(notNullValue()));
            assertThat(msgC.response, is(notNullValue()));
            assertThat(mainTerminal.response, is(nullValue()));
            assertThat(secondTerminal.response, is(nullValue()));

            assert msgA.response != null;
            assert msgB.response != null;
            assert msgC.response != null;
            assertThat(msgA.response.getBody(), is(equalTo(new byte[]{0x11})));
            assertThat(msgB.response.getBody(), is(equalTo(new byte[]{0x22})));
            assertThat(msgC.response.getBody(), is(equalTo(new byte[]{0x33})));

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 2);
        }

        /**
         * Run a simple threaded test, but sending to both queues.
         *
         * Set a junit timeout, in-case everything goes haywire
         *
         * \@Test(timeout) runs \@After and \@Before on a different thread.. so don't use them!
         */
        @Test(timeout = 3000L)
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void simpleMixed(InterfaceType mainType, InterfaceType secondType) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x11, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(secondType, SOLICITED, new byte[]{0x22, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(secondType, SOLICITED, new byte[]{0x33, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x44, SW_1_OK, SW_2_OK}),
                    null
            );

            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> queueMap =
                    new EnumMap<>(InterfaceType.class);
            LinkedBlockingQueue<PollerMessage> mainQueue = new LinkedBlockingQueue<>(3);
            LinkedBlockingQueue<PollerMessage> secondQueue = new LinkedBlockingQueue<>(3);
            queueMap.put(mainType, mainQueue);
            queueMap.put(secondType, secondQueue);

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, queueMap,
                    mockUnsolicitedCallback, mockStatusCallback,
                    100L, TimeUnit.MILLISECONDS);
            Thread thread = new Thread(poller);

            // ------------------------------------------------------------------
            // execute! Threaded!
            // ------------------------------------------------------------------
            thread.start();

            // it's better to use "take" or poll(timeout) rather than "poll" for first one
            // as the thread might not have started up, which gets us out of "sync"
            PollerMessage msgA = mainQueue.take();
            PollerMessage msgB = secondQueue.take();
            PollerMessage msgC = secondQueue.take();
            PollerMessage msgD = mainQueue.take();
            PollerMessage mainTerminal = mainQueue.take();
            PollerMessage secondTerminal = secondQueue.take();

            // Stream should read EOF, we should get status callback, and it should exit.
            thread.join();

            // verify
            // ------------------------------------------------------------------
            verifyZeroInteractions(mockUnsolicitedCallback);

            assertThat(msgA.solicitedResponseId, is(equalTo(0)));
            assertThat(msgB.solicitedResponseId, is(equalTo(1)));
            assertThat(msgC.solicitedResponseId, is(equalTo(2)));
            assertThat(msgD.solicitedResponseId, is(equalTo(3)));
            assertThat(mainTerminal.solicitedResponseId, is(equalTo(3)));
            assertThat(secondTerminal.solicitedResponseId, is(equalTo(3)));

            assertThat(msgA.response, is(notNullValue()));
            assertThat(msgB.response, is(notNullValue()));
            assertThat(msgC.response, is(notNullValue()));
            assertThat(msgD.response, is(notNullValue()));
            assertThat(mainTerminal.response, is(nullValue()));
            assertThat(secondTerminal.response, is(nullValue()));

            assert msgA.response != null;
            assert msgB.response != null;
            assert msgC.response != null;
            assert msgD.response != null;
            assertThat(msgA.response.getBody(), is(equalTo(new byte[]{0x11})));
            assertThat(msgB.response.getBody(), is(equalTo(new byte[]{0x22})));
            assertThat(msgC.response.getBody(), is(equalTo(new byte[]{0x33})));
            assertThat(msgD.response.getBody(), is(equalTo(new byte[]{0x44})));

            assertThat(msgA.response.getNodeAddress(), is(equalTo(mainType)));
            assertThat(msgB.response.getNodeAddress(), is(equalTo(secondType)));
            assertThat(msgC.response.getNodeAddress(), is(equalTo(secondType)));
            assertThat(msgD.response.getNodeAddress(), is(equalTo(mainType)));

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 3);
        }


        /**
         * Run a simple threaded test that uses nothing but unsolicited messages
         *
         * Set a junit timeout, in-case everything goes haywire
         *
         * \@Test(timeout) runs \@After and \@Before on a different thread.. so don't use them!
         */
        @Test(timeout = 3000L)
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void allUnsolicited(
                InterfaceType mainType, InterfaceType secondType) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainType, UNSOLICITED, new byte[]{0x11, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, UNSOLICITED, new byte[]{0x22, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, UNSOLICITED, new byte[]{0x33, SW_1_OK, SW_2_OK}),
                    null
            );

            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> queueMap =
                    new EnumMap<>(InterfaceType.class);
            LinkedBlockingQueue<PollerMessage> mainQueue = new LinkedBlockingQueue<>(3);
            LinkedBlockingQueue<PollerMessage> secondQueue = new LinkedBlockingQueue<>(3);
            queueMap.put(mainType, mainQueue);
            queueMap.put(secondType, secondQueue);

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);

            final List<PollerMessage> unsolicitedMessages = new ArrayList<>(3);

            doAnswer(new Answer<Void>() {
                @Nullable
                @Override
                public Void answer(InvocationOnMock invocation) throws Throwable {
                    Object o = invocation.getArguments()[0];
                    unsolicitedMessages.add((PollerMessage) o);
                    return null;
                }
            }).when(mockUnsolicitedCallback).handle(any(PollerMessage.class));


            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, queueMap,
                    mockUnsolicitedCallback, mockStatusCallback,
                    100L, TimeUnit.MILLISECONDS);
            Thread thread = new Thread(poller);

            // ------------------------------------------------------------------
            // execute! Threaded!
            // ------------------------------------------------------------------
            thread.start();

            // it's better to use "take" or poll(timeout) rather than "poll" for first one
            // as the thread might not have started up, which gets us out of "sync"
            PollerMessage mainTerminal = mainQueue.take();
            PollerMessage secondTerminal = secondQueue.take();

            // Stream should read EOF, we should get status callback, and it should exit.
            thread.join();

            // verify
            // ------------------------------------------------------------------
            // all -1 for ids, as there's no solicited messages!

            PollerMessage msgA = unsolicitedMessages.get(0);
            PollerMessage msgB = unsolicitedMessages.get(1);
            PollerMessage msgC = unsolicitedMessages.get(2);

            assertThat(msgA.solicitedResponseId, is(equalTo(-1)));
            assertThat(msgB.solicitedResponseId, is(equalTo(-1)));
            assertThat(msgC.solicitedResponseId, is(equalTo(-1)));
            assertThat(mainTerminal.solicitedResponseId, is(equalTo(-1)));
            assertThat(secondTerminal.solicitedResponseId, is(equalTo(-1)));

            assertThat(msgA.response, is(notNullValue()));
            assertThat(msgB.response, is(notNullValue()));
            assertThat(msgC.response, is(notNullValue()));
            assertThat(mainTerminal.response, is(nullValue()));
            assertThat(secondTerminal.response, is(nullValue()));

            assert msgA.response != null;
            assert msgB.response != null;
            assert msgC.response != null;
            assertThat(msgA.response.getBody(), is(equalTo(new byte[]{0x11})));
            assertThat(msgB.response.getBody(), is(equalTo(new byte[]{0x22})));
            assertThat(msgC.response.getBody(), is(equalTo(new byte[]{0x33})));

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, -1);
        }

        /**
         * Interrupt the thread whilst it's offering to queue
         *
         * Use a SynchronousQueue, but don't .take from it so that the thread is sat there waiting.
         * Then .interrupt it.
         *
         * Set a junit timeout, in-case everything goes haywire.
         *
         * \@Test(timeout) runs \@After and \@Before on a different thread.. so don't use them!
         */
        @Test(timeout = 3000L)
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void interruptedQueue(
                InterfaceType mainType, InterfaceType secondType) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x11, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x22, SW_1_OK, SW_2_OK}),
                    null
            );

            // SynchronousQueue = one in one out
            // be careful to use it only with one queue, otherwise we could easily deadlock
            // due to the parametrised tests
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> queueMap =
                    new EnumMap<>(InterfaceType.class);
            SynchronousQueue<PollerMessage> mainQueue = new SynchronousQueue<>();
            LinkedBlockingQueue<PollerMessage> secondQueue = new LinkedBlockingQueue<>();
            queueMap.put(mainType, mainQueue);
            queueMap.put(secondType, secondQueue);


            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, queueMap,
                    mockUnsolicitedCallback, mockStatusCallback,
                    250L, TimeUnit.MILLISECONDS);
            Thread thread = new Thread(poller);

            // ------------------------------------------------------------------
            // execute! Threaded!
            // ------------------------------------------------------------------
            thread.start();

            /*
                Take the first one. Then let the next one sit in it's timeout period
                and .interrupt it. Then be sure to .take the terminal message, just to avoid
                a pointless wait.
            */
            PollerMessage msgA = mainQueue.take();

            // wait N ms to be sure the thread is "into" its timeout period
            Thread.sleep(100L);
            thread.interrupt();

            // wait enough to bust the poller's original timeout if we're in it
            // (in-case `.interrupt()` didn't work) but also be safely in the
            // terminal message timeout zone.
            Thread.sleep(200L);
            PollerMessage mainTerminal = mainQueue.take();
            PollerMessage secondTerminal = secondQueue.take();

            // Stream should read EOF, we should get status callback, and it should exit.
            thread.join();

            // verify
            // ------------------------------------------------------------------
            verifyZeroInteractions(mockUnsolicitedCallback);

            assert msgA.response != null;
            assertThat(msgA.solicitedResponseId, is(equalTo(0)));
            assertThat(msgA.response, is(notNullValue()));
            assertThat(msgA.response.getBody(), is(equalTo(new byte[]{0x11})));

            assertThat(mainTerminal.response, is(nullValue()));
            assertThat(mainTerminal.solicitedResponseId, is(equalTo(0)));

            assertThat(secondTerminal.response, is(nullValue()));
            assertThat(secondTerminal.solicitedResponseId, is(equalTo(0)));

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(
                    PollerStatus.StoppedQueuePostInterrupted, 0);
        }

        /**
         * Let the thread timeout whilst it's offering to queue
         *
         * Use a SynchronousQueue, but don't .take from it so that the thread is sat there waiting.
         *
         * Set a junit timeout, in-case everything goes haywire.
         *
         * \@Test(timeout) runs \@After and \@Before on a different thread.. so don't use them!
         */
        @Test(timeout = 3000L)
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void timeoutQueue(
                InterfaceType mainType, InterfaceType secondType) throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x11, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x22, SW_1_OK, SW_2_OK}),
                    null
            );

            // SynchronousQueue = one in one out
            // be careful to use it only with one queue, otherwise we could easily deadlock
            // due to the parameterised tests
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> queueMap =
                    new EnumMap<>(InterfaceType.class);
            SynchronousQueue<PollerMessage> mainQueue = new SynchronousQueue<>();
            LinkedBlockingQueue<PollerMessage> secondQueue = new LinkedBlockingQueue<>();
            queueMap.put(mainType, mainQueue);
            queueMap.put(secondType, secondQueue);

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, queueMap,
                    mockUnsolicitedCallback, mockStatusCallback,
                    100L, TimeUnit.MILLISECONDS);
            Thread thread = new Thread(poller);

            // ------------------------------------------------------------------
            // execute! Threaded!
            // ------------------------------------------------------------------
            thread.start();

            /*
                Take the first one. Then ignore the second one.
                Then be sure to .take the terminal message, just to avoid
                a pointless wait.
            */
            PollerMessage msgA = mainQueue.take();

            // wait N ms to be sure the thread is past its timeout period
            // and into the timeout period for the terminal message. We could
            // let that timeout as well but this is a faster test.
            Thread.sleep(150L);
            PollerMessage mainTerminal = mainQueue.take();
            PollerMessage secondTerminal = secondQueue.take();

            // Stream should read EOF, we should get status callback, and it should exit.
            thread.join();

            // verify
            // ------------------------------------------------------------------
            verifyZeroInteractions(mockUnsolicitedCallback);


            assert msgA.response != null;
            assertThat(msgA.solicitedResponseId, is(equalTo(0)));
            assertThat(msgA.response.getBody(), is(equalTo(new byte[]{0x11})));
            assertThat(msgA.response, is(notNullValue()));

            assertThat(mainTerminal.response, is(nullValue()));
            assertThat(mainTerminal.solicitedResponseId, is(equalTo(0)));

            assertThat(secondTerminal.response, is(nullValue()));
            assertThat(secondTerminal.solicitedResponseId, is(equalTo(0)));

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(
                    PollerStatus.StoppedQueuePostTimedOut, 0);
        }

        /**
         * Kill a thread (by closing connector) whilst it is blocked on its input stream
         *
         * Note that we're not using a real blocked input stream, e.g. a ClientSocketConnector, but
         * just simulating one and its return value.
         *
         * Set a junit timeout, in-case everything goes haywire
         *
         * \@Test(timeout) runs \@After and \@Before on a different thread.. so don't use them!
         */
        @Test(timeout = 3000L)
        @Parameters({
                "MPI, RPI",
                "RPI, MPI",
        })
        public void blockedInputStream(
                InterfaceType mainType, InterfaceType secondType) throws Exception {

            // setup
            // ------------------------------------------------------------------
            final CountDownLatch connectorOpen = new CountDownLatch(1);

            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x11, SW_1_OK, SW_2_OK}),
                    new ResponseMessage(mainType, SOLICITED, new byte[]{0x22, SW_1_OK, SW_2_OK})
            ).thenAnswer(new Answer<ResponseMessage>() {
                @Nullable
                @Override
                public ResponseMessage answer(InvocationOnMock invocation) throws Throwable {
                    connectorOpen.await();
                    return null;
                }
            });

            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> queueMap =
                    new EnumMap<>(InterfaceType.class);
            LinkedBlockingQueue<PollerMessage> mainQueue = new LinkedBlockingQueue<>(3);
            LinkedBlockingQueue<PollerMessage> secondQueue = new LinkedBlockingQueue<>(3);
            queueMap.put(mainType, mainQueue);
            queueMap.put(secondType, secondQueue);

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);
            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, queueMap,
                    mockUnsolicitedCallback, mockStatusCallback,
                    100L, TimeUnit.MILLISECONDS);
            Thread thread = new Thread(poller);

            // ------------------------------------------------------------------
            // execute! Threaded!
            // ------------------------------------------------------------------
            thread.start();

            // wait a while so that the poller reads in the valid messages and hits the block
            Thread.sleep(200L);

            /*
                The poller should now be blocked on the input stream. "close" the connector
                and interrupt the thread. The interrupt is not necessary in our case, but
                it's the documented "canonical" way, so do it.
            */
            connectorOpen.countDown();

            // a sleep is required due to our waiting in the Answer in our simulation of a connector
            // closing. If we do it too early we Interrupt the sleep. This isn't a problem with
            // a real bluetooth socket, though a wait might still be prudent.
            Thread.sleep(50L);
            thread.interrupt();

            // Stream should read EOF/broken, we should get status callback, and it should exit.
            thread.join();

            // verify
            // ------------------------------------------------------------------
            verifyZeroInteractions(mockUnsolicitedCallback);

            PollerMessage msgA = mainQueue.take();
            PollerMessage msgB = mainQueue.take();
            PollerMessage secondTerminal = secondQueue.take();
            PollerMessage mainTerminal = mainQueue.take();

            assertThat(msgA.solicitedResponseId, is(equalTo(0)));
            assertThat(msgB.solicitedResponseId, is(equalTo(1)));
            assertThat(mainTerminal.solicitedResponseId, is(equalTo(1)));
            assertThat(secondTerminal.solicitedResponseId, is(equalTo(1)));

            assertThat(msgA.response, is(notNullValue()));
            assertThat(msgB.response, is(notNullValue()));
            assertThat(mainTerminal.response, is(nullValue()));
            assertThat(secondTerminal.response, is(nullValue()));

            assert msgA.response != null;
            assert msgB.response != null;
            assertThat(msgA.response.getBody(), is(equalTo(new byte[]{0x11})));
            assertThat(msgB.response.getBody(), is(equalTo(new byte[]{0x22})));

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 1);
        }

    }

    @RunWith(JUnitParamsRunner.class)
    public static class callbackErrorsTest {

        /** Errors in start callback. */
        @Test
        @Parameters({
                "com.miurasystems.miuralibrary.comms"
                        + ".InputResponsePollerTest$callbackErrorsTest$TestException",
                "com.miurasystems.miuralibrary.comms"
                        + ".InputResponsePollerTest$callbackErrorsTest$TestRuntimeException",
        })
        public void statusCallbackError_start(Class<? extends Throwable> exceptionType)
                throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(null);

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);

            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);
            doThrow(exceptionType).when(mockStatusCallback).handle(PollerStatus.Running, -1);

            // Make mock queues
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                BlockingQueue<PollerMessage> mockQueue = mock(BlockingQueuePollerMessage.class);
                mockQueues.put(nad, mockQueue);
                when(mockQueue
                        .offer(any(PollerMessage.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                        .thenReturn(true);
            }

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------

            // ensure terminal message was posted
            ArgumentCaptor<PollerMessage> mainCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(MPI), times(1))
                    .offer(mainCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

            PollerMessage terminalMessage = mainCaptor.getValue();
            assertThat(terminalMessage.solicitedResponseId, is(equalTo(-1)));
            assertThat(terminalMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockQueues.get(MPI));

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedCallbackError, -1);
        }

        /** Errors in final callback. We shouldn't see much, but the poller should handle it */
        @Test
        @Parameters({
                "com.miurasystems.miuralibrary.comms"
                        + ".InputResponsePollerTest$callbackErrorsTest$TestException",
                "com.miurasystems.miuralibrary.comms"
                        + ".InputResponsePollerTest$callbackErrorsTest$TestRuntimeException",
        })
        public void statusCallbackError_final(Class<? extends Throwable> exceptionType)
                throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(MPI, SOLICITED, new byte[]{(byte) 0x0, SW_1_OK, SW_2_OK}),
                    (ResponseMessage) null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);

            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);
            doThrow(exceptionType).when(mockStatusCallback).handle(
                    PollerStatus.StoppedStreamBroken, 0);

            // Make mock queues
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);
            for (InterfaceType nad : InterfaceType.values()) {
                BlockingQueue<PollerMessage> mockQueue = mock(BlockingQueuePollerMessage.class);
                mockQueues.put(nad, mockQueue);
                when(mockQueue
                        .offer(any(PollerMessage.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                        .thenReturn(true);
            }

            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------

            // ensure terminal message was posted
            ArgumentCaptor<PollerMessage> mainCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockQueues.get(MPI), times(2))
                    .offer(mainCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

            List<PollerMessage> allMainValues = mainCaptor.getAllValues();

            assertThat(allMainValues.get(1).solicitedResponseId, is(equalTo(0)));
            assertThat(allMainValues.get(1).response, is(nullValue()));
            verifyNoMoreInteractions(mockQueues.get(MPI));

            verifyZeroInteractions(mockUnsolicitedCallback);

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedStreamBroken, 0);
        }

        /** Callback in unsolicited handler */
        @Test
        @Parameters({
                "com.miurasystems.miuralibrary.comms"
                        + ".InputResponsePollerTest$callbackErrorsTest$TestException",
                "com.miurasystems.miuralibrary.comms"
                        + ".InputResponsePollerTest$callbackErrorsTest$TestRuntimeException",
        })
        public void run_normalUnsolicitedEof(Class<? extends Throwable> exceptionType)
                throws Exception {

            // setup
            // ------------------------------------------------------------------
            ResponseReader mockReader = mock(ResponseReader.class);
            when(mockReader.nextResponse()).thenReturn(
                    new ResponseMessage(MPI, UNSOLICITED,
                            new byte[]{(byte) 0x70, SW_1_OK, SW_2_OK}),
                    (ResponseMessage) null
            );

            UnsolicitedResponseCallback mockUnsolicitedCallback =
                    mock(UnsolicitedResponseCallback.class);

            doThrow(exceptionType).when(mockUnsolicitedCallback).handle(any(PollerMessage.class));

            PollerStatusCallback mockStatusCallback = mock(PollerStatusCallback.class);

            // Make mock queues. MAIN gets messages + terminal. IGNORED gets terminal.
            EnumMap<InterfaceType, BlockingQueue<PollerMessage>> mockQueues =
                    new EnumMap<>(InterfaceType.class);

            // true=msg | EOF, false=queue timesout
            BlockingQueue<PollerMessage> mockMpiQueue = mock(BlockingQueuePollerMessage.class);
            when(mockMpiQueue
                    .offer(any(PollerMessage.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(MPI, mockMpiQueue);
            BlockingQueue<PollerMessage> mockRpiQueue = mock(BlockingQueuePollerMessage.class);
            when(mockRpiQueue
                    .offer(any(PollerMessage.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(true);
            mockQueues.put(RPI, mockRpiQueue);


            InputResponsePoller poller = new InputResponsePoller(
                    mockReader, mockQueues,
                    mockUnsolicitedCallback, mockStatusCallback,
                    50L, TimeUnit.MILLISECONDS);

            // execute
            // ------------------------------------------------------------------
            poller.run();

            // verify
            // ------------------------------------------------------------------
            ArgumentCaptor<PollerMessage> captor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockMpiQueue, times(1))
                    .offer(captor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

            PollerMessage terminalMessage = captor.getValue();
            assertThat(terminalMessage.solicitedResponseId, is(equalTo(-1)));
            assertThat(terminalMessage.response, is(nullValue()));
            verifyNoMoreInteractions(mockMpiQueue);

            ArgumentCaptor<PollerMessage> msgCaptor = ArgumentCaptor.forClass(PollerMessage.class);
            verify(mockUnsolicitedCallback, times(1)).handle(msgCaptor.capture());
            PollerMessage msg = msgCaptor.getValue();
            assertThat(msg.solicitedResponseId, is(equalTo(-1)));
            assertThat(msg.response, is(notNullValue()));
            //noinspection ConstantConditions
            assertThat(msg.response.getBody()[0], is(equalTo((byte) (0x70))));

            verify(mockStatusCallback, times(1)).handle(PollerStatus.Running, -1);
            verify(mockStatusCallback, times(1)).handle(PollerStatus.StoppedCallbackError, -1);
        }

        public static class TestRuntimeException extends RuntimeException {

        }

        public static class TestException extends Exception {

        }
    }

}
