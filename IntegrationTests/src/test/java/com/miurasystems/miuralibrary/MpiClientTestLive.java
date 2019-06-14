/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;


import static com.miurasystems.miuralibrary.MpiClient.OnlinePinResultType.PinEnteredOk;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import android.support.annotation.NonNull;

import com.miurasystems.examples.connectors.ClientSocketConnector;
import com.miurasystems.examples.connectors.JavaBluetoothConnector;
import com.miurasystems.miuralibrary.MpiClient.GetNumericDataError;
import com.miurasystems.miuralibrary.MpiClient.OnlinePinResult;
import com.miurasystems.miuralibrary.api.objects.BatteryData;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.api.utils.GetDeviceFile;
import com.miurasystems.miuralibrary.api.utils.StreamBinaryFile;
import com.miurasystems.miuralibrary.api.utils.StreamBinaryFile.ProgressCallback;
import com.miurasystems.miuralibrary.comms.ResponseMessage;
import com.miurasystems.miuralibrary.enums.BacklightSettings;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.OnlinePINError;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.enums.SelectFileMode;
import com.miurasystems.miuralibrary.enums.StatusSettings;
import com.miurasystems.miuralibrary.enums.SystemLogMode;
import com.miurasystems.miuralibrary.enums.TransactionResponse;
import com.miurasystems.miuralibrary.enums.TransactionType;
import com.miurasystems.miuralibrary.events.CommsStatusChange;
import com.miurasystems.miuralibrary.events.ConnectionInfo;
import com.miurasystems.miuralibrary.events.DeviceStatusChange;
import com.miurasystems.miuralibrary.events.MpiEventHandler;
import com.miurasystems.miuralibrary.events.MpiEvents;
import com.miurasystems.miuralibrary.tlv.BinaryUtil;
import com.miurasystems.miuralibrary.tlv.CardData;
import com.miurasystems.miuralibrary.tlv.CardStatus;
import com.miurasystems.miuralibrary.tlv.Description;
import com.miurasystems.miuralibrary.tlv.TLVObject;
import com.miurasystems.miuralibrary.tlv.TLVParser;
import com.miurasystems.miuralibrary.tlv.Track2Data;

import org.hamcrest.core.StringEndsWith;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.bluetooth.RemoteDevice;

@Ignore("Requires specific live PEDs")
@SuppressWarnings("UseOfSystemOutOrSystemErr")
@RunWith(PowerMockRunner.class)
@PrepareForTest({ResponseMessage.class})
public class MpiClientTestLive {
    private static final Charset US_ASCII = Charset.forName("US-ASCII");
    private static final int GBP = 826;

    private static final RemoteDevice BT_PED_DEVICE_WITH_CARD;
    private static final RemoteDevice BT_PED_DEVICE_WITHOUT_CARD;
    private static final RemoteDevice BT_POS_DEVICE;

    static {
        try {
            RemoteDevice miura515 = JavaBluetoothConnector.findDeviceForName("Miura 515");
            if (miura515 == null) {
                throw new AssertionError("Expected 'Miura 515' to be paired");
            }
            BT_PED_DEVICE_WITH_CARD = miura515;

            RemoteDevice miura606 = JavaBluetoothConnector.findDeviceForName("Miura 606");
            if (miura606 == null) {
                throw new AssertionError("Expected 'Miura 606' to be paired");
            }
            BT_PED_DEVICE_WITHOUT_CARD = miura606;

            RemoteDevice pos = JavaBluetoothConnector.findDeviceForName("Miura POSzle 203");
            if (pos == null) {
                throw new AssertionError("Expected 'Miura POSzle 203' to be paired");
            }
            BT_POS_DEVICE = pos;
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }


    /**
     * The device running this test needs to be:
     * 1. paired with miura device 515.
     * 2. on the miura-wrt-belk network,
     * and a PED needs to be on the same network listening on 192.168.0.96 ...
     */
    @Test
    public void multiClient() throws Exception {
        // just to ensure the test works ok and not fail due to open connectors
        Thread.sleep(150L);

        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);

        InetAddress addr = InetAddress.getByName("192.168.0.96");
        ClientSocketConnector wifiConnector = new ClientSocketConnector(addr, 6543);

        CountDownLatch counter = new CountDownLatch(2);
        System.out.println("COUNTER:" + counter.getCount());
        MpiEvents events = new MpiEvents();

        MpiClient btClient = new MpiClient(btConnector, events);
        MpiClient wifiClient = new MpiClient(wifiConnector, events);

        Runnable btRunnable = new MultiClientTestRunnable(
                "Miura 515: Bluetooth", counter, btClient);
        Runnable wifiRunnable = new MultiClientTestRunnable(
                "Miura 920: WiFi", counter, wifiClient);
        final Thread btThread = new Thread(btRunnable);
        final Thread wifiThread = new Thread(wifiRunnable);

        // Make events and register them
        events.Connected.register(new MpiEventHandler<ConnectionInfo>() {
            @Override
            public void handle(@NonNull ConnectionInfo arg) {
                System.out.println("Connected event");
            }
        });
        events.Disconnected.register(new MpiEventHandler<ConnectionInfo>() {
            @Override
            public void handle(@NonNull ConnectionInfo arg) {
                System.out.println("Disconnected event");
            }
        });
        events.DeviceStatusChanged.register(new MpiEventHandler<DeviceStatusChange>() {
            @Override
            public void handle(@NonNull DeviceStatusChange arg) {
                System.out.printf("DeviceStatusChanged event. status:'%s' text:'%s'%n",
                        arg.statusText, arg.statusText);
            }
        });
        events.CommsChannelStatusChanged.register(new MpiEventHandler<CommsStatusChange>() {
            @Override
            public void handle(@NonNull CommsStatusChange arg) {
                System.out.println("CommsChannelStatusChanged event");
            }
        });

        // ----------------------------------------------------
        // Execute
        // ----------------------------------------------------

        // Just incase the peds were used in another test: let them 'catch up'
        Thread.sleep(15L);

        btThread.start();
        wifiThread.start();

        boolean ok = counter.await(5L, TimeUnit.SECONDS);
        btThread.join(5L);
        wifiThread.join(5L);

        // verify

        assertThat(ok, is(true));
        assertThat(counter.getCount(), is(equalTo(0L)));
    }

    @Test
    public void transactionTest() throws Exception {
        // setup
        MpiEvents mpiEvents = new MpiEvents();
        //InetAddress addr = InetAddress.getByName("192.168.0.96");
        //ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);

        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        MpiClient client = new MpiClient(connector, mpiEvents);
        client.openSession();

        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        assert p2PEStatus != null;
        assertThat(p2PEStatus.isPINReady, is(true));
        assertThat(p2PEStatus.isSREDReady, is(true));

        client.systemClock(InterfaceType.MPI);
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.cardStatus(InterfaceType.MPI, false, true, true, true, true);

        // -----------------------------------------------------------------------------
        // execute
        // -----------------------------------------------------------------------------

        Result<byte[], TransactionResponse> startTransactionResult =
                client.startTransaction(InterfaceType.MPI, TransactionType.Purchase, 456, GBP);
        if (startTransactionResult.isError()) {
            TransactionResponse error = startTransactionResult.asError().getError();
            System.out.println(error);
            throw new AssertionError(error);
        }

        // ---
        byte[] startTransactionBytes = startTransactionResult.asSuccess().getValue();
        List<TLVObject> startTransactionResponse = TLVParser.decode(startTransactionBytes);

        TLVObject e4 = CommandUtil.firstMatch(
                startTransactionResponse, Description.Online_Authorisation_Required);
        assert e4 != null;
        for (TLVObject tlvObject : e4.getConstrustedTLV()) {
            //System.out.println("<<" + tlvObject + ">>");
        }

        // ---
        // continue transaction
        // ---
        ArrayList<TLVObject> continueTransactionParams = new ArrayList<>();

        TLVObject authCode = new TLVObject(Description.Authorisation_Response_Code,
                BinaryUtil.parseHexBinary("3030"));
        continueTransactionParams.add(authCode);

        TLVObject commandData = new TLVObject(
                Description.Command_Data, continueTransactionParams);

        Result<byte[], TransactionResponse> transactionResponse =
                client.continueTransaction(InterfaceType.MPI, commandData);
        if (transactionResponse.isError()) {
            TransactionResponse error = transactionResponse.asError().getError();
            System.out.println(error);
            throw new AssertionError(error);
        }

        byte[] continueTransactionBytes = transactionResponse.asSuccess().getValue();
        for (TLVObject tlvObject : TLVParser.decode(continueTransactionBytes)) {
            System.out.println("<<" + tlvObject + ">>");
        }

        client.closeSession();

        // verify
    }

    @Test
    public void abortTransactionAfterTransactionRespondsTest() throws Exception {
        // setup
        Thread.sleep(50L);

        MpiEvents mpiEvents = new MpiEvents();
        //InetAddress addr = InetAddress.getByName("192.168.0.96");
        //ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        MpiClient client = new MpiClientMTAbort(connector, mpiEvents);
        client.openSession();

        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        assert p2PEStatus != null;
        assertThat(p2PEStatus.isPINReady, is(true));
        assertThat(p2PEStatus.isSREDReady, is(true));

        client.systemClock(InterfaceType.MPI);
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.cardStatus(InterfaceType.MPI, false, true, true, true, true);

        // -----------------------------------------------------------------------------
        // execute
        // -----------------------------------------------------------------------------

        Result<byte[], TransactionResponse> startTransactionResult =
                client.startTransaction(InterfaceType.MPI, TransactionType.Purchase, 456, GBP);
        if (startTransactionResult.isError()) {
            TransactionResponse error = startTransactionResult.asError().getError();
            System.out.println(error);
            throw new AssertionError(error);
        }

        // decode results of startTransaction...
        byte[] startTransactionBytes = startTransactionResult.asSuccess().getValue();
        List<TLVObject> startTransactionResponse = TLVParser.decode(startTransactionBytes);
        TLVObject e4 = CommandUtil.firstMatch(
                startTransactionResponse, Description.Online_Authorisation_Required);
        assert e4 != null;
        for (TLVObject tlvObject : e4.getConstrustedTLV()) {
            //System.out.println("<<" + tlvObject + ">>");
        }

        // --
        // Now abort!
        boolean b = client.abortTransaction(InterfaceType.MPI);
        assertThat(b, is(true));

        // --
        client.closeSession();
    }

    @Test
    public void abortTransactionBeforeTransactionRespondsTest() throws Exception {
        // setup
        Thread.sleep(50L);

        MpiEvents mpiEvents = new MpiEvents();
        //InetAddress addr = InetAddress.getByName("192.168.0.96");
        //ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITHOUT_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        final MpiClient client = new MpiClientMTAbort(connector, mpiEvents);
        client.openSession();

        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        assert p2PEStatus != null;
        assertThat(p2PEStatus.isPINReady, is(true));
        assertThat(p2PEStatus.isSREDReady, is(true));

        client.systemClock(InterfaceType.MPI);
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.cardStatus(InterfaceType.MPI, false, true, true, true, true);

        List<Thread> threads = new ArrayList<>(5);

        // -----------------------------------------------------------------------------
        // execute
        // -----------------------------------------------------------------------------


        // start a thread to abort us in a small amount of time
        Runnable abortRunnable = new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // --
                // Now abort!
                boolean b = client.abortTransaction(InterfaceType.MPI);
                assertThat(b, is(true));
            }
        };
        Thread t1 = new Thread(abortRunnable, "abort thread 1");
        Thread t2 = new Thread(abortRunnable, "abort thread 2");
        Thread t3 = new Thread(abortRunnable, "abort thread 3");
        threads.addAll(Arrays.asList(t1, t2, t3));
        t1.start();
        t2.start();
        t3.start();

        // start the contactless transaction, which will do user-input on the PED and
        // therefore block. The abort should cause it to return.
        Result<byte[], TransactionResponse> response = client.startContactlessTransaction(
                InterfaceType.MPI, TransactionType.Purchase, 456, GBP, "GB"
        );
        assertThat(response.asError().getError(), is(TransactionResponse.USER_CANCELLED));

        Thread t4 = new Thread(abortRunnable, "abort thread 4");
        threads.add(t4);
        t4.start();

        client.displayText(InterfaceType.MPI, "Transaction failed!", false, false, false);

        Thread t5 = new Thread(abortRunnable, "abort thread 5");
        threads.add(t5);
        t5.start();

        // --
        for (Thread t : threads) {
            t.join(2000L);
            assertThat(t.isAlive(), is(false));
        }
        client.closeSession();
    }

    @Test
    public void closeInAbortThreadCloseAfterDisplayText() throws Exception {
        // setup
        Thread.sleep(50L);

        MpiEvents mpiEvents = new MpiEvents();
        //InetAddress addr = InetAddress.getByName("192.168.0.96");
        //ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITHOUT_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        final MpiClient client = new MpiClientMTAbort(connector, mpiEvents);
        client.openSession();

        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        assert p2PEStatus != null;
        assertThat(p2PEStatus.isPINReady, is(true));
        assertThat(p2PEStatus.isSREDReady, is(true));

        client.systemClock(InterfaceType.MPI);
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.cardStatus(InterfaceType.MPI, false, true, true, true, true);

        // -----------------------------------------------------------------------------
        // execute
        // -----------------------------------------------------------------------------


        // start a thread to abort us in a small amount of time
        Runnable abortRunnable = new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // --
                // Now abort!
                boolean b = client.abortTransaction(InterfaceType.MPI);
                assertThat(b, is(true));
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                client.closeSession();
            }
        };
        Thread t1 = new Thread(abortRunnable, "abort thread 1");
        t1.start();

        // start the contactless transaction, which will do user-input on the PED and
        // therefore block. The abort should cause it to return.
        Result<byte[], TransactionResponse> response = client.startContactlessTransaction(
                InterfaceType.MPI, TransactionType.Purchase, 456, GBP, "GB"
        );
        assertThat(response.asError().getError(), is(TransactionResponse.USER_CANCELLED));

        client.displayText(InterfaceType.MPI, "Transaction failed!", false, false, false);


        // --
        t1.join(5000L);
        assertThat(t1.isAlive(), is(false));

        client.closeSession();
    }

    @Ignore("Requires specific live PEDs")
    @Test
    public void closeInAbortThreadCloseBeforeDisplayText() throws Exception {
        // setup
        Thread.sleep(50L);

        MpiEvents mpiEvents = new MpiEvents();
        //InetAddress addr = InetAddress.getByName("192.168.0.96");
        //ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITHOUT_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        final MpiClient client = new MpiClientMTAbort(connector, mpiEvents);
        client.openSession();

        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        assert p2PEStatus != null;
        assertThat(p2PEStatus.isPINReady, is(true));
        assertThat(p2PEStatus.isSREDReady, is(true));

        client.systemClock(InterfaceType.MPI);
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.cardStatus(InterfaceType.MPI, false, true, true, true, true);

        // -----------------------------------------------------------------------------
        // execute
        // -----------------------------------------------------------------------------


        // start a thread to abort us in a small amount of time
        Runnable abortRunnable = new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(150L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // --
                // Now abort!
                boolean b = client.abortTransaction(InterfaceType.MPI);
                assertThat(b, is(true));
                client.closeSession();
            }
        };
        Thread t1 = new Thread(abortRunnable, "abort thread 1");
        t1.start();

        // start the contactless transaction, which will do user-input on the PED and
        // therefore block. The abort should cause it to return.
        Result<byte[], TransactionResponse> response = client.startContactlessTransaction(
                InterfaceType.MPI, TransactionType.Purchase, 456, GBP, "GB"
        );
        assertThat(response.asError().getError(), is(TransactionResponse.USER_CANCELLED));

        Thread.sleep(50L);
        try {
            client.displayText(InterfaceType.MPI, "Transaction failed!", false, false, false);
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage().toLowerCase(), containsString("session not open"));
        }

        // --
        t1.join(5000L);
        assertThat(t1.isAlive(), is(false));

        client.closeSession();
    }

    @Ignore
    @Test
    public void displayTextFailsAfterCardEvent() throws Exception {
        MpiEvents mpiEvents = new MpiEvents();
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        final AtomicBoolean cardRemoved = new AtomicBoolean(false);
        final CountDownLatch waitForCardInsert = new CountDownLatch(1);

        mpiEvents.CardStatusChanged.register(new MpiEventHandler<CardData>() {
            @Override
            public void handle(@NonNull CardData arg) {
                System.out.println("CardStatus");

                CardStatus cardStatus = arg.getCardStatus();
                if (cardStatus != null) {
                    System.out.printf(
                            "cardPresent: %s, compat:%s%n",
                            cardStatus.isCardPresent(), cardStatus.isEMVCompatible()
                    );
                    if (cardStatus.isCardPresent()) {
                        waitForCardInsert.countDown();
                    } else if (waitForCardInsert.getCount() == 0L) {
                        cardRemoved.set(true);
                    }
                }
            }
        });

        final MpiClient client = new MpiClientMTAbort(connector, mpiEvents);
        client.openSession();

        boolean success;

//            success = client.systemLog(InterfaceType.MPI, SystemLogMode.Archive);
//            assertThat(success, is(true));

        success = client.systemLog(InterfaceType.MPI, SystemLogMode.Remove);
        assertThat(success, is(true));

        success = client.displayText(InterfaceType.MPI, "Insert card", true, true, true);
        assertThat(success, is(true));

        client.cardStatus(InterfaceType.MPI, true, false, true, true, true);

        waitForCardInsert.await();

        client.cardStatus(InterfaceType.MPI, false, false, true, true, true);

        Result<byte[], TransactionResponse> result = client.startTransaction(
                InterfaceType.MPI, TransactionType.Purchase, 123, 826
        );
        assertThat(result.isSuccess(), is(true));

        int i = 0;
        boolean problemHappened = false;
        while (!cardRemoved.get()) {
            success = client.displayText(
                    InterfaceType.MPI, "Yank card out! NOW! " + i, true, true, true
            );
            if (!success) {
                System.out.println("Problem happened!");
                problemHappened = true;
                break;
            }
            Thread.sleep(60L);
            i += 1;
        }

        assertThat(problemHappened, is(true));
        // assertThat(cardRemoved.get(), is(true));


        System.out.println("Getting log file...");

        // fixme: why do I have to close and open session to get the log file?
        // Otherwise select_file returns -1 for file size?
        // because the stream gets out of sync
        client.closeSession();
        client.openSession();

        success = client.systemLog(InterfaceType.MPI, SystemLogMode.Archive);
        assertThat(success, is(true));

        byte[] bytes = GetDeviceFile.getDeviceFile(
                client, InterfaceType.MPI, "mpi.log", new GetDeviceFile.ProgressCallback() {
                    @Override
                    public void onProgress(float fraction) {
                        System.out.printf("Reading log: %f%%%n", fraction * 100.0f);
                    }
                }
        );

        assertThat(bytes, is(notNullValue()));

        client.closeSession();

        Thread.sleep(150L);
        System.err.flush();
        System.out.flush();

        assert bytes != null;
        System.out.println("-----------------------------------------------------------------");
        System.out.println(new String(bytes, "US-ASCII"));
        System.out.println("-----------------------------------------------------------------");
    }


    @Ignore("Requires specific live PEDs and user interaction")
    @Test
    public void onlinePinTest() throws Exception {
        // setup
        Thread.sleep(50L);

        MpiEvents mpiEvents = new MpiEvents();
        //InetAddress addr = InetAddress.getByName("192.168.0.96");
        //ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        MpiClient client = new MpiClientMTAbort(connector, mpiEvents);
        client.openSession();

        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        assert p2PEStatus != null;
        assertThat(p2PEStatus.isPINReady, is(true));
        assertThat(p2PEStatus.isSREDReady, is(true));

        client.systemClock(InterfaceType.MPI);
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.cardStatus(InterfaceType.MPI, true, false, true, true, true);

        final ArrayBlockingQueue<CardData> queue = new ArrayBlockingQueue<>(1);
        mpiEvents.CardStatusChanged.register(new MpiEventHandler<CardData>() {
            @Override
            public void handle(@NonNull CardData cardData) {

                System.out.println("CardData!");

                if (!cardData.getCardStatus().isMSRDataAvailable()) {
                    System.out.println("!isMSRDataAvailable");
                    return;
                }
                if (!cardData.getCardStatus().isTrack2DataAvailable()) {
                    System.out.println("!isTrack2DataAvailable");
                    return;
                }

                try {
                    queue.put(cardData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new AssertionError();
                }
            }
        });

        // -----------------------------------------------------------------------------
        // execute
        // -----------------------------------------------------------------------------
        CardData cardData = queue.take();
        Track2Data maskedTrack2Data = cardData.getMaskedTrack2Data();
        assertThat(maskedTrack2Data.getPAN(), is(notNullValue()));

        Result<OnlinePinResult, OnlinePINError> onlinePinOption =
                client.onlinePin(InterfaceType.MPI, 5000, GBP, maskedTrack2Data, "test label");

        if (onlinePinOption.isError()) {
            OnlinePINError error = onlinePinOption.asError().getError();
            System.out.println(error);
            throw new AssertionError(error);
        }

        // decode results of onlinePin...
        OnlinePinResult onlinePinResult = onlinePinOption.asSuccess().getValue();
        assertThat(onlinePinResult.mType, is(equalTo(PinEnteredOk)));
        System.out.println(onlinePinResult.PinData);
        System.out.println(onlinePinResult.PinKsn);

        // --
        client.closeSession();
    }

    @Ignore("Requires specific live PEDs and user interaction")
    @Test
    public void abortOnlinePinTest() throws Exception {
        // setup
        Thread.sleep(50L);

        MpiEvents mpiEvents = new MpiEvents();
        //InetAddress addr = InetAddress.getByName("192.168.0.96");
        //ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        final MpiClient client = new MpiClientMTAbort(connector, mpiEvents);
        client.openSession();

        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        assert p2PEStatus != null;
        assertThat(p2PEStatus.isPINReady, is(true));
        assertThat(p2PEStatus.isSREDReady, is(true));

        client.systemClock(InterfaceType.MPI);
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.cardStatus(InterfaceType.MPI, true, false, true, true, true);

        final ArrayBlockingQueue<CardData> queue = new ArrayBlockingQueue<>(1);
        mpiEvents.CardStatusChanged.register(new MpiEventHandler<CardData>() {
            @Override
            public void handle(@NonNull CardData cardData) {

                System.out.println("CardData!");

                if (!cardData.getCardStatus().isMSRDataAvailable()) {
                    System.out.println("!isMSRDataAvailable");
                    return;
                }
                if (!cardData.getCardStatus().isTrack2DataAvailable()) {
                    System.out.println("!isTrack2DataAvailable");
                    return;
                }

                try {
                    queue.put(cardData);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new AssertionError();
                }
            }
        });

        List<Thread> threads = new ArrayList<>(5);

        // -----------------------------------------------------------------------------
        // execute
        // -----------------------------------------------------------------------------
        System.out.println("Waiting for card swipe");
        CardData cardData = queue.take();
        Track2Data maskedTrack2Data = cardData.getMaskedTrack2Data();
        assertThat(maskedTrack2Data.getPAN(), is(notNullValue()));

        // start a thread to abort us in a small amount of time
        Runnable abortRunnable = new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(600L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // --
                // Now abort!
                System.out.println("aborting!");
                boolean b = client.abortTransaction(InterfaceType.MPI);
                System.out.println("abort done!");
                assertThat(b, is(true));
            }
        };
        Thread t1 = new Thread(abortRunnable, "abort thread 1");
        Thread t2 = new Thread(abortRunnable, "abort thread 2");
        Thread t3 = new Thread(abortRunnable, "abort thread 3");
        threads.addAll(Arrays.asList(t1, t2, t3));
        t1.start();
        t2.start();
        t3.start();


        System.out.println("start online pin!");
        Result<OnlinePinResult, OnlinePINError> onlinePinOption =
                client.onlinePin(InterfaceType.MPI, 5000, GBP, maskedTrack2Data, "test label");
        OnlinePinResult result = onlinePinOption.asSuccess().getValue();
        assertThat(result.mType, is(MpiClient.OnlinePinResultType.CancelOrTimeout));

        Thread t4 = new Thread(abortRunnable, "abort thread 4");
        threads.add(t4);
        t4.start();

        client.displayText(InterfaceType.MPI, "Online Pin failed!", false, false, false);

        Thread t5 = new Thread(abortRunnable, "abort thread 5");
        threads.add(t5);
        t5.start();

        // --
        for (Thread t : threads) {
            t.join(2000L);
            assertThat(t.isAlive(), is(false));
        }
        client.closeSession();
    }

    @Test
    @SuppressWarnings("unchecked")
    @Ignore("Requires specific live PEDs and user interaction")
    public void testPrintEvents() throws Exception {
        //----------
        //This test requires you to interact with a PED.
        //----------

        // setup
        InetAddress addr = InetAddress.getByName("192.168.0.96");
        ClientSocketConnector connector = new ClientSocketConnector(addr, 6543);
        // String url = JavaBluetoothConnector.getUrlForDevice("Miura 515");
        //JavaBluetoothConnector connector = new JavaBluetoothConnector(url);

        final CountDownLatch latch = new CountDownLatch(1);

        MpiEventHandler mpiEventPrinter = new MpiEventHandler() {
            @Override
            public void handle(@NonNull Object arg) {
                System.out.println(arg);
            }
        };
        MpiEvents mpiEvents = new MpiEvents();
        mpiEvents.Disconnected.register(mpiEventPrinter);
        mpiEvents.CardStatusChanged.register(mpiEventPrinter);
        mpiEvents.DeviceStatusChanged.register(mpiEventPrinter);
        mpiEvents.PrinterStatusChanged.register(mpiEventPrinter);
        mpiEvents.CommsChannelStatusChanged.register(mpiEventPrinter);
        mpiEvents.BarcodeScanned.register(mpiEventPrinter);
        mpiEvents.UsbSerialPortDataReceived.register(mpiEventPrinter);

        mpiEvents.KeyPressed.register(new MpiEventHandler<Integer>() {
            @Override
            public void handle(@NonNull Integer arg) {
                System.out.println(arg);
                if (arg == 27) {
                    System.out.print("Escape key pressed, exiting!");
                    latch.countDown();
                }
            }
        });

        MpiClient client = new MpiClient(connector, mpiEvents);
        client.openSession();

        client.cardStatus(InterfaceType.MPI, true, true, true, true, true);

        boolean b1 = client.keyboardStatus(
                InterfaceType.MPI, StatusSettings.Enable, BacklightSettings.Enable);
        assertThat(b1, is(true));

        BatteryData batteryData = client.batteryStatus(InterfaceType.MPI, false);
        assert batteryData != null;

        boolean b2 = client.printerSledStatus(InterfaceType.MPI, true);
        assertThat(b2, is(true));

        latch.await(2L, TimeUnit.SECONDS);
        //latch.await();

        client.closeSession();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void poszleTest() throws Exception {
        String url = JavaBluetoothConnector.getUrlForDevice(BT_POS_DEVICE);
        JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);

        MpiEventHandler mpiEventPrinter = new MpiEventHandler() {
            @Override
            public void handle(@NonNull Object arg) {
                System.out.println(arg);
            }
        };
        MpiEvents mpiEvents = new MpiEvents();
        mpiEvents.Disconnected.register(mpiEventPrinter);
        mpiEvents.CardStatusChanged.register(mpiEventPrinter);
        mpiEvents.DeviceStatusChanged.register(mpiEventPrinter);
        mpiEvents.PrinterStatusChanged.register(mpiEventPrinter);
        mpiEvents.CommsChannelStatusChanged.register(mpiEventPrinter);
        mpiEvents.BarcodeScanned.register(mpiEventPrinter);
        mpiEvents.UsbSerialPortDataReceived.register(mpiEventPrinter);
        mpiEvents.KeyPressed.register(mpiEventPrinter);


        MpiClient btClient = new MpiClient(btConnector, mpiEvents);
        btClient.openSession();

        SoftwareInfo softwareInfo = btClient.resetDevice(
                InterfaceType.RPI, ResetDeviceType.Soft_Reset);

        System.out.println(softwareInfo.toString());

        btClient.closeSession();
    }

    @Test
    public void streamBinaryTest() throws Exception {
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        MpiEvents mpiEvents = new MpiEvents();
        MpiClient client = new MpiClient(btConnector, mpiEvents);

        client.openSession();
        Thread.sleep(20L);

        boolean success = client.systemLog(InterfaceType.MPI, SystemLogMode.Remove);
        assertThat(success, is(true));

        int pedFileSize = client.selectFile(
                InterfaceType.MPI, SelectFileMode.Truncate, "test.txt");
        assertThat(pedFileSize, is(greaterThanOrEqualTo(0)));

        byte[] buffer = new byte[26 + 8];

        byte[] rubbish = new byte[]{
                (byte) 0x01, (byte) 0x00, (byte) 0x04,
                (byte) 0xD0, (byte) 0x00,
                (byte) 0x00, (byte) 0x00,
                (byte) 0x01
        };
        byte[] lowerString = "abcdefghijklmnopqrstuvwxyz".getBytes("US-ASCII");
        byte[] upperString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".getBytes("US-ASCII");

        System.arraycopy(lowerString, 0, buffer, 0, 26);
        System.arraycopy(rubbish, 0, buffer, 26, 8);
        success = client.streamBinary(
                InterfaceType.MPI, false, buffer, 26, 26, 1000);
        assertThat(success, is(true));

        System.arraycopy(upperString, 0, buffer, 0, 26);
        success = client.streamBinary(
                InterfaceType.MPI, false, buffer, 0, 26, 1000);
        assertThat(success, is(true));

        // random soft reset, just to ensure command stream hasn't caused chaos.
        SoftwareInfo softwareInfo = client.resetDevice(
                InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        if (softwareInfo == null) throw new AssertionError();
        String serialNumber = softwareInfo.getSerialNumber();
        assertThat(serialNumber, StringEndsWith.endsWith("515"));

        pedFileSize = client.selectFile(
                InterfaceType.MPI, SelectFileMode.Append, "test.txt");
        System.out.println(pedFileSize);
        assertThat(pedFileSize, is(equalTo(52)));

        ResponseMessage rm = client.readBinary(InterfaceType.MPI, 52, 0, 52);
        assert rm != null;

        assertThat(rm.getStatusCode(), is(equalTo(0x9000)));
        assertThat(rm.isSuccess(), is(true));
        client.closeSession();

        String rcvString = new String(rm.getBody(), "UTF-8");
        String expectedString = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";


        assertThat(rcvString, is(equalTo(expectedString)));
    }

    @Test
    public void getLog() throws Exception {
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        MpiEvents mpiEvents = new MpiEvents();
        MpiClient client = new MpiClient(btConnector, mpiEvents);

        client.openSession();
        Thread.sleep(20L);

        boolean success = client.systemLog(InterfaceType.MPI, SystemLogMode.Archive);
        assertThat(success, is(true));

        byte[] bytes = GetDeviceFile.getDeviceFile(
                client, InterfaceType.MPI, "mpi.log", new GetDeviceFile.ProgressCallback() {
                    @Override
                    public void onProgress(float fraction) {
                        System.out.printf("Reading log: %f%%%n", fraction * 100.0f);
                    }
                }
        );
        success = client.systemLog(InterfaceType.MPI, SystemLogMode.Remove);
        assertThat(success, is(true));

        System.out.println("-----------------------------------------------------------------");
        System.out.println(new String(bytes, "US-ASCII"));
        System.out.println("-----------------------------------------------------------------");

        client.closeSession();
    }

    @Test
    public void createLargeLogFile() throws Exception {

        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        final JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        final MpiEvents mpiEvents = new MpiEvents();
        final MpiClient client = new MpiClientMTAbort(btConnector, mpiEvents);

        client.openSession();
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Clear_Files);

        for (int i = 0; i < 10000; i++) {
            // resetDevice is quick and generates multiple lines of log.
            client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
            if (i % 25 == 0) {
                System.out.println("resetDevice: " + i);
            }
        }

        client.closeSession();
    }

    @Test
    public void closeDuringStreamBinaryFile() throws Exception {

        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        final JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        final MpiEvents mpiEvents = new MpiEvents();
        final MpiClient client = new MpiClientMTAbort(btConnector, mpiEvents);

        InfiniteInputStream infiniteStream = new InfiniteInputStream(0x1000);

        client.openSession();
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Clear_Files);

        ProgressCallback callback = new ProgressCallback() {
            @Override
            public void onProgress(int bytesTransferred) {
                System.out.println("onProgress: " + bytesTransferred);

                if (bytesTransferred >= 0xffff) {
                    client.closeSession();
                }
            }
        };
        boolean status = StreamBinaryFile.streamBinaryFile(
                client, InterfaceType.MPI, "test.bin", infiniteStream, callback);

        client.closeSession();

        assertThat(status, is(false));
    }

    @Test
    public void multiThreadedCloseDuringStreamBinaryFile() throws Exception {

        // Setup
        //------------------------------------------------------------------------
        final String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        final JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        final MpiEvents mpiEvents = new MpiEvents();
        final MpiClient client = new MpiClientMTAbort(btConnector, mpiEvents);
        // Will lock up or abort with MpiClient, as expected.
        // final MpiClient client = new MpiClient(btConnector, mpiEvents);
        final InfiniteInputStream infiniteStream = new InfiniteInputStream(0x1000);

        ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

        for (int testRun = 0; testRun < 10; testRun++) {

            System.out.println("multiThreadedCloseDuringStreamBinaryFile: " + testRun);

            final CountDownLatch latch = new CountDownLatch(1);
            final ProgressCallback callback = new ProgressCallback() {
                @Override
                public void onProgress(int bytesTransferred) {
                    System.out.println("onProgress: " + bytesTransferred);
                    if (bytesTransferred >= 0xffff) {
                        latch.countDown();
                        System.out.println("countDown!");
                    }
                }
            };
            Runnable command = new Runnable() {
                @Override
                public void run() {
                    System.out.println("command started");

                    boolean status = StreamBinaryFile.streamBinaryFile(
                            client, InterfaceType.MPI, "test.bin", infiniteStream, callback);
                    assertThat(status, is(false));
                    status = client.displayText(InterfaceType.MPI, "ROFL", false, false, false);
                    assertThat(status, is(false));

                    System.out.println("command finished");
                }
            };
            mpiEvents.Disconnected.register(new MpiEventHandler<ConnectionInfo>() {
                @Override
                public void handle(@NonNull ConnectionInfo arg) {
                    System.out.println("Disconnected!");
                    latch.countDown();
                }
            });

            // Execute
            //------------------------------------------------------------------------
            client.openSession();
            client.resetDevice(InterfaceType.MPI, ResetDeviceType.Clear_Files);
            //Future<?> future = mExecutor.schedule(command, 10L, TimeUnit.MILLISECONDS);
            Future<?> future = mExecutor.submit(command);

            // Wait until we've streamed at least N bytes
            System.out.println("await!");
            latch.await();
            //noinspection BusyWait
            Thread.sleep(30L);

            // Close the session. hopefully in an incontinent place in MpiClient.
            client.closeSession();

            // Verify
            //------------------------------------------------------------------------
            // ensure the task has finished and didn't throw an exception.
            future.get(333L, TimeUnit.MILLISECONDS);
        }
    }

    @Test
    public void multiThreadedCloseDuringStreamBinaryFileEarlyClose() throws Exception {

        // Setup
        //------------------------------------------------------------------------
        final String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        final JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        final MpiEvents mpiEvents = new MpiEvents();
        final MpiClient client = new MpiClientMTAbort(btConnector, mpiEvents);
        // Will lock up or abort with MpiClient, as expected.
        // final MpiClient client = new MpiClient(btConnector, mpiEvents);
        final InfiniteInputStream infiniteStream = new InfiniteInputStream(0x1000);

        ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();

        for (int testRun = 0; testRun < 10; testRun++) {

            System.out.println("multiThreadedCloseDuringStreamBinaryFile: " + testRun);

            final CountDownLatch latch = new CountDownLatch(1);
            final ProgressCallback callback = new ProgressCallback() {
                @Override
                public void onProgress(int bytesTransferred) {
                    System.out.println("onProgress: " + bytesTransferred);
                    if (bytesTransferred >= 0xffff) {
                        latch.countDown();
                        System.out.println("countDown!");
                    }
                }
            };
            Runnable command = new Runnable() {
                @Override
                public void run() {
                    System.out.println("command started thread:" +
                            Thread.currentThread().getId());

                    boolean status = StreamBinaryFile.streamBinaryFile(
                            client, InterfaceType.MPI, "test.bin", infiniteStream, callback);
                    assertThat(status, is(false));
                    status = client.displayText(InterfaceType.MPI, "ROFL", false, false, false);
                    assertThat(status, is(false));

                    System.out.println("command finished");
                }
            };
            mpiEvents.Disconnected.register(new MpiEventHandler<ConnectionInfo>() {
                @Override
                public void handle(@NonNull ConnectionInfo arg) {
                    System.out.println(
                            "Disconnected! thread:" + Thread.currentThread().getId());
                    latch.countDown();
                }
            });

            // Execute
            //------------------------------------------------------------------------
            System.out.println("Open session!");
            client.openSession();
            client.resetDevice(InterfaceType.MPI, ResetDeviceType.Clear_Files);

            System.out.println("Close session via connector!");
            btConnector.closeSession();

            //Future<?> future = mExecutor.schedule(command, 10L, TimeUnit.MILLISECONDS);
            Future<?> future = mExecutor.submit(command);

            // Wait until we've streamed at least N bytes
            System.out.println("await!");
            latch.await();
            //noinspection BusyWait
            Thread.sleep(30L);

            // Close the session. hopefully in an incontinent place in MpiClient.
            client.closeSession();

            // Verify
            //------------------------------------------------------------------------
            // ensure the task has finished and didn't throw an exception.
            future.get(333L, TimeUnit.MILLISECONDS);
        }
    }

    @Ignore("Requires specific live PEDs")
    @Test
    public void getNumericData() throws Exception {
        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        MpiEvents mpiEvents = new MpiEvents();
        MpiClient client = new MpiClient(btConnector, mpiEvents);

        client.openSession();

        Result<String, GetNumericDataError> numericData = client.getNumericData(
                InterfaceType.MPI,
                false, true,
                1, 0, 0,
                4, 2,
                "1234",
                5432, 826, 2, 2
        );

        if (numericData.isSuccess()) {
            System.out.println("getNumericData: " + numericData.asSuccess().getValue());
        } else {
            System.out.println("getNumericData: " + numericData.asError().getError());
        }
        assertThat(numericData.isSuccess(), is(true));

        client.closeSession();
    }

    @Test
    public void getDynamicTip() throws Exception {

        String url = JavaBluetoothConnector.getUrlForDevice(BT_PED_DEVICE_WITH_CARD);
        JavaBluetoothConnector btConnector = new JavaBluetoothConnector(url);
        MpiEvents mpiEvents = new MpiEvents();
        MpiClient client = new MpiClient(btConnector, mpiEvents);

        client.openSession();

        Result<Integer, GetNumericDataError> numericData = client.getDynamicTip(
                false, false,
                Arrays.asList(0, 5, 10, 255),
                1,
                3400, 826, 2
        );
        if (numericData.isSuccess()) {
            System.out.println("getDynamicTip: " + numericData.asSuccess().getValue());
        } else {
            System.out.println("getDynamicTip: " + numericData.asError().getError());
        }
        assertThat(numericData.isSuccess(), is(true));

        client.closeSession();
    }


    public static class MultiClientTestRunnable implements Runnable {
        private final String mWhat;
        private final CountDownLatch mLatch;
        private final MpiClient mClient;

        public MultiClientTestRunnable(
                final String what, final CountDownLatch latch, MpiClient client) {
            mWhat = what;
            mLatch = latch;
            mClient = client;
        }

        @Override
        public void run() {
            System.out.println(mWhat + " run()");

            try {
                System.out.println(mWhat + " calling openSession()");
                mClient.openSession();

                System.out.println(mWhat + " calling doTest()");
                doTest();

                System.out.println(mWhat + " calling countDown()");
                // give the 'all clear' signal
                mLatch.countDown();
            } catch (Throwable e) {
                e.printStackTrace();
            } finally {
                System.out.println(mWhat + " calling closeSession()");

                mClient.closeSession();
            }
        }

        private void doTest() throws IOException {
            System.out.println(mWhat + " doTest()");

            boolean ok;
            BatteryData batteryData = mClient.batteryStatus(InterfaceType.MPI, false);
            if (batteryData == null) {
                throw new IOException(mWhat + " batteryStatus(NoSleep) failed");
            }
            log(String.format("battery: %s, %s%%",
                    batteryData.mChargingStatus, batteryData.mBatteryLevel));

            ok = mClient.systemLog(InterfaceType.MPI, SystemLogMode.Remove);
            if (!ok) {
                throw new IOException(mWhat + " systemLog(Remove) failed");
            }
            log("Log removed ok");

            ok = mClient.systemClock(InterfaceType.MPI, new Date());
            if (!ok) {
                throw new IOException(mWhat + " systemClock(now) failed");
            }
            log("Updating clock");

            Date date = mClient.systemClock(InterfaceType.MPI);
            if (date == null) {
                throw new IOException(mWhat + " systemClock() failed");
            }
            log("Date = " + date);


            SoftwareInfo info = mClient.resetDevice(
                    InterfaceType.MPI, ResetDeviceType.Soft_Reset);
            if (info == null) {
                throw new IOException(mWhat + " resetDevice(Soft_Reset) failed");
            }
            log(info.toString());
            // check OS and update -- not done
            // check MPI and update -- not done


            HashMap<String, String> configuration = mClient.getConfiguration();
            if (configuration == null) {
                throw new IOException(mWhat + " getConfiguration() failed");
            }
            log("Get configuration");
            for (Map.Entry<String, String> entry : configuration.entrySet()) {
                log("\t" + entry.getKey() + ": " + entry.getValue());
            }

            P2PEStatus p2PEStatus = mClient.p2peStatus(InterfaceType.MPI);
            if (p2PEStatus == null) {
                throw new IOException(mWhat + " p2peStatus() failed");
            }
            log(String.format("P2PEStatus init:%s PIN:%s SRED:%s %n",
                    p2PEStatus.isInitialised,
                    p2PEStatus.isPINReady,
                    p2PEStatus.isSREDReady
            ));
        }

        private void log(String text) throws IOException {
            // System.out.println(String.format("%-21s | %s", mWhat, text));
            boolean ok = mClient.displayText(InterfaceType.MPI, text, true, true, true);
            if (!ok) {
                throw new IOException(mWhat + " displayText(" + text + ") failed");
            }
        }
    }

}


