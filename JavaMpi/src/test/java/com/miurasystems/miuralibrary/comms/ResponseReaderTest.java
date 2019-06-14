/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;
import static com.miurasystems.miuralibrary.enums.InterfaceType.RPI;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyZeroInteractions;

import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MpiPacket.class})
public class ResponseReaderTest {

    public MpiPacket makeMockPacket(
            InterfaceType address,
            String unsolicitedStr,
            String chainedStr
    ) {
        boolean unsolicited;
        if ("Unsolicited".equals(unsolicitedStr)) {
            unsolicited = true;
        } else if ("Solicited".equals(unsolicitedStr)) {
            unsolicited = false;
        } else {
            throw new AssertionError();
        }
        boolean chained;
        if ("Unchained".equals(chainedStr)) {
            chained = false;
        } else if ("Chained".equals(chainedStr)) {
            chained = true;
        } else {
            throw new AssertionError();
        }

        MpiPacket mockPacket = mock(MpiPacket.class);
        when(mockPacket.getNodeAddress()).thenReturn(address);
        when(mockPacket.isChained()).thenReturn(chained);
        when(mockPacket.isUnsolicited()).thenReturn(unsolicited);

        return mockPacket;
    }

    @Test
    public void firstPacketIsNull() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream stream = mock(InputStream.class);
        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(stream)).thenReturn(null);

        ResponseReader reader = new ResponseReader(stream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(stream);
        assertThat(response, is(nullValue()));
    }

    @Test
    public void singlePacket_solicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacket = makeMockPacket(MPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacket);
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacket)))
                .thenReturn(new byte[]{(byte) 0xFF, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert response != null;

        assertThat(response.getBody(), is(equalTo(new byte[]{(byte) 0xff})));

        assertThat(response.getNodeAddress(), is(equalTo(MPI)));
        assertThat(response.isSuccess(), is(equalTo(true)));
        assertThat(response.isUnsolicited(), is(equalTo(false)));
        assertThat(response.getStatusCode(), is(equalTo(0x9000)));
    }

    @Test
    public void singlePacket_unsolicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacket = makeMockPacket(MPI, "Unsolicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacket);
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacket)))
                .thenReturn(new byte[]{(byte) 0xA5, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert response != null;

        assertThat(response.getBody(), is(equalTo(new byte[]{(byte) 0xa5})));

        assertThat(response.getNodeAddress(), is(equalTo(MPI)));
        assertThat(response.isSuccess(), is(equalTo(true)));
        assertThat(response.isUnsolicited(), is(equalTo(true)));
        assertThat(response.getStatusCode(), is(equalTo(0x9000)));
    }

    @Test
    public void multiPacket_chainedSolicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketC = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketD = makeMockPacket(MPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(mockPacketA, mockPacketB, mockPacketC, mockPacketD);
        List<MpiPacket> mockList = Arrays.asList(mockPacketA, mockPacketB, mockPacketC,
                mockPacketD);
        when(MpiPacket.reconstructApdu(mockList))
                .thenReturn(
                        new byte[]{0xA, 0xA, 0xB, 0xB, 0xC, 0xC, 0xD, 0xD, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert response != null;

        assertThat(response.getBody(), is(equalTo(new byte[]{
                0xa, 0xa, 0xb, 0xb, 0xc, 0xc, 0xd, 0xd,
        })));
        assertThat(response.getNodeAddress(), is(equalTo(MPI)));
        assertThat(response.isSuccess(), is(equalTo(true)));
        assertThat(response.isUnsolicited(), is(equalTo(false)));
        assertThat(response.getStatusCode(), is(equalTo(0x9000)));
    }

    @Test
    public void multiPacket_chainedUnsolicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketC = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketD = makeMockPacket(MPI, "Unsolicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(mockPacketA, mockPacketB, mockPacketC, mockPacketD);
        List<MpiPacket> mockList = Arrays.asList(mockPacketA, mockPacketB, mockPacketC,
                mockPacketD);
        when(MpiPacket.reconstructApdu(mockList))
                .thenReturn(
                        new byte[]{0xA, 0xA, 0xB, 0xB, 0xC, 0xC, 0xD, 0xD, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert response != null;

        assertThat(response.getBody(), is(equalTo(new byte[]{
                0xa, 0xa, 0xb, 0xb, 0xc, 0xc, 0xd, 0xd,
        })));
        assertThat(response.getNodeAddress(), is(equalTo(MPI)));
        assertThat(response.isSuccess(), is(equalTo(true)));
        assertThat(response.isUnsolicited(), is(equalTo(true)));
        assertThat(response.getStatusCode(), is(equalTo(0x9000)));
    }

    @Test
    public void multiPacket_chainedInconsistentSolicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketC = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketD = makeMockPacket(MPI, "Unsolicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(mockPacketA, mockPacketB, mockPacketC, mockPacketD);

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assertThat(response, is(nullValue()));
    }

    @Test
    public void multiPacket_chainedInconsistentSolicitedEndChain() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketC = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketD = makeMockPacket(MPI, "Unsolicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(mockPacketA, mockPacketB, mockPacketC, mockPacketD);

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assertThat(response, is(nullValue()));
    }

    @Test
    public void multiPacket_chainedSolicitedRpiOverlappingSolicitedMpi() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiC = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiD = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Solicited", "Unchained");


        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketMpiA, // PED -|
                        mockPacketMpiB, //     >|
                        mockPacketRpiA, //      | RPI -¦
                        mockPacketMpiC, //     >|      ¦
                        mockPacketMpiD, // -----|      ¦
                        mockPacketRpiB // -------------¦
                );
        List<MpiPacket> mpiCompleted = Arrays.asList(
                mockPacketMpiA, mockPacketMpiB, mockPacketMpiC, mockPacketMpiD);
        when(MpiPacket.reconstructApdu(mpiCompleted))
                .thenReturn(
                        new byte[]{0xA, 0xA, 0xB, 0xB, 0xC, 0xC, 0xD, 0xD, (byte) 0x90, 0x00});

        List<MpiPacket> rpiCompleted = Arrays.asList(mockPacketRpiA, mockPacketRpiB);
        when(MpiPacket.reconstructApdu(rpiCompleted))
                .thenReturn(
                        new byte[]{0x7A, 0x7A, 0x7B, 0x7B, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi = reader.nextResponse();
        ResponseMessage responseRpi = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseMpi != null;

        assertThat(responseMpi.getBody(), is(equalTo(new byte[]{
                0xa, 0xa, 0xb, 0xb, 0xc, 0xc, 0xd, 0xd,
        })));
        assertThat(responseMpi.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi.isUnsolicited(), is(equalTo(false)));

        assert responseRpi != null;
        assertThat(responseRpi.getBody(), is(equalTo(new byte[]{0x7A, 0x7A, 0x7B, 0x7B})));
        assertThat(responseRpi.getNodeAddress(), is(equalTo(RPI)));
        assertThat(responseRpi.isSuccess(), is(equalTo(true)));
        assertThat(responseRpi.isUnsolicited(), is(equalTo(false)));
    }

    @Test
    public void multiPacket_chainedMpiThenRpi() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiC = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiD = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Solicited", "Unchained");


        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketMpiA, // PED -|
                        mockPacketMpiB, //      |
                        mockPacketMpiC, //      |
                        mockPacketMpiD, // -----|

                        mockPacketRpiA, // RPI -¦
                        mockPacketRpiB // ------¦
                );
        List<MpiPacket> mpiCompleted = Arrays.asList(
                mockPacketMpiA, mockPacketMpiB, mockPacketMpiC, mockPacketMpiD);
        when(MpiPacket.reconstructApdu(mpiCompleted))
                .thenReturn(
                        new byte[]{0xA, 0xA, 0xB, 0xB, 0xC, 0xC, 0xD, 0xD, (byte) 0x90, 0x00});

        List<MpiPacket> rpiCompleted = Arrays.asList(mockPacketRpiA, mockPacketRpiB);
        when(MpiPacket.reconstructApdu(rpiCompleted))
                .thenReturn(
                        new byte[]{0x7A, 0x7A, 0x7B, 0x7B, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi = reader.nextResponse();
        ResponseMessage responseRpi = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseMpi != null;

        assertThat(responseMpi.getBody(), is(equalTo(new byte[]{
                0xa, 0xa, 0xb, 0xb, 0xc, 0xc, 0xd, 0xd,
        })));
        assertThat(responseMpi.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi.isUnsolicited(), is(equalTo(false)));

        assert responseRpi != null;
        assertThat(responseRpi.getBody(), is(equalTo(new byte[]{0x7A, 0x7A, 0x7B, 0x7B})));
        assertThat(responseRpi.getNodeAddress(), is(equalTo(RPI)));
        assertThat(responseRpi.isSuccess(), is(equalTo(true)));
        assertThat(responseRpi.isUnsolicited(), is(equalTo(false)));
    }

    @Test
    public void multiPacket_chainedOverlappingSolicitedRpiAndUnsolicitedMpi() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiC = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiD = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketRpiA, // RPI -----¦
                        mockPacketMpiA, // PED --|  ¦
                        mockPacketMpiB, //       |  ¦
                        mockPacketMpiC, //       |  ¦
                        mockPacketMpiD, // ------|  ¦
                        mockPacketRpiB // ----------¦
                );
        List<MpiPacket> mpiCompleted = Arrays.asList(
                mockPacketMpiA, mockPacketMpiB, mockPacketMpiC, mockPacketMpiD);
        when(MpiPacket.reconstructApdu(mpiCompleted))
                .thenReturn(
                        new byte[]{0xA, 0xA, 0xB, 0xB, 0xC, 0xC, 0xD, 0xD, (byte) 0x90, 0x00});

        List<MpiPacket> rpiCompleted = Arrays.asList(mockPacketRpiA, mockPacketRpiB);
        when(MpiPacket.reconstructApdu(rpiCompleted))
                .thenReturn(
                        new byte[]{0x7A, 0x7A, 0x7B, 0x7B, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi = reader.nextResponse();
        ResponseMessage responseRpi = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseMpi != null;

        // MPI first, as it completed first.
        assertThat(responseMpi.getBody(), is(equalTo(new byte[]{
                0xa, 0xa, 0xb, 0xb, 0xc, 0xc, 0xd, 0xd,
        })));
        assertThat(responseMpi.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi.isUnsolicited(), is(equalTo(false)));

        assert responseRpi != null;
        assertThat(responseRpi.getBody(), is(equalTo(new byte[]{0x7A, 0x7A, 0x7B, 0x7B})));
        assertThat(responseRpi.getNodeAddress(), is(equalTo(RPI)));
        assertThat(responseRpi.isSuccess(), is(equalTo(true)));
        assertThat(responseRpi.isUnsolicited(), is(equalTo(false)));
    }

    @Test
    public void multiPacket_chainedOverlappingSolicitedRpiUnsolicitedMpiEOF() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiC = makeMockPacket(MPI, "Solicited", "Chained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketRpiA, // RPI -----¦
                        mockPacketMpiA, // PED --|  ¦
                        mockPacketMpiB, //       |  ¦
                        mockPacketMpiC, //       |  ¦
                        mockPacketRpiB, // ------|--¦
                        null //                 EOF
                );
        List<MpiPacket> rpiCompleted = Arrays.asList(mockPacketRpiA, mockPacketRpiB);
        when(MpiPacket.reconstructApdu(rpiCompleted))
                .thenReturn(
                        new byte[]{0x7A, 0x7A, 0x7B, 0x7B, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseRpi = reader.nextResponse();
        ResponseMessage responseMpi = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseRpi != null;
        assertThat(responseRpi.getBody(), is(equalTo(new byte[]{0x7A, 0x7A, 0x7B, 0x7B})));
        assertThat(responseRpi.getNodeAddress(), is(equalTo(RPI)));
        assertThat(responseRpi.isSuccess(), is(equalTo(true)));
        assertThat(responseRpi.isUnsolicited(), is(equalTo(false)));

        assertThat(responseMpi, is(nullValue()));
    }


    @Test
    public void multiPacket_chainedOverlappingRpiMultipleMpi() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpi1A = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpi1B = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketMpi2A = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpi2B = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketMpi3 = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Solicited", "Unchained");


        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketRpiA, // RPI ------¦
                        //                           ¦
                        mockPacketMpi1A, // PED -|   ¦
                        mockPacketMpi1B, // ---- |   ¦
                        mockPacketMpi2A, // PED -|   ¦
                        mockPacketMpi2B, // -----|   ¦
                        mockPacketMpi3, // = PED     ¦
                        //                           ¦
                        mockPacketRpiB // -----------¦
                );
        List<MpiPacket> mpiCompleted1 = Arrays.asList(mockPacketMpi1A, mockPacketMpi1B);
        List<MpiPacket> mpiCompleted2 = Arrays.asList(mockPacketMpi2A, mockPacketMpi2B);
        List<MpiPacket> mpiCompleted3 = Collections.singletonList(mockPacketMpi3);
        when(MpiPacket.reconstructApdu(mpiCompleted1))
                .thenReturn(new byte[]{0xA, 0xA, 0xB, 0xB, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(mpiCompleted2))
                .thenReturn(new byte[]{0xC, 0xC, 0xD, 0xD, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(mpiCompleted3))
                .thenReturn(new byte[]{0xE, 0xE, (byte) 0x90, 0x00});

        List<MpiPacket> rpiCompleted = Arrays.asList(mockPacketRpiA, mockPacketRpiB);
        when(MpiPacket.reconstructApdu(rpiCompleted))
                .thenReturn(
                        new byte[]{0x7A, 0x7A, 0x7B, 0x7B, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi1 = reader.nextResponse();
        ResponseMessage responseMpi2 = reader.nextResponse();
        ResponseMessage responseMpi3 = reader.nextResponse();
        ResponseMessage responseRpi = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);

        assert responseMpi1 != null;
        assertThat(responseMpi1.getBody(), is(equalTo(new byte[]{0xa, 0xa, 0xb, 0xb,})));
        assertThat(responseMpi1.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi1.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi1.isUnsolicited(), is(equalTo(false)));

        assert responseMpi2 != null;
        assertThat(responseMpi2.getBody(), is(equalTo(new byte[]{0xc, 0xc, 0xd, 0xd,})));
        assertThat(responseMpi2.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi2.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi2.isUnsolicited(), is(equalTo(false)));

        assert responseMpi3 != null;
        assertThat(responseMpi3.getBody(), is(equalTo(new byte[]{0xe, 0xe})));
        assertThat(responseMpi3.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi3.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi3.isUnsolicited(), is(equalTo(false)));

        assert responseRpi != null;
        assertThat(responseRpi.getBody(), is(equalTo(new byte[]{0x7A, 0x7A, 0x7B, 0x7B})));
        assertThat(responseRpi.getNodeAddress(), is(equalTo(RPI)));
        assertThat(responseRpi.isSuccess(), is(equalTo(true)));
        assertThat(responseRpi.isUnsolicited(), is(equalTo(false)));
    }

    @Test
    public void multiPacket_chainedOverlappingRpiMultipleMpiAbsurd() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpi1A = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpi1B = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketMpi2A = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpi2B = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketMpi3 = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketMpi4A = makeMockPacket(MPI, "Unsolicited", "Chained");
        MpiPacket mockPacketMpi4B = makeMockPacket(MPI, "Unsolicited", "Unchained");
        MpiPacket mockPacketMpi5A = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpi5B = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Solicited", "Unchained");


        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketRpiA, // RPI ------¦
                        //                           ¦
                        mockPacketMpi1A, // PED -|   ¦
                        mockPacketMpi1B, // ---- |   ¦
                        mockPacketMpi2A, // PED -|   ¦
                        mockPacketMpi2B, // -----|   ¦
                        mockPacketMpi3, //  PED      ¦
                        mockPacketMpi4A, // PED -|   ¦
                        mockPacketMpi4B, // -----|   ¦
                        mockPacketMpi5A, // PED -|   ¦
                        mockPacketMpi5B, // -----|   ¦
                        //                           ¦
                        mockPacketRpiB // -----------¦
                );
        List<MpiPacket> mpiCompleted1 = Arrays.asList(mockPacketMpi1A, mockPacketMpi1B);
        List<MpiPacket> mpiCompleted2 = Arrays.asList(mockPacketMpi2A, mockPacketMpi2B);
        List<MpiPacket> mpiCompleted3 = Collections.singletonList(mockPacketMpi3);
        List<MpiPacket> mpiCompleted4 = Arrays.asList(mockPacketMpi4A, mockPacketMpi4B);
        List<MpiPacket> mpiCompleted5 = Arrays.asList(mockPacketMpi5A, mockPacketMpi5B);

        when(MpiPacket.reconstructApdu(mpiCompleted1))
                .thenReturn(new byte[]{0xA, 0xA, 0xB, 0xB, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(mpiCompleted2))
                .thenReturn(new byte[]{0xC, 0xC, 0xD, 0xD, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(mpiCompleted3))
                .thenReturn(new byte[]{0xE, 0xE, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(mpiCompleted4))
                .thenReturn(new byte[]{0xF, 0xF, 0x10, 0x10, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(mpiCompleted5))
                .thenReturn(new byte[]{0x20, 0x20, 0x30, 0x30, (byte) 0x90, 0x00});

        List<MpiPacket> rpiCompleted = Arrays.asList(mockPacketRpiA, mockPacketRpiB);
        when(MpiPacket.reconstructApdu(rpiCompleted))
                .thenReturn(
                        new byte[]{0x7A, 0x7A, 0x7B, 0x7B, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi1 = reader.nextResponse();
        ResponseMessage responseMpi2 = reader.nextResponse();
        ResponseMessage responseMpi3 = reader.nextResponse();
        ResponseMessage responseMpi4 = reader.nextResponse();
        ResponseMessage responseMpi5 = reader.nextResponse();
        ResponseMessage responseRpi = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);

        assert responseMpi1 != null;
        assertThat(responseMpi1.getBody(), is(equalTo(new byte[]{0xa, 0xa, 0xb, 0xb,})));
        assertThat(responseMpi1.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi1.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi1.isUnsolicited(), is(equalTo(false)));

        assert responseMpi2 != null;
        assertThat(responseMpi2.getBody(), is(equalTo(new byte[]{0xc, 0xc, 0xd, 0xd,})));
        assertThat(responseMpi2.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi2.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi2.isUnsolicited(), is(equalTo(false)));

        assert responseMpi3 != null;
        assertThat(responseMpi3.getBody(), is(equalTo(new byte[]{0xe, 0xe,})));
        assertThat(responseMpi3.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi3.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi3.isUnsolicited(), is(equalTo(false)));

        assert responseMpi4 != null;
        assertThat(responseMpi4.getBody(), is(equalTo(new byte[]{0xf, 0xf, 0x10, 0x10,})));
        assertThat(responseMpi4.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi4.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi4.isUnsolicited(), is(equalTo(true))); // +

        assert responseMpi5 != null;
        assertThat(responseMpi5.getBody(), is(equalTo(new byte[]{0x20, 0x20, 0x30, 0x30,})));
        assertThat(responseMpi5.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi5.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi5.isUnsolicited(), is(equalTo(false)));

        assert responseRpi != null;
        assertThat(responseRpi.getBody(), is(equalTo(new byte[]{0x7A, 0x7A, 0x7B, 0x7B})));
        assertThat(responseRpi.getNodeAddress(), is(equalTo(RPI)));
        assertThat(responseRpi.isSuccess(), is(equalTo(true)));
        assertThat(responseRpi.isUnsolicited(), is(equalTo(false)));
    }


    @Test
    public void multiPacket_chainedOverlappingInconsistentBothRpiMpi() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Unsolicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Unsolicited", "Unchained");


        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketMpiA, // PED -|
                        mockPacketRpiA, //      | RPI -|
                        mockPacketRpiB, //      | -----|
                        mockPacketMpiB // ------|
                );
        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi = reader.nextResponse();
        ResponseMessage responseRpi = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);

        assertThat(responseMpi, is(nullValue()));
        assertThat(responseRpi, is(nullValue()));
    }

    @Test
    public void multiPacket_chainedOverlappingInconsistentRpi() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Unsolicited", "Unchained");


        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketMpiA, // PED -|
                        mockPacketRpiA, //      | RPI -|
                        mockPacketRpiB, //      | -----|
                        mockPacketMpiB // ------|
                );
        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi = reader.nextResponse();
        ResponseMessage responseNothing = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assertThat(responseMpi, is(nullValue()));
        assertThat(responseNothing, is(nullValue()));
    }

    @Test
    public void multiPacket_chainedOverlappingInconsistentRpiAfterCompleteMpi() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Unsolicited", "Unchained");


        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketMpiA, // PED -|
                        mockPacketRpiA, //      | RPI -|
                        mockPacketMpiB, //------|      |
                        mockPacketRpiB // -------------|
                );

        List<MpiPacket> mpiCompleted = Arrays.asList(mockPacketMpiA, mockPacketMpiB);

        when(MpiPacket.reconstructApdu(mpiCompleted))
                .thenReturn(new byte[]{0xA, 0xA, 0xB, 0xB, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpi = reader.nextResponse();
        ResponseMessage responseNothing = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);

        assert responseMpi != null;
        assertThat(responseMpi.getBody(), is(equalTo(new byte[]{0xa, 0xa, 0xb, 0xb,})));
        assertThat(responseMpi.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpi.isSuccess(), is(equalTo(true)));
        assertThat(responseMpi.isUnsolicited(), is(equalTo(false)));


        assertThat(responseNothing, is(nullValue()));
    }

    @Test
    public void multiPacket_chainedNullPacketSingleNad() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Solicited", "Chained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacketA, mockPacketB, null);

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        assertThat(response, is(nullValue()));
    }

    @Test
    public void multiPacket_chainedNullPacketMultiNad() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Solicited", "Chained");
        MpiPacket mockPacketB = makeMockPacket(RPI, "Solicited", "Chained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacketA, mockPacketB, null);

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage response = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        assertThat(response, is(nullValue()));
    }

    @Test
    public void multiPacket_chainedInconsistentLeadsToBroken() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketMpiA = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketMpiB = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketMpiC = makeMockPacket(MPI, "Solicited", "Unchained");

        MpiPacket mockPacketRpiA = makeMockPacket(RPI, "Solicited", "Chained");
        MpiPacket mockPacketRpiB = makeMockPacket(RPI, "Unsolicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream))
                .thenReturn(
                        mockPacketMpiA, // PED
                        mockPacketMpiB, // PED
                        mockPacketRpiA, //       RPI -|
                        mockPacketRpiB, //  ----------|
                        mockPacketMpiC // PED
                );

        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketMpiA)))
                .thenReturn(new byte[]{0xA, 0xA, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketMpiB)))
                .thenReturn(new byte[]{0xB, 0xB, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseMpiA = reader.nextResponse();
        ResponseMessage responseMpiB = reader.nextResponse();
        ResponseMessage responseRpiInconsistent = reader.nextResponse();
        ResponseMessage responseMpiBroken = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);

        assert responseMpiA != null;
        assertThat(responseMpiA.getBody(), is(equalTo(new byte[]{0xa, 0xa})));
        assertThat(responseMpiA.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpiA.isSuccess(), is(equalTo(true)));
        assertThat(responseMpiA.isUnsolicited(), is(equalTo(false)));

        assert responseMpiB != null;
        assertThat(responseMpiB.getBody(), is(equalTo(new byte[]{0xb, 0xb})));
        assertThat(responseMpiB.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseMpiB.isSuccess(), is(equalTo(true)));
        assertThat(responseMpiB.isUnsolicited(), is(equalTo(false)));

        assertThat(responseRpiInconsistent, is(nullValue()));
        assertThat(responseMpiBroken, is(nullValue()));
    }

    @Test
    public void multiPacket_notChainedEof() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacketA, null, null);
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketA)))
                .thenReturn(new byte[]{(byte) 0xaa, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseA = reader.nextResponse();
        ResponseMessage responseEof = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseA != null;
        assertThat(responseA.getBody(), is(equalTo(new byte[]{(byte) 0xaa})));
        assertThat(responseA.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseA.isSuccess(), is(equalTo(true)));
        assertThat(responseA.isUnsolicited(), is(equalTo(false)));

        assertThat(responseEof, is(nullValue()));
    }

    @Test
    public void multiPacket_notChainedEofBroken() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacketA, null, null);
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketA)))
                .thenReturn(new byte[]{(byte) 0xaa, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseA = reader.nextResponse();
        ResponseMessage responseEof = reader.nextResponse();
        ResponseMessage responseBroken = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseA != null;
        assertThat(responseA.getBody(), is(equalTo(new byte[]{(byte) 0xaa})));
        assertThat(responseA.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseA.isSuccess(), is(equalTo(true)));
        assertThat(responseA.isUnsolicited(), is(equalTo(false)));

        assertThat(responseEof, is(nullValue()));
        assertThat(responseBroken, is(nullValue()));
    }

    @Test
    public void multiPacket_notChainedAllSolicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketC = makeMockPacket(MPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacketA, mockPacketB,
                mockPacketC);

        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketA)))
                .thenReturn(new byte[]{0xA, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketB)))
                .thenReturn(new byte[]{0xB, (byte) 0x00, 0x00});
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketC)))
                .thenReturn(new byte[]{0xC, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseA = reader.nextResponse();
        ResponseMessage responseB = reader.nextResponse();
        ResponseMessage responseC = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseA != null;
        assertThat(responseA.getBody(), is(equalTo(new byte[]{(byte) 0x0A})));
        assertThat(responseA.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseA.isSuccess(), is(equalTo(true)));
        assertThat(responseA.isUnsolicited(), is(equalTo(false)));

        assert responseB != null;
        assertThat(responseB.getBody(), is(equalTo(new byte[]{(byte) 0x0B})));
        assertThat(responseB.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseB.isSuccess(), is(equalTo(false)));
        assertThat(responseB.isUnsolicited(), is(equalTo(false)));

        assert responseC != null;
        assertThat(responseC.getBody(), is(equalTo(new byte[]{(byte) 0x0C})));
        assertThat(responseC.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseC.isSuccess(), is(equalTo(true)));
        assertThat(responseC.isUnsolicited(), is(equalTo(false)));
    }

    @Test
    public void multiPacket_notChainedAllUnsolicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Unsolicited", "Unchained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Unsolicited", "Unchained");
        MpiPacket mockPacketC = makeMockPacket(MPI, "Unsolicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacketA, mockPacketB,
                mockPacketC);

        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketA)))
                .thenReturn(new byte[]{0xA, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketB)))
                .thenReturn(new byte[]{0xB, (byte) 0x00, 0x00});
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketC)))
                .thenReturn(new byte[]{0xC, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseA = reader.nextResponse();
        ResponseMessage responseB = reader.nextResponse();
        ResponseMessage responseC = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseA != null;
        assertThat(responseA.getBody(), is(equalTo(new byte[]{(byte) 0x0A})));
        assertThat(responseA.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseA.isSuccess(), is(equalTo(true)));
        assertThat(responseA.isUnsolicited(), is(equalTo(true)));

        assert responseB != null;
        assertThat(responseB.getBody(), is(equalTo(new byte[]{(byte) 0x0B})));
        assertThat(responseB.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseB.isSuccess(), is(equalTo(false)));
        assertThat(responseB.isUnsolicited(), is(equalTo(true)));

        assert responseC != null;
        assertThat(responseC.getBody(), is(equalTo(new byte[]{(byte) 0x0C})));
        assertThat(responseC.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseC.isSuccess(), is(equalTo(true)));
        assertThat(responseC.isUnsolicited(), is(equalTo(true)));
    }

    @Test
    public void multiPacket_notChainedMixSolicited() {
        // ----------------------------------------------------------------
        // setup
        // ----------------------------------------------------------------
        InputStream mockStream = mock(InputStream.class);

        MpiPacket mockPacketA = makeMockPacket(MPI, "Solicited", "Unchained");
        MpiPacket mockPacketB = makeMockPacket(MPI, "Unsolicited", "Unchained");
        MpiPacket mockPacketC = makeMockPacket(MPI, "Solicited", "Unchained");

        mockStatic(MpiPacket.class);
        when(MpiPacket.readFromStream(mockStream)).thenReturn(mockPacketA, mockPacketB,
                mockPacketC);

        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketA)))
                .thenReturn(new byte[]{0xA, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketB)))
                .thenReturn(new byte[]{0xB, (byte) 0x90, 0x00});
        when(MpiPacket.reconstructApdu(Collections.singletonList(mockPacketC)))
                .thenReturn(new byte[]{0xC, (byte) 0x90, 0x00});

        ResponseReader reader = new ResponseReader(mockStream);

        // ----------------------------------------------------------------
        // execute
        // ----------------------------------------------------------------
        ResponseMessage responseA = reader.nextResponse();
        ResponseMessage responseB = reader.nextResponse();
        ResponseMessage responseC = reader.nextResponse();

        // ----------------------------------------------------------------
        // verify
        // ----------------------------------------------------------------
        verifyZeroInteractions(mockStream);
        assert responseA != null;
        assertThat(responseA.getBody(), is(equalTo(new byte[]{(byte) 0x0A})));
        assertThat(responseA.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseA.isSuccess(), is(equalTo(true)));
        assertThat(responseA.isUnsolicited(), is(equalTo(false)));

        assert responseB != null;
        assertThat(responseB.getBody(), is(equalTo(new byte[]{(byte) 0x0B})));
        assertThat(responseB.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseB.isSuccess(), is(equalTo(true)));
        assertThat(responseB.isUnsolicited(), is(equalTo(true)));

        assert responseC != null;
        assertThat(responseC.getBody(), is(equalTo(new byte[]{(byte) 0x0C})));
        assertThat(responseC.getNodeAddress(), is(equalTo(MPI)));
        assertThat(responseC.isSuccess(), is(equalTo(true)));
        assertThat(responseC.isUnsolicited(), is(equalTo(false)));
    }
}
