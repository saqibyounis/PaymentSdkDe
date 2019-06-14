/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.usb.host;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasample.ui.usb.UserTextPrinter;
import com.miurasystems.miuralibrary.CommandType;
import com.miurasystems.miuralibrary.comms.CommandApdu;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;
import com.miurasystems.miuralibrary.enums.InterfaceType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implements a Connector for a UsbSerial using Android's USB Host capabilities.
 */
public class UsbDeviceSerialConnector extends Connector {

    private static final String TAG = UsbDeviceSerialConnector.class.getSimpleName();

    @NonNull
    private final UsbDevice mDevice;

    @NonNull
    private final UsbManager mUsbManager;

    @NonNull
    private final UserTextPrinter mLogger;

    @Nullable
    private UsbDeviceConnection mUsbDeviceConnection;

    @Nullable
    private AcmBulkInputStream mInputStream;

    @Nullable
    private AcmBulkOutputStream mOutputStream;

    private UsbInterface mUsbBulkInterface;

    public UsbDeviceSerialConnector(
        @NonNull UsbDevice device,
        @NonNull UsbManager usbManager,
        @NonNull UserTextPrinter logger
    ) {
        mDevice = device;
        mUsbManager = usbManager;
        mLogger = logger;
    }

    boolean isCompatible(UsbManager manager, UsbDevice device) {
        if (!mUsbManager.equals(manager)) {
            return false;
        } else if (!mDevice.equals(device)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isConnected() {
        return mUsbDeviceConnection != null;
    }

    @Override
    protected void connect() throws IOException {

        mUsbDeviceConnection = mUsbManager.openDevice(mDevice);
        if (mUsbDeviceConnection == null) {
            log(TAG, "mDevice open fail");
            throw new IOException("mDevice open failed");
        }

        log(TAG, "Connected to " + mDevice.getDeviceName());

        // todo hard coded interface and endpoint values.
        mUsbBulkInterface = mDevice.getInterface(1);
        boolean claimed = mUsbDeviceConnection.claimInterface(mUsbBulkInterface, false);
        if (!claimed) {
            log(TAG, "Interface not claimed!");
            mUsbDeviceConnection.releaseInterface(mUsbBulkInterface);
            mUsbDeviceConnection.close();
            mUsbDeviceConnection = null;
            mUsbBulkInterface = null;
            throw new IOException("Interface not claimed!");
        }

        UsbEndpoint bulk_endpoint_in = mUsbBulkInterface.getEndpoint(0);
        UsbEndpoint bulk_endpoint_out = mUsbBulkInterface.getEndpoint(1);
        mInputStream = new AcmBulkInputStream(mUsbDeviceConnection, bulk_endpoint_in);
        mOutputStream = new AcmBulkOutputStream(mUsbDeviceConnection, bulk_endpoint_out);

        /* Usb serial cables can often get out of sync and have previous sessions "stuff" in there,
           which will get this session out of sync. So drain it all
         */
        mInputStream.drain(30);
    }

    @Override
    protected void disconnect(@NonNull MpiProtocolSession closingSession) throws IOException {

        log(TAG, "UsbDeviceSerialConnector.disconnect()");
        if (mUsbDeviceConnection == null) {
            log(TAG, "already disconnected!");
            return;
        }

        IOException delayedException = null;

        log(TAG, "Sending SERIAL_DISCONNECT");
        CommandApdu apdu = new CommandApdu(CommandType.USB_Serial_Disconnect);
        try {
            closingSession.sendCommandAPDU(InterfaceType.MPI, apdu);
            closingSession.receiveResponseTimeout(InterfaceType.MPI, 2000L);
        } catch (IOException e) {
            /* This is expected if e.g. the cable was yanked out */
            log(TAG, "Failed to send SERIAL_DISCONNECT");
            delayedException = e;
        } catch (InterruptedException e) {
            log(TAG, "InterruptedException whilst sending SERIAL_DISCONNECT");
            delayedException = new IOException(e);
        }

        boolean ok = mUsbDeviceConnection.releaseInterface(mUsbBulkInterface);
        if (!ok) {
            log(TAG, "Failed to releaseInterface");
            delayedException = new IOException("failed to release mUsbBulkInterface");
        }
        mUsbDeviceConnection.close();

        if (mInputStream != null) {
            mInputStream.close();
        }
        if (mOutputStream != null) {
            mOutputStream.close();
        }

        mUsbDeviceConnection = null;
        mInputStream = null;
        mOutputStream = null;

        if (delayedException == null) {
            log(TAG, "Disconnected ok from " + mDevice.getDeviceName());
        } else {
            throw delayedException;
        }
    }

    @NonNull
    @Override
    protected InputStream getInputStream() throws IOException {
        if (mInputStream != null) {
            return mInputStream;
        } else {
            throw new IOException("InputStream Closed");
        }
    }

    @NonNull
    @Override
    protected OutputStream getOutputStream() throws IOException {
        if (mOutputStream != null) {
            return mOutputStream;
        } else {
            throw new IOException("OutputStream Closed");
        }
    }

    private void log(String tag, String text) {
        mLogger.log(tag, text);
    }
}
