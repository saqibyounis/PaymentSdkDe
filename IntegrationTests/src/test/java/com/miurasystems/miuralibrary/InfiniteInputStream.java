/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
public class InfiniteInputStream extends InputStream {

    private final AtomicInteger mPos;
    private final AtomicInteger mMark;
    private final AtomicBoolean mIsClosed;
    private final int mPacketSize;

    public InfiniteInputStream(int packetSize) {
        if (packetSize <= 0) {
            throw new IllegalArgumentException("packetSize <= 0");
        }
        mPos = new AtomicInteger(0);
        mMark = new AtomicInteger(0);
        mIsClosed = new AtomicBoolean(false);
        mPacketSize = packetSize;
    }

    public InfiniteInputStream() {
        this(2048);
    }

    @Override
    public int read() throws IOException {
        if (mIsClosed.get()) {
            throw new IOException("Stream is closed");
        }
        int value = mPos.getAndIncrement();
        return value & 0xFF;
    }

    @Override
    public int read(@NonNull byte[] buffer, int offset, int len) throws IOException {
        if ((offset < 0) || (len < 0) || (len > (buffer.length - offset))) {
            throw new IndexOutOfBoundsException("Bad parameters: " + offset + "," + len);
        } else if (len == 0) {
            return 0;
        }

        int startValue = mPos.get();
        int size = Math.min(mPacketSize, len);

        int i;
        for (i = 0; i < size; i++) {
            if (mIsClosed.get()) {
                break;
            }
            buffer[offset + i] = (byte) ((startValue + i) & 0xFF);
        }
        int numWritten = i;

        // If multiple threads read the stream, with the same read size, they'll end up with the
        // same data, but we'll account for them both. That's the callers fault.
        // mPos is always an accurate reflection of the number of bytes sent out.
        for (; ; ) {
            int current = mPos.get();
            int next = current + numWritten;
            if (mPos.compareAndSet(current, next)) {
                return numWritten;
            }
        }

    }

    @Override
    public long skip(long n) throws IOException {
        int skip = (int) n;
        for (; ; ) {
            if (mIsClosed.get()) {
                throw new IOException("Stream is closed");
            }
            int current = mPos.get();
            int next = current + skip;
            if (mPos.compareAndSet(current, next)) {
                return n;
            }
        }
    }

    @Override
    public int available() throws IOException {
        if (mIsClosed.get()) {
            throw new IOException("Stream is closed");
        }
        // We could return Integer.MAX_VALUE or something, but some daft
        // implementation might take the return value of `available` and do stupid things with#
        // it, e.g. allocate that much.
        return mPacketSize;
    }

    @Override
    public void close() throws IOException {
        mIsClosed.set(true);
    }

    @Override
    public void mark(int readLimit) {
        if (mIsClosed.get()) {
            return;
        }
        mMark.set(mPos.get());
    }

    @Override
    public void reset() throws IOException {
        if (mIsClosed.get()) {
            throw new IOException("Stream is closed");
        }
        mPos.set(mMark.get());
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}

