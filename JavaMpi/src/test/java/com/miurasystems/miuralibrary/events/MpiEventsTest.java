/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.enums.DeviceStatus;
import com.miurasystems.miuralibrary.enums.M012Printer;
import com.miurasystems.miuralibrary.tlv.CardData;

import org.junit.Assert;
import org.junit.Test;

import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicInteger;

public class MpiEventsTest {

    /**
     * Test that the grouplock is wired correctly and that no event can call another.
     */
    @Test
    public void groupLock() {
        // setup
        // -----------------------------------------------------------------------------------
        final MpiEvents mpiEvents = new MpiEvents();

        final AtomicInteger notifyCounter = new AtomicInteger(0);

        final MpiEventHandler<byte[]> usbHandler =
                new MpiEventHandler<byte[]>() {
                    @Override
                    public void handle(@NonNull byte[] arg) {
                        try {
                            mpiEvents.UsbSerialPortDataReceived.notifyListener(arg);
                            Assert.fail();
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                        mpiEvents.UsbSerialPortDataReceived.deregister(this);
                    }
                };
        final MpiEventHandler<String> barcodeHandler =
                new MpiEventHandler<String>() {
                    @Override
                    public void handle(@NonNull String arg) {
                        mpiEvents.BarcodeScanned.deregister(this);

                        mpiEvents.UsbSerialPortDataReceived.register(usbHandler);
                        try {
                            mpiEvents.UsbSerialPortDataReceived.notifyListener(arg.getBytes());
                            Assert.fail();
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };
        final MpiEventHandler<CommsStatusChange> commsChangedHandler =
                new MpiEventHandler<CommsStatusChange>() {
                    @Override
                    public void handle(@NonNull CommsStatusChange arg) {
                        mpiEvents.CommsChannelStatusChanged.deregister(this);

                        mpiEvents.BarcodeScanned.register(barcodeHandler);
                        try {
                            mpiEvents.BarcodeScanned.notifyListener("01234");
                            Assert.fail();
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };
        final MpiEventHandler<M012Printer> printerHandler =
                new MpiEventHandler<M012Printer>() {
                    @Override
                    public void handle(@NonNull M012Printer arg) {
                        mpiEvents.PrinterStatusChanged.deregister(this);

                        mpiEvents.CommsChannelStatusChanged.register(commsChangedHandler);
                        try {
                            mpiEvents.CommsChannelStatusChanged.notifyListener(
                                    new CommsStatusChange());
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };
        final MpiEventHandler<DeviceStatusChange> deviceHandler =
                new MpiEventHandler<DeviceStatusChange>() {
                    @Override
                    public void handle(@NonNull DeviceStatusChange arg) {
                        mpiEvents.DeviceStatusChanged.deregister(this);

                        mpiEvents.PrinterStatusChanged.register(printerHandler);
                        try {
                            mpiEvents.PrinterStatusChanged.notifyListener(
                                    M012Printer.Printer_Error);
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };
        final MpiEventHandler<Integer> keyPressedHandler =
                new MpiEventHandler<Integer>() {
                    @Override
                    public void handle(@NonNull Integer arg) {
                        mpiEvents.KeyPressed.deregister(this);

                        mpiEvents.DeviceStatusChanged.register(deviceHandler);
                        try {
                            mpiEvents.DeviceStatusChanged.notifyListener(
                                    new DeviceStatusChange(DeviceStatus.DevicePoweredOn,
                                            "DevicePoweredOn"));
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };
        final MpiEventHandler<CardData> cardHandler =
                new MpiEventHandler<CardData>() {
                    @Override
                    public void handle(@NonNull CardData arg) {
                        mpiEvents.CardStatusChanged.deregister(this);

                        mpiEvents.KeyPressed.register(keyPressedHandler);
                        try {
                            mpiEvents.KeyPressed.notifyListener(13);
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };
        final MpiEventHandler<ConnectionInfo> disconnectedHandler =
                new MpiEventHandler<ConnectionInfo>() {
                    @Override
                    public void handle(@NonNull ConnectionInfo arg) {
                        mpiEvents.Disconnected.deregister(this);

                        mpiEvents.CardStatusChanged.register(cardHandler);
                        try {
                            mpiEvents.CardStatusChanged.notifyListener(new CardData());
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };
        final MpiEventHandler<ConnectionInfo> connectedHandler =
                new MpiEventHandler<ConnectionInfo>() {
                    @Override
                    public void handle(@NonNull ConnectionInfo arg) {
                        mpiEvents.Connected.deregister(this);

                        mpiEvents.Disconnected.register(disconnectedHandler);
                        try {
                            mpiEvents.Disconnected.notifyListener(arg);
                        } catch (ConcurrentModificationException ignore) {
                            notifyCounter.incrementAndGet();
                        }
                    }
                };

        // execute
        // -----------------------------------------------------------------------------------
        mpiEvents.Connected.register(connectedHandler);

        mpiEvents.Connected.notifyListener(new ConnectionInfo(true));
        mpiEvents.Disconnected.notifyListener(new ConnectionInfo(false));
        mpiEvents.CardStatusChanged.notifyListener(new CardData());
        mpiEvents.KeyPressed.notifyListener(15);
        mpiEvents.DeviceStatusChanged.notifyListener(
                new DeviceStatusChange(DeviceStatus.DevicePoweredOn, "Device Powered On!"));
        mpiEvents.PrinterStatusChanged.notifyListener(M012Printer.Printer_Error);
        mpiEvents.CommsChannelStatusChanged.notifyListener(new CommsStatusChange());
        mpiEvents.BarcodeScanned.notifyListener("0123456789");
        //noinspection ImplicitNumericConversion
        mpiEvents.UsbSerialPortDataReceived.notifyListener(new byte[]{
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20
        });

        // verify
        // -----------------------------------------------------------------------------------
        //noinspection ImplicitNumericConversion
        assertThat(notifyCounter.get(), is(equalTo(9)));
    }

}
