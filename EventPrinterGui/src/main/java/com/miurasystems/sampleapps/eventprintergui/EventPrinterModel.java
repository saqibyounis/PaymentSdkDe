/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.eventprintergui;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.examples.connectors.JavaBluetoothConnector;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.enums.BacklightSettings;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.StatusSettings;
import com.miurasystems.miuralibrary.events.ConnectionInfo;
import com.miurasystems.miuralibrary.events.MpiEventHandler;
import com.miurasystems.miuralibrary.events.MpiEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.bluetooth.RemoteDevice;

public class EventPrinterModel implements EventPrinterMvp.Model {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventPrinterModel.class);
    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();
    private static final MpiEvents MPI_EVENTS = MIURA_MANAGER.getMpiEvents();

    @SuppressWarnings("rawtypes")
    private final MpiEventHandler mMpiEventPrinter;

    private final MpiEventHandler<ConnectionInfo> mHandleDisconnect;

    @Nullable
    private EventPrinterMvp.ModelPresenter mPresenter;

    @Nullable
    private String mCurrentDeviceName;

    @Nullable
    private JavaBluetoothConnector mCurrentConnector;

    EventPrinterModel() {
        mMpiEventPrinter = this::printEvent;
        mHandleDisconnect = this::handleUnexpectedDisconnect;

        mPresenter = null;
        mCurrentDeviceName = null;
        mCurrentConnector = null;
    }

    @Override
    public void setPresenter(@Nullable EventPrinterMvp.ModelPresenter presenter) {
        LOGGER.trace("Presenter attached: {}", presenter);
        mPresenter = presenter;
    }

    @Override
    public void connectBt(String name) throws IOException {
        LOGGER.trace("connectBt({})", name);

        printEvent("Establishing connection to " + name);

        JavaBluetoothConnector connector = getConnectorForDevice(name);
        LOGGER.trace("connectBt: connector:{}", connector);

        registerEventHandlers();

        String lower = name.toLowerCase();
        InterfaceType iface;
        if (lower.contains("pos") || lower.contains("itp")) {
            iface = InterfaceType.RPI;
        } else {
            iface = InterfaceType.MPI;
        }

        MIURA_MANAGER.setConnector(connector);
        MIURA_MANAGER.openSession();
        MIURA_MANAGER.executeAsync(client -> enableEventsOnDevice(client, iface));

        LOGGER.trace("connectBt: connected ok!");
    }

    @Override
    public void disconnectBt() {
        LOGGER.trace("disconnectBt()");

        deregisterEventHandlers();
        MIURA_MANAGER.closeSession();

        // fake connection event, as we de-register handler before disconnect.
        // This way any disconnected event can be considered unexpected
        printEvent("Disconnected from " + mCurrentDeviceName);
    }

    private JavaBluetoothConnector getConnectorForDevice(String deviceName) throws IOException {

        JavaBluetoothConnector cached = checkCache(deviceName);
        if (cached != null) {
            return cached;
        }

        JavaBluetoothConnector connector = makeNewBluetoothConnector(deviceName);
        addToCache(deviceName, connector);
        return connector;
    }

    private void addToCache(String deviceName, JavaBluetoothConnector connector) {
        mCurrentDeviceName = deviceName;
        mCurrentConnector = connector;
    }

    @Nullable
    private JavaBluetoothConnector checkCache(String deviceName) throws IOException {
        // todo cache for different devices. currently we just cache one.
        if (!deviceName.equals(mCurrentDeviceName)) {
            return null;
        }

        if (mCurrentConnector == null) {
            throw new IOException("mCurrentDeviceName set but mCurrentConnector null??");
        }
        return mCurrentConnector;
    }

    private void printEvent(Object eventObj) {
        if (mPresenter == null) {
            LOGGER.info("Device event with no presenter? {}", eventObj);
            return;
        }
        mPresenter.onPrintEvent(eventObj.toString());
    }

    private void handleUnexpectedDisconnect(ConnectionInfo arg) {
        if (mPresenter == null) {
            LOGGER.info("Device event with no presenter? {}", arg);
            return;
        }

        mPresenter.onPrintEvent(arg.toString());
        mPresenter.onUnexpectedDisconnection();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void registerEventHandlers() {
        // any Disconnected event is always unexpected
        MPI_EVENTS.Disconnected.register(mHandleDisconnect);

        MPI_EVENTS.Connected.register(mMpiEventPrinter);
        MPI_EVENTS.CardStatusChanged.register(mMpiEventPrinter);
        MPI_EVENTS.KeyPressed.register(mMpiEventPrinter);
        MPI_EVENTS.DeviceStatusChanged.register(mMpiEventPrinter);
        MPI_EVENTS.PrinterStatusChanged.register(mMpiEventPrinter);
        MPI_EVENTS.CommsChannelStatusChanged.register(mMpiEventPrinter);
        MPI_EVENTS.BarcodeScanned.register(mMpiEventPrinter);
        MPI_EVENTS.UsbSerialPortDataReceived.register(mMpiEventPrinter);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void deregisterEventHandlers() {
        MPI_EVENTS.Disconnected.deregister(mHandleDisconnect);

        MPI_EVENTS.Connected.deregister(mMpiEventPrinter);
        MPI_EVENTS.CardStatusChanged.deregister(mMpiEventPrinter);
        MPI_EVENTS.KeyPressed.deregister(mMpiEventPrinter);
        MPI_EVENTS.DeviceStatusChanged.deregister(mMpiEventPrinter);
        MPI_EVENTS.PrinterStatusChanged.deregister(mMpiEventPrinter);
        MPI_EVENTS.CommsChannelStatusChanged.deregister(mMpiEventPrinter);
        MPI_EVENTS.BarcodeScanned.deregister(mMpiEventPrinter);
        MPI_EVENTS.UsbSerialPortDataReceived.deregister(mMpiEventPrinter);
    }

    private static void enableEventsOnDevice(MpiClient client, InterfaceType interfaceType) {

        if (interfaceType == InterfaceType.MPI) {
            client.keyboardStatus(
                    InterfaceType.MPI,
                    StatusSettings.Enable,
                    BacklightSettings.Enable
            );
            client.cardStatus(InterfaceType.MPI, true, false, true, true, true);
            client.printerSledStatus(InterfaceType.MPI, true);
        }

        if (interfaceType == InterfaceType.RPI) {
            client.barcodeStatus(InterfaceType.RPI, true);
        }
    }

    @NonNull
    private static JavaBluetoothConnector makeNewBluetoothConnector(String deviceName)
            throws IOException {
        RemoteDevice device = JavaBluetoothConnector.findDeviceForName(deviceName);
        if (device == null) {
            throw new IOException("Unknown device name: " + deviceName);
        }

        String url = JavaBluetoothConnector.getUrlForDevice(device);
        return new JavaBluetoothConnector(url);
    }
}
