/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.module.bluetooth;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

// this class requires:
// bluetooth to already be supported
// bluetooth to already be enabled
// the device to already be "bonded"
// to be given a device (we have no idea which one the app wants)
// or even a mac address, or even a bluetooth socket.

public final class AndroidBluetoothClientConnector extends Connector {

    // UUID for standard Bluetooth SPP (Serial Port Profile)
    private static final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    @NonNull
    private final BluetoothDevice mDevice;
    @Nullable
    private BluetoothSocket mSocket;

    // must support BT on device, i.e. BluetoothAdapter.getDefaultAdapter() is not-null.
    public AndroidBluetoothClientConnector(@NonNull BluetoothDevice device) {
        if (BluetoothAdapter.getDefaultAdapter() == null) {
            throw new IllegalArgumentException("Bluetooth not supported on this device?");
        }

        mDevice = device;
    }

    @Override
    public boolean isConnected() {
        return mSocket != null && mSocket.isConnected();
    }

    @Override
    protected void connect() throws IOException {
        if (isConnected()) {
            return;
        }
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

        // ParcelUuid[] list = ped.getUuids();
        // System.out.println("parcelUuid list.length = " + list.length);
        // for (ParcelUuid parcelUuid : list) {
        //     System.out.printf("parcelUuid %s,  getUuid=%s, hashcode=%s %n",
        //             parcelUuid, parcelUuid.getUuid(), parcelUuid.hashCode());
        // }

        // try {
        //     Method m = ped.getClass().getMethod("createRfcommSocket", int.class);
        //     mSocket = (BluetoothSocket) m.invoke(ped, 1);
        // } catch (SecurityException | InvocationTargetException | IllegalArgumentException |
        //         IllegalAccessException | NoSuchMethodException e) {
        //     throw new AssertionError(e);
        // }

        mSocket = mDevice.createRfcommSocketToServiceRecord(SERIAL_UUID);
        mSocket.connect();
    }

    @Override
    protected void disconnect(@NonNull MpiProtocolSession closingSession) throws IOException {
        if (!isConnected()) {
            return;
        }

        assert mSocket != null;
        mSocket.close();
        mSocket = null;
    }

    @NonNull
    @Override
    protected InputStream getInputStream() throws IOException {
        if (mSocket == null) {
            throw new IOException("BluetoothSocket is closed");
        }
        return mSocket.getInputStream();
    }

    @NonNull
    @Override
    protected OutputStream getOutputStream() throws IOException {
        if (mSocket == null) {
            throw new IOException("BluetoothSocket is closed");
        }
        return mSocket.getOutputStream();
    }

}
