/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import com.miurasystems.miuralibrary.comms.CommandApdu;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;
import com.miurasystems.miuralibrary.comms.ResponseMessage;
import com.miurasystems.miuralibrary.comms.StubConnectorSession;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.events.MpiEvents;

import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.nio.charset.Charset;
import java.util.Arrays;

@RunWith(Enclosed.class)
public class MpiClientTest {
    private static final Charset US_ASCII = Charset.forName("US-ASCII");
    private static final int GBP = 826;

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({ResponseMessage.class})
    public static class MpiClientTestSessionManagement {
        @Test
        public void sendCommandWithoutOpenSession() throws Exception {
            // this is more to help test the interface, rather than
            // test displayText

            // setup
            MpiEvents mpiEvents = new MpiEvents();
            MpiProtocolSession mockSession = mock(MpiProtocolSession.class);
            StubConnectorSession connector = new StubConnectorSession(mockSession);
            MpiClient client = new MpiClient(connector, mpiEvents);

            // execute
            // client.openSession(); <---
            boolean b = client.displayText(InterfaceType.MPI, "Hello world!", false, false, false);
            assertThat(b, is(false));
            //this shouldn't crash it in anyway
            client.closeSession();

            // verify
        }
    }

    @RunWith(PowerMockRunner.class)
    @PrepareForTest({ResponseMessage.class})
    public static class MpiClientTestMocked {

        @Test
        public void displayText() throws Exception {
            // this is more to help test the interface, rather than
            // test displayText

            // setup
            MpiEvents mpiEvents = new MpiEvents();

            ResponseMessage mockResponse = mock(ResponseMessage.class);
            when(mockResponse.isSuccess()).thenReturn(true);

            MpiProtocolSession mockSession = mock(MpiProtocolSession.class);
            when(mockSession.receiveResponse(InterfaceType.MPI)).thenReturn(mockResponse);

            StubConnectorSession connector = new StubConnectorSession(mockSession);

            // execute
            MpiClient client = new MpiClient(connector, mpiEvents);
            client.openSession();
            boolean ok =
                    client.displayText(InterfaceType.MPI, "Hello world!", false, false, false);
            client.closeSession();

            // verify
            assertThat(ok, is(true));

            ArgumentCaptor<CommandApdu> captor = ArgumentCaptor.forClass(CommandApdu.class);
            verify(mockSession).sendCommandAPDU(eq(InterfaceType.MPI), captor.capture());

            byte[] helloWorldBytes = "Hello world!".getBytes(US_ASCII);
            CommandApdu apdu = captor.getValue();
            byte[] bytes = apdu.getBytes();

            // ignore the CLA/INS/P1/P2/Lc header...
            byte[] slice = Arrays.copyOfRange(bytes, 5, bytes.length);
            assertThat(slice, is(equalTo(helloWorldBytes)));
        }
    }

}
