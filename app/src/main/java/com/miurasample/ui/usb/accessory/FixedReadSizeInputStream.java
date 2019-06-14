/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.usb.accessory;


import android.support.annotation.NonNull;
import android.util.Log;

import com.miurasystems.miuralibrary.tlv.BinaryUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Ensures that FileInputStream.read() is only ever called with 512 bytes.
 *
 * <p>
 * This is a workaround for Android Kernel bug https://issuetracker.google.com/issues/37043228
 * </p>
 * <p>
 * This is very similiar to BufferedInputStream, except BufferedInputStream doesn't work
 * as it always fails with EINVAL when used with a FileInputStream.
 * </p>
 */
@SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
public class FixedReadSizeInputStream extends InputStream {

    private static final int DEFAULT_READ_SIZE = 512;

    private final FileInputStream mFileInputStream;

    private final int mReadSize;
    private final byte[] mBuffer;
    /** inclusive, i.e. the next byte read is mBuffer[mBufferStart] */
    private int mBufferStart;

    /** exclusive, i.e. the last byte read is mBuffer[mBufferEnd - 1] */
    private int mBufferEnd;

    private boolean mIsClosed;

    public FixedReadSizeInputStream(FileInputStream fileInputStream) {
        this(fileInputStream, DEFAULT_READ_SIZE);
    }

    public FixedReadSizeInputStream(FileInputStream fileInputStream, int readSize) {
        mFileInputStream = fileInputStream;
        mReadSize = readSize;
        mBuffer = new byte[mReadSize];
    }

    private void ensureBufferIsValid() throws IOException {
        if (mBufferEnd < -1) {
            return;
        }

        if (mBufferStart >= mBufferEnd) {
            if (mBufferStart != mBufferEnd) {
                throw new AssertionError("Buffer overrun?");
            }
            Log.d("FixedReadSizeInputStrea", "Filling buffer...");
            mBufferStart = 0;
            mBufferEnd = mFileInputStream.read(mBuffer, 0, mReadSize);
            Log.d("FixedReadSizeInputStrea", "Buffer filled with " + mBufferEnd + " bytes!");
        }
    }

    private boolean isEof() {
        return mBufferEnd < 0;
    }

    @Override
    public int read() throws IOException {
        if (mIsClosed) throw new IOException("Stream is closed");

        ensureBufferIsValid();
        if (isEof()) return -1;

        byte result = mBuffer[mBufferStart];
        mBufferStart += 1;

        return BinaryUtil.ubyteToInt(result);
    }

    @Override
    public int read(@NonNull byte[] output) throws IOException {
        return read(output, 0, output.length);
    }

    @Override
    public int read(@NonNull byte[] output, int outputPosition, int outputCount)
            throws IOException {
        if (mIsClosed) throw new IOException("Stream is closed");

        ensureBufferIsValid();
        if (isEof()) return -1;

        int bufferSize = available();
        int readSize = Math.min(outputCount, bufferSize);

        System.arraycopy(mBuffer, mBufferStart, output, outputPosition, readSize);

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
        if (mIsClosed) {
            return;
        }
        mIsClosed = true;
        mFileInputStream.close();
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}
