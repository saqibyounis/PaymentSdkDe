package com.miurasample.ui.usb.host;


import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.support.annotation.NonNull;
import android.util.Log;

import com.miurasystems.miuralibrary.tlv.BinaryUtil;

import java.io.IOException;
import java.io.InputStream;

public class AcmBulkInputStream extends InputStream {

    private static final String TAG = AcmBulkInputStream.class.getSimpleName();

    @NonNull
    private final UsbEndpoint mEndpoint;

    private final int mReadSize;

    @NonNull
    private final byte[] mBuffer;

    @NonNull
    private final UsbDeviceConnection mConnection;

    /** inclusive, i.e. the next byte read is mBuffer[mBufferStart] */
    private int mBufferStart;

    /** exclusive, i.e. the last byte read is mBuffer[mBufferEnd - 1] */
    private int mBufferEnd;

    private boolean mIsClosed;

    public AcmBulkInputStream(
        @NonNull UsbDeviceConnection connection,
        @NonNull UsbEndpoint endpoint
    ) {
        if (endpoint.getDirection() != UsbConstants.USB_DIR_IN) {
            throw new AssertionError("Can't create input stream, not USB_DIR_IN");
        }

        mConnection = connection;
        mEndpoint = endpoint;
        mReadSize = mEndpoint.getMaxPacketSize();
        mBuffer = new byte[mReadSize];
        mBufferStart = 0;
        mBufferEnd = 0;
        mIsClosed = false;
    }

    void drain(int timeout) {
        if (mIsClosed) throw new AssertionError("Can't drain a closed stream?!");
        byte[] b = new byte[mReadSize];
        int numRead;
        do {
            numRead = mConnection.bulkTransfer(mEndpoint, b, mReadSize, timeout);
            Log.v(TAG, String.format("Drained: %d bytes", numRead));
        } while (numRead > 0);
    }

    private void ensureBufferIsValid() throws IOException {
        if (mBufferStart >= mBufferEnd) {
            if (mBufferStart != mBufferEnd) {
                throw new AssertionError("Buffer overrun?");
            }
            Log.d(TAG, "Filling buffer...");
            mBufferStart = 0;
            mBufferEnd = mConnection.bulkTransfer(mEndpoint, mBuffer, mReadSize, 0);
            Log.d(TAG, "Buffer filled with " + mBufferEnd + " bytes!");

            if (mBufferEnd < 0) {
                // it looks like -1 on timeout AND error...
                // We only ever use a 0 timeout, so it must be error?
                String msg = "USB Read error: mConnection.bulkTransfer returned " + mBufferEnd;
                throw new IOException(msg);
            }
        }
    }

    private boolean isEof() {
        return mBufferEnd < 0;
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        int count = read(b, 0, 1);
        if (count == -1) {
            return -1;
        } else {
            return BinaryUtil.ubyteToInt(b[0]);
        }
    }

    @Override
    public int read(@NonNull byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(@NonNull byte[] b, int off, int len) throws IOException {
        if (mIsClosed) throw new IOException("Stream is closed");

        ensureBufferIsValid();
        if (isEof()) return -1;
        int bufferSize = available();
        int readSize = Math.min(len, bufferSize);

        System.arraycopy(mBuffer, mBufferStart, b, off, readSize);

        mBufferStart += readSize;
        return readSize;
    }

    @Override
    public long skip(long n) throws IOException {
        if (mIsClosed) throw new IOException("Stream is closed");
        return super.skip(n);
    }

    @Override
    public int available() throws IOException {
        if (mIsClosed) throw new IOException("Stream is closed");
        if (isEof()) return 0;

        int available = mBufferEnd - mBufferStart;
        if (available < 0) throw new AssertionError("Negative available bytes?");
        return available;
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

        // Try and prevent out-of-sync issues by dumping anything left.
        drain(50);

        mIsClosed = true;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

}
