/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

enum MockOutputStreamCommands {
    THROW_ON_NTH_BYTE(true),
    THROW_ON_CLOSE,
    THROW_ON_FLUSH;

    public final boolean hasValue;

    MockOutputStreamCommands() {
        this(false);
    }

    MockOutputStreamCommands(boolean hasValue) {
        this.hasValue = hasValue;
    }
}


class MockOutputStreamCommand {
    @NonNull
    public final MockOutputStreamCommands mCmd;
    public final int mValue;

    MockOutputStreamCommand(@NonNull MockOutputStreamCommands cmd) {
        if (cmd.hasValue) {
            String msg = String.format("Command %s requires a value!", cmd.toString());
            throw new AssertionError(msg);
        }
        mCmd = cmd;
        mValue = -1;
    }

    MockOutputStreamCommand(@NonNull MockOutputStreamCommands cmd, int value) {
        mCmd = cmd;
        mValue = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MockOutputStreamCommand command = (MockOutputStreamCommand) o;

        return mValue == command.mValue && mCmd == command.mCmd;
    }

    @Override
    public int hashCode() {
        int result = mCmd.hashCode();
        result = 31 * result + mValue;
        return result;
    }
}


/**
 * This is basically a copy + pasted ByteArrayOutputStream, but changed to ints
 * and to have write() perform actions on demand (e.g. throw exceptions).
 */
@SuppressWarnings("OverloadedVarargsMethod")
public class MockOutputStream extends OutputStream {

    // 512 = enough to fit an MpiPacket in.
    private static final int DEFAULT_SIZE = 512;

    private final boolean mThrowOnClose;
    private final boolean mThrowOnFlush;
    // throw = -1, written = [0, 1, 2, 3, 4, ...]
    // throw =  0, written = [] throw as they try to write byte 0
    // throw =  1, written = [0], throw as they try to write byte 1
    // throw =  2, written = [0, 1], throw as they try to write byte 2
    // throw =  5, written = [0, 1, 2, 3, 4], throw as they try to write byte 5
    private final int mThrowOnNthByte;

    private byte[] mBuf;
    private int mCount;
    private boolean mClosed;

    public MockOutputStream() {
        this(DEFAULT_SIZE);
    }

    public MockOutputStream(int size) {
        this(size, new MockOutputStreamCommand[0]);
    }

    public MockOutputStream(MockOutputStreamCommand... commands) {
        this(DEFAULT_SIZE, commands);
    }

    public MockOutputStream(int size, MockOutputStreamCommand... commands) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: " + size);
        }
        mBuf = new byte[size];
        mClosed = false;

        boolean throwOnClose = false;
        boolean throwOnFlush = false;
        int throwOnNthByte = -1;

        for (MockOutputStreamCommand cmd : commands) {
            switch (cmd.mCmd) {
                case THROW_ON_CLOSE:
                    throwOnClose = true;
                    break;
                case THROW_ON_FLUSH:
                    throwOnFlush = true;
                    break;
                case THROW_ON_NTH_BYTE:
                    throwOnNthByte = cmd.mValue;
                    if (throwOnNthByte < 0) {
                        throw new IllegalArgumentException("THROW_ON_NTH_BYTE < 0");
                    }
                    break;
            }
        }

        this.mThrowOnClose = throwOnClose;
        this.mThrowOnFlush = throwOnFlush;
        this.mThrowOnNthByte = throwOnNthByte;

    }

    private void ensureCapacity(int minCapacity) {
        // overflow-conscious code
        if (minCapacity - mBuf.length > 0) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = mBuf.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        if (newCapacity < 0) {
            if (minCapacity < 0) // overflow
            {
                throw new OutOfMemoryError();
            }
            newCapacity = Integer.MAX_VALUE;
        }
        mBuf = Arrays.copyOf(mBuf, newCapacity);
    }

    @Override
    public void write(int b) throws IOException {
        if (mClosed) {
            throw new IOException("Stream is closed");
        }

        if (mThrowOnNthByte == mCount) {
            String message = String.format("Throwing on byte number %d!", mCount);
            throw new IOException(message);
        }

        ensureCapacity(mCount + 1);
        mBuf[mCount] = (byte) b;
        mCount += 1;
    }

    @Override
    public void write(@NonNull byte[] b, int off, int len) throws IOException {
        if ((off < 0) || (off > b.length) || (len < 0) ||
                ((off + len) - b.length > 0)) {
            throw new IndexOutOfBoundsException();
        }
        ensureCapacity(mCount + len);
        for (int i = 0; i < len; i++) {
            write(b[off + i]);
        }
    }

    void reopen() {
        mCount = 0;
        mClosed = false;
    }

    byte[] toByteArray() {
        return Arrays.copyOf(mBuf, mCount);
    }

    int size() {
        return mCount;
    }

    @Override
    public void flush() throws IOException {
        if (mThrowOnFlush) {
            throw new IOException("Throwing on flush()!");
        }
    }

    @Override
    public void close() throws IOException {
        if (mThrowOnClose) {
            throw new IOException("Throwing on close()!");
        }
        mClosed = true;
    }
}
