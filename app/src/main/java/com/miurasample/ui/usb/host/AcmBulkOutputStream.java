package com.miurasample.ui.usb.host;


import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.tlv.BinaryUtil;

import java.io.IOException;
import java.io.OutputStream;

class AcmBulkOutputStream extends OutputStream {
    @NonNull
    private final UsbDeviceConnection mConnection;

    @NonNull
    private final UsbEndpoint mEndpoint;

    private final int mWriteSize;

    /* Not required to be a field, but saves reallocating it all the time */
    @NonNull
    private final byte[] mWriteBuffer;

    private boolean mIsClosed;

    public AcmBulkOutputStream(
        @NonNull UsbDeviceConnection connection,
        @NonNull UsbEndpoint endpoint
    ) {
        if (endpoint.getDirection() != UsbConstants.USB_DIR_OUT) {
            throw new AssertionError("Can't create input stream, not USB_DIR_OUT");
        }

        mConnection = connection;
        mEndpoint = endpoint;
        mWriteSize = endpoint.getMaxPacketSize();
        mWriteBuffer = new byte[mWriteSize];
        mIsClosed = false;
    }

    @Override
    public void write(int i) throws IOException {
        // Not the best implementation, but the SDK doesn't do single byte writes, so this won't
        // be called?
        byte b = BinaryUtil.intToUbyte(i);
        byte[] buf = {b};
        write(buf, 0, 1);
    }

    @Override
    public void write(@NonNull byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(
        @NonNull byte[] inputBuffer, int inputOffset, int inputLength
    ) throws IOException {

        if (mIsClosed) throw new IOException("Stream is closed");

        if ((inputOffset < 0)
            || (inputOffset > inputBuffer.length)
            || (inputLength < 0)
            || ((inputOffset + inputLength) > inputBuffer.length)
            || ((inputOffset + inputLength) < 0)) {
            throw new IndexOutOfBoundsException("bad parameters");
        } else if (inputLength == 0) {
            return;
        }

        while (inputLength > 0) {

            int numBytesToWrite = Math.min(inputLength, mWriteSize);
            System.arraycopy(inputBuffer, inputOffset, mWriteBuffer, 0, numBytesToWrite);

            int numWritten = mConnection.bulkTransfer(
                mEndpoint, mWriteBuffer, numBytesToWrite, 0
            );
            if (numWritten <= 0) {
                String msg = "USB Write error: mConnection.bulkTransfer returned " + numWritten;
                throw new IOException(msg);
            }

            inputLength -= numWritten;
            inputOffset += numWritten;
        }
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
        /* Simple close method that makes the stream unusable once closed.
           The details of USb stream closing are handled by
           UsbDeviceSerialConnector.disconnect()
        */
        if (mIsClosed) {
            return;
        }
        mIsClosed = true;
    }
}
