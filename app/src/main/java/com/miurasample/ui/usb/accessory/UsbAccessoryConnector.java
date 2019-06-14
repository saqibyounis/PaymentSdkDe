/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.usb.accessory;

import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasample.ui.usb.UserTextPrinter;
import com.miurasystems.miuralibrary.CommandType;
import com.miurasystems.miuralibrary.comms.CommandApdu;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;
import com.miurasystems.miuralibrary.enums.InterfaceType;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implements a Connector for Android's UsbAccessory.
 *
 * <p>
 * Note that this worksaround various Android bugs, notably via the use of
 * {@link FixedReadSizeInputStream} and by closing the InputStream before sending
 * SERIAL_DISCONNECT. (See Implementation of {@link #disconnect(MpiProtocolSession) for more}
 * </p>
 */
public class UsbAccessoryConnector extends Connector {

    private static final String TAG = UsbAccessoryConnector.class.getSimpleName();
    private final UsbAccessory mAccessory;
    private final UsbManager mUsbManager;
    private final UserTextPrinter mLogger;

    @Nullable
    private ParcelFileDescriptor mFileDescriptor;

    @Nullable
    private InputStream mInputStream;

    @Nullable
    private OutputStream mOutputStream;

    public UsbAccessoryConnector(
            UsbAccessory accessory,
            UsbManager usbManager,
            UserTextPrinter logger
    ) {
        mAccessory = accessory;
        mUsbManager = usbManager;
        mLogger = logger;
    }

    boolean isCompatible(UsbManager manager, UsbAccessory accessory) {
        if (!mUsbManager.equals(manager)) {
            return false;
        } else if (!mAccessory.equals(accessory)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isConnected() {
        return mFileDescriptor != null;
    }

    @Override
    protected void connect() throws IOException {

        mFileDescriptor = mUsbManager.openAccessory(mAccessory);
        if (mFileDescriptor == null) {
            log(TAG, "mAccessory open fail");
            throw new IOException("mAccessory open failed");
        }

        log(TAG, "Connected to " + mAccessory.getSerial());
        FileDescriptor fd = mFileDescriptor.getFileDescriptor();
        mInputStream = new FixedReadSizeInputStream(new FileInputStream(fd), 256);
        mOutputStream = new FileOutputStream(fd);
    }

    @Override
    protected void disconnect(@NonNull MpiProtocolSession closingSession) throws IOException {
        IOException firstException = null;

        if (mInputStream != null) {
            try {
                mInputStream.close();
            } catch (IOException e) {
                log(TAG, e.toString());
                firstException = e;
            }
        }

        if (mOutputStream != null) {
            /*
                We need to send SERIAL_DISCONNECT, which causes the POSzle to
                drop its end of the connection, AND close our end of the connection.
                If we don't do both then Android won't let us reopen the accessory without a
                physical disconnect occurring.
                Looks like this is a WONTFIX Android bug:
                    https://issuetracker.google.com/issues/36933798
                    https://issuetracker.google.com/issues/36978772
                    https://issuetracker.google.com/issues/36981924

                    Whilst a fix is apparently merged in Android kernel 3-10 the
                    device I'm using is Android 3.18.19 and still exhibits the bug?

                We "close" the inputstream first before sending out the reply, as
                    a) The poller/ResponseReader is currently blocked in read()
                    b) The mInputStream.close() above won't cause that thread to unblock
                        (nor will interrupting) due to Android bugs
                    c) but it will mean that when it receives and dispatches the reply to
                        SERIAL_DISCONNECT, it'll hit an IOException when trying to read() for
                        the "next" packet. (And there will never be a next packet,
                        so we want to avoid it getting into the blocking part of read())
             */

            CommandApdu apdu = new CommandApdu(CommandType.USB_Serial_Disconnect);
            try {
                closingSession.sendCommandAPDU(InterfaceType.RPI, apdu);
            } catch (IOException e) {
                /* This is expected if e.g. the cable was yanked out */
                log(TAG, "Failed to send SERIAL_DISCONNECT");
                if (firstException == null) firstException = e;
            }

            try {
                mOutputStream.close();
            } catch (IOException e) {
                log(TAG, e.toString());
                if (firstException == null) firstException = e;
            }
        }
        if (mFileDescriptor != null) {
            try {
                mFileDescriptor.close();
            } catch (IOException e) {
                log(TAG, e.toString());
                if (firstException == null) firstException = e;
            }
        }

        mInputStream = null;
        mOutputStream = null;
        mFileDescriptor = null;

        if (firstException == null) {
            log(TAG, "Disconnected from " + mAccessory.getDescription());
        } else {
            throw firstException;
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
