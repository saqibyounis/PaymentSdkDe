/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNoMoreInteractions;
import static org.powermock.api.mockito.PowerMockito.verifyZeroInteractions;
import static org.powermock.api.mockito.PowerMockito.when;

import com.miurasystems.miuralibrary.comms.ResponseMessage;
import com.miurasystems.miuralibrary.enums.DeviceStatus;
import com.miurasystems.miuralibrary.enums.M012Printer;
import com.miurasystems.miuralibrary.tlv.CardData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;


@SuppressWarnings("unchecked")
@RunWith(PowerMockRunner.class)
@PrepareForTest({ResponseMessage.class})
public class UnsolicitedMessageEventDispatcherTest {

    @Test
    public void emptyMessage() {
        // setup
        ResponseMessage mockMsg = mockResponseMessage(new byte[0]);
        MpiEvents events = mock(MpiEvents.class);

        // execute
        UnsolicitedMessageEventDispatcher.signalEvent(mockMsg, events);

        // verify
        verifyZeroInteractions(events);
    }

    @Test
    public void deviceStatus() {
        // setup
        // nad: 01 pcb: 40 data_len: 14
        //      template: E6 length: 0x10
        //          16 bytes: C3 01 02 C4 06 50 49 4E 20 4F 4B DF A1 02 01 02
        //      sw12: 90 00
        // lrc: 3D
        ResponseMessage mockMsg = mockResponseMessage(new byte[]{
                (byte) 0xE6, 0x10,
                /* C3     */ (byte) 0xC3, 0x01, 0x02,
                /* C4     */ (byte) 0xC4, 0x06, 0x50, 0x49, 0x4E, 0x20, 0x4F, 0x4B,
                /* DFA102 */ (byte) 0xDF, (byte) 0xA1, 0x02, 0x01, 0x02,
                (byte) 0x90, 0x00,
        });
        MpiEvents events = new MpiEvents();
        MpiEventHandler<DeviceStatusChange> mockHandler = mock(MpiEventHandler.class);
        events.DeviceStatusChanged.register(mockHandler);

        // execute
        UnsolicitedMessageEventDispatcher.signalEvent(mockMsg, events);

        // verify
        ArgumentCaptor<DeviceStatusChange> captor =
                ArgumentCaptor.forClass(DeviceStatusChange.class);
        verify(mockHandler).handle(captor.capture());

        DeviceStatusChange actualDeviceStatusChange = captor.getValue();
        assertThat(actualDeviceStatusChange.deviceStatus, is(equalTo(DeviceStatus.PinEntryEvent)));
        assertThat(actualDeviceStatusChange.statusText, is(equalTo("PIN OK")));

        verifyNoMoreInteractions(mockHandler);
    }

    @Test
    public void cardStatus() {
        // setup
        //  0x48, len: 2,
        //      smartcard_status_byte [1:0] = card present | emv_compatible
        //      magswipe_status_byte [3:0] = Track 1 available
        ResponseMessage mockMsg = mockResponseMessage(new byte[]{
                (byte) 0xE1, 0x4,
                0x48, 0x2, 0x3, 0x2,
                (byte) 0x90, 0x00,
        });
        MpiEvents events = new MpiEvents();
        MpiEventHandler<CardData> mockHandler = mock(MpiEventHandler.class);
        events.CardStatusChanged.register(mockHandler);

        // execute
        UnsolicitedMessageEventDispatcher.signalEvent(mockMsg, events);

        // verify
        ArgumentCaptor<CardData> captor = ArgumentCaptor.forClass(CardData.class);
        verify(mockHandler).handle(captor.capture());

        CardData cardData = captor.getValue();
        assertThat(cardData.getCardStatus().isCardPresent(), is(true));
        assertThat(cardData.getCardStatus().isEMVCompatible(), is(true));
        assertThat(cardData.getCardStatus().isTrack1DataAvailable(), is(true));

        verifyNoMoreInteractions(mockHandler);
    }

    @Test
    public void keyPressed() {
        // setup
        // 0xDFA205, len: 1, value = 'cancel'
        ResponseMessage mockMsg = mockResponseMessage(new byte[]{
                (byte) 0xE0, 0x5,
                (byte) 0xDF, (byte) 0xA2, 0x05, 0x01, 0x1B,
                (byte) 0x90, 0x00,
        });
        MpiEvents events = new MpiEvents();
        MpiEventHandler<Integer> mockHandler = mock(MpiEventHandler.class);
        events.KeyPressed.register(mockHandler);

        // execute
        UnsolicitedMessageEventDispatcher.signalEvent(mockMsg, events);

        // verify
        verify(mockHandler).handle(0x1B);
        verifyNoMoreInteractions(mockHandler);
    }

    @Test
    public void printStatus() {
        // setup
        // 0xDFA401, len: 1, value = Powered|Attached
        ResponseMessage mockMsg = mockResponseMessage(new byte[]{
                (byte) 0xE6, 5,
                (byte) 0xDF, (byte) 0xA4, 0x01, 0x1, 0x3,
                (byte) 0x90, 0x00,
        });
        MpiEvents events = new MpiEvents();
        MpiEventHandler<M012Printer> mockHandler = mock(MpiEventHandler.class);
        events.PrinterStatusChanged.register(mockHandler);

        // execute
        UnsolicitedMessageEventDispatcher.signalEvent(mockMsg, events);

        // verify
        ArgumentCaptor<M012Printer> printStatus = ArgumentCaptor.forClass(M012Printer.class);
        verify(mockHandler).handle(printStatus.capture());
        assertThat(printStatus.getValue().getValue(), is(equalTo((byte) 0x3)));

        verifyNoMoreInteractions(mockHandler);
    }

    @Test
    public void barcodeStatus() {
        // setup
        //      0xDFAB01, len: 1, value = Powered|Attached
        //      sw12: 90 00
        ResponseMessage mockMsg = mockResponseMessage(new byte[]{
                (byte) 0xE1, 0xD,
                (byte) 0xDF, (byte) 0xAB, 0x01, 0x9,
                /* b"something" */  0x73, 0x6F, 0x6D, 0x65, 0x74, 0x68, 0x69, 0x6E, 0x67,
                (byte) 0x90, 0x00,
        });
        MpiEvents events = new MpiEvents();
        MpiEventHandler<String> mockHandler = mock(MpiEventHandler.class);
        events.BarcodeScanned.register(mockHandler);

        // execute
        UnsolicitedMessageEventDispatcher.signalEvent(mockMsg, events);

        // verify
        verify(mockHandler).handle("something");
        verifyNoMoreInteractions(mockHandler);
    }

    private static ResponseMessage mockResponseMessage(byte[] body) {
        ResponseMessage mockResponse = mock(ResponseMessage.class);
        when(mockResponse.getBody()).thenReturn(body);
        return mockResponse;
    }

}
