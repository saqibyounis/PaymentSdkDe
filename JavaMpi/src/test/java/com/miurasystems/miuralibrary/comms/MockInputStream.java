/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import static java.lang.Math.max;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe byte input stream class that is intended to be used in tests,
 * as it allows 'actions' to be performed mid-stream.
 */
public final class MockInputStream extends InputStream {

    /**
     * Return EOF/-1 to reader.
     * Latches, so further bytes in the Mock stream will not be processed.
     * Any subsequent read() operations will always return EOF
     */
    public final static int EOF = -1;

    /**
     * Throw IOException to reader.
     * Latches, so further bytes in the Mock stream will not be processed.
     * Any subsequent read() operations will always throw an IOException
     */
    public final static int THROW_IO_EXCEPTION = -2;

    /**
     * Interrupt the stream of bytes in {@link #read(byte[], int, int)} calls.
     *
     * This value is ignored if the stream reader is using {@link #read()}, as that
     * is defined to return either EOF or a 0-255 int. So trying to 'cut' the stream there
     * would be out-of-contract and just confusing.
     */
    public final static int CUT_STREAM = -3;

    /**
     * Throw Assertion error -- aka fail the test
     */
    public final static int ASSERT = -4;

    /**
     * Causes the reading thread to block until {@link #signalPausedThreads()} is called,
     * which can be dona via the following public methods:
     * <ol>
     * <li> {@link #waitForPauseAndSetData(int[])} </li>
     * <li> {@link #waitAndSignalPausedThreads()} </li>
     * </ol>
     */
    public final static int PAUSE = -5;

    /**
     * Causes the reading thread to block until this stream is closed!
     *
     * <p>Latches, so further bytes in the Mock stream will not be processed.
     * Any subsequent read() operations will always throw an IOException
     */
    public final static int BLOCK = -6;

    /**
     * The main lock used to mutex access to the data buffer.
     * Used instead of {@code synchronized} (which is used on the super InputStream) as:
     * <ul>
     * <li>synchronized(this) is dangerous</li>
     * <li>Avoids excess indentation</li>
     * <li>And mainly: The ability to use multiple Condition objects and wait on them.
     * (The alternative is to use other Object()s and wait on them -- but you need to
     * synchronized on them which is more logic that's prone to errors.
     * </li>
     * </ul>
     */
    private final ReentrantLock bufLock = new ReentrantLock();

    /**
     * Condition used by any thread that is currently 'paused'.
     *
     * <p>See also: {@link #pauseThisThread()}, {@link #PAUSE},
     * {@link #isPaused}, {@link #signalPausedThreads()}
     */
    private final Condition waitingToUnpause = bufLock.newCondition();

    /**
     * Condition used threads that want to wait until at least one other thread
     * has paused itself.
     *
     * <p>See also: {@link #waitForPause(long, TimeUnit)} ()}, {@link #PAUSE},
     * {@link #isPaused}}
     */
    private final Condition somethingIsPaused = bufLock.newCondition();

    /**
     * Condition used by threads that are 'blocked until close'.
     *
     * <p>See also: {@link #blockUntilClose()}, {@link #BLOCK}
     */
    private final Condition blocked = bufLock.newCondition();

    /**
     * Condition used by threads that become "unblocked".
     *
     * Threads that are waiting for all threads to unblock wait on this and check
     * {@link #mNumBlocked}. Threads that are blocked signal this after they have unblocked and
     * decremented mNumBlocked.
     *
     * <p>See also: {@link #reopen()}, {@link #BLOCK}
     */
    private final Condition somethingUnblocked = bufLock.newCondition();

    /**
     * If true, at least one Thread is currently 'paused' in the read() method.
     * If false, no Threads are paused in the  read() method.
     *
     * <p>See also: {@link #PAUSE}, {@link #waitingToUnpause}
     */
    private final AtomicBoolean isPaused = new AtomicBoolean(false);

    /**
     * If set, the MockInputStream will throw an exception when {@link #close()} is called,
     * as the contract of InputStream allows.
     */
    private volatile boolean throwOnClose = false;

    /*
        All of these fields should only be accessed when bufLock is held.
     */
    @Nullable
    private int[] mBuf;
    private int mPos;
    private int mMark = 0;
    private int mCount;

    // Volatile to avoid while-loop hoisting.
    volatile private boolean mIsClosed = false;

    /**
     * Number of threads currently blocked.
     *
     * Value is the number of 'blocked' Threads in the read() method.
     * If 0, no Threads are blocked in the read() method, though one could soon be.
     *
     * Volatile to avoid while-loop hoisting.
     *
     * <p>See also: {@link #BLOCK}, {@link #blocked}
     */
    volatile private int mNumBlocked = 0;

    public MockInputStream() {
        this.setData(null);
    }

    public MockInputStream(@NonNull int[] buf) {
        this(buf, 0, buf.length);
    }

    public MockInputStream(@NonNull int[] buf, int offset, int length) {
        this.setData(buf, offset, length);
    }

    public void setData(@Nullable int[] bytes) {
        if (bytes == null) {
            bufLock.lock();
            try {
                log("setData(null): got lock");
                this.mBuf = null;
                this.mPos = 0;
                this.mCount = 0;
                this.mMark = 0;
                //signalPausedThreads();
            } finally {
                bufLock.unlock();
            }
            return;
        }
        setData(bytes, 0, bytes.length);
    }

    public void setData(@NonNull int[] buf, int offset, int length) {
        bufLock.lock();
        try {
            log("setData(buf, offset, len): got lock");
            this.mBuf = buf;
            this.mPos = offset;
            this.mCount = Math.min(offset + length, buf.length);
            this.mMark = offset;
            //signalPausedThreads();
        } finally {
            bufLock.unlock();
        }
    }

    @Nullable
    public int[] getRemainingData() {
        bufLock.lock();
        try {
            if (mPos >= mCount) {
                return new int[]{EOF};
            }

            assert mBuf != null;
            return Arrays.copyOfRange(mBuf, mPos, mCount);
        } finally {
            bufLock.unlock();
        }
    }

    int peek() throws IOException {
        bufLock.lock();
        try {
            log("peek(): got lock");
            if (mIsClosed) {
                throw new IOException("Stream closed!");
            }
            if (mPos >= mCount) {
                return EOF;
            }

            assert mBuf != null;
            int value = mBuf[mPos];
            log("peek(): value " + value);
            return value;
        } finally {
            bufLock.unlock();
        }
    }

    private void consumeLeadingCuts() throws IOException {
        bufLock.lock();
        try {
            log("consumeLeadingCuts(): got lock");
            while (peek() == CUT_STREAM) {
                mPos++;
            }
        } finally {
            bufLock.unlock();
        }
    }

    @Override
    public int read() throws IOException {

        bufLock.lock();
        try {
            log("read(): got lock");
            if (isPaused.get() || mNumBlocked > 0) {
                /*
                    Should any code be wacky enough to have multiple threads reading from
                    the same input stream, and one of them is paused, then explode.
                    (We could pause them, but we'd end up
                    having to solve all sorts of problems to do with missing signals etc from repeat
                    pauses, should any MockInputStream use those)
                */
                throw new AssertionError("Multiple readers from paused input stream?!");
            }

            consumeLeadingCuts();

            int value = peek();
            log("read(): value:" + value);

            switch (value) {
                case ASSERT:
                    // don't touch mPos, so that ASSERT 'latches'
                    throw new AssertionError();
                case THROW_IO_EXCEPTION:
                    // don't touch mPos, so that THROW_IO_EXCEPTION 'latches'
                    throw new IOException(
                            "An Test IO exception was thrown on purpose e.g. stream closed");
                case EOF:
                    // don't touch mPos, so that EOF 'latches'
                    return EOF;
                case PAUSE:
                    // increment mPos before we pauseThisThread, so that this state change
                    // doesn't corrupt a setData that caused us to unpaused.
                    mPos += 1;
                    pauseThisThread();
                    return read();
                case BLOCK:
                    blockUntilClose();
                    return EOF;
                default:
                    // Java's lack of unsigned means our test data could end up being the "wrong"
                    // byte value. So make sure it isn't...
                    int outValue = value & 0xff;
                    if (outValue < 0 || outValue > 0xFF || outValue != value) {
                        String err = "dodgy test values outValue=%d value=%d";
                        throw new AssertionError(
                                String.format(Locale.ENGLISH, err, outValue, value));
                    }
                    mPos += 1;
                    return outValue;
            }
        } finally {
            bufLock.unlock();
        }
    }

    @Override
    @IntRange(from = -1, to = 255)
    public int read(@NonNull byte[] b, int off, int len)
            throws IOException {

        bufLock.lock();
        try {
            log("read(buf): got lock");
            if (isPaused.get() || mNumBlocked > 0) {
                throw new AssertionError("Multiple readers from paused input stream?!");
            }

            // Ensure we don't return 0, due to contract of read()
            consumeLeadingCuts();

            if (this.available() == 0) {
                return EOF;
            }

            int bytesWritten = 0;
            while (bytesWritten < len) {
                if (peek() == CUT_STREAM) {
                    consumeLeadingCuts();
                    if (bytesWritten == 0) throw new AssertionError();
                    break;
                }

                int val = read();
                if (val == EOF) {
                    break;
                }
                b[off + bytesWritten] = (byte) val;
                bytesWritten += 1;
            }
            return bytesWritten;

        } finally {
            bufLock.unlock();
        }
    }

    @Override
    public long skip(long n) throws IOException {
        //noinspection ImplicitNumericConversion
        if (n < 0L || n > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Can only skip in range[0, Integer.MAX_VALUE]");
        }
        int skip = (int) n;

        bufLock.lock();
        try {
            log("skip: got lock");
            int available = max(this.available(), skip);
            mPos += available;
            //noinspection ImplicitNumericConversion
            return available;
        } finally {
            bufLock.unlock();
        }
    }

    @Override
    public int available() throws IOException {
        bufLock.lock();
        try {
            log("available: got lock");
            if (mIsClosed) throw new IOException("Stream is closed!");
            if (mBuf == null) return 0;
            if (mPos >= mCount) return 0;
            if (mBuf[mPos] == EOF) return 0;
            return mCount - mPos;
        } finally {
            bufLock.unlock();
        }

    }

    @Override
    public boolean markSupported() {
        return true;
    }

    @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
    @Override
    public void mark(int readAheadLimit) {
        bufLock.lock();
        try {
            log("mark: got lock");
            mMark = mPos;
        } finally {
            bufLock.unlock();
        }
    }

    @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
    @Override
    public void reset() {
        bufLock.lock();
        try {
            log("reset: got lock");
            mPos = mMark;
        } finally {
            bufLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        bufLock.lock();
        try {
            log("close: got lock");
            mIsClosed = true;
            if (throwOnClose) {
                throw new IOException("Throwing on close()");
            }
        } finally {
            signalPausedThreads();
            signalBlockedThreads();
            bufLock.unlock();
        }
    }

    public void reopen() {
        bufLock.lock();
        try {
            if (isPaused.get() || mNumBlocked > 0) {
                log("!! Threads still paused in reopen()?");
            }

            if (!mIsClosed) {
                mIsClosed = true;
                signalPausedThreads();
                while (mNumBlocked > 0) {
                    // Incase things are blocked, given them time to unblock
                    // and to notice that `isClosed == true` before we set it false.
                    signalBlockedThreads();
                    somethingUnblocked.awaitUninterruptibly();
                }
            }

            setData(mBuf);
            mIsClosed = false;
        } finally {
            bufLock.unlock();
        }
    }

    public void setThrowOnClose() {
        throwOnClose = true;
    }

    private boolean waitForPause(long timeout, TimeUnit unit) {
        return waitForPauseNanos(unit.toNanos(timeout));
    }

    private boolean waitForPauseNanos(long nanos) {
        bufLock.lock();
        try {
            log("waitForPauseNanos: got lock");
            while (!isPaused.get()) {
                log("waitForPauseNanos: nanos=" + nanos);
                log("waitForPauseNanos: isPaused: still false");
                log("waitForPauseNanos: somethingIsPaused.awaitNanos");
                nanos = somethingIsPaused.awaitNanos(nanos);
                if (nanos <= 0L) {
                    log("waitForPauseNanos: timed out! nanos=" + nanos);
                    return false;
                }
            }
            log("waitForPauseNanos: something is paused!");
            return true;
        } catch (InterruptedException ignore) {
            return false;
        } finally {
            bufLock.unlock();
        }
    }

    private void pauseThisThread() throws IOException {
        bufLock.lock();
        try {

            log("pauseThisThread: got lock");
            isPaused.set(true);
            while (isPaused.get()) {
                log("pauseThisThread: isPaused: still true");
                log("pauseThisThread: somethingIsPaused.signalAll");
                log("pauseThisThread: waitingToUnpause.await");
                somethingIsPaused.signalAll();
                waitingToUnpause.await();
            }
        } catch (InterruptedException e) {
            throw new IOException("pause was interrupted", e);
        } finally {
            bufLock.unlock();
        }
    }

    private void signalPausedThreads() {
        bufLock.lock();
        try {
            log("signalPausedThreads: got lock");
            log("signalPausedThreads: waitingToUnpause.signalAll");
            isPaused.set(false);
            waitingToUnpause.signalAll();
        } finally {
            bufLock.unlock();
        }
    }

    public void waitAndSignalPausedThreads() {
        waitAndSignalPausedThreads(1L, TimeUnit.SECONDS);
    }

    public void waitAndSignalPausedThreads(long timeout, TimeUnit unit) {
        if (!waitForPause(timeout, unit)) {
            throw new AssertionError("waitForPause timed out!");
        }
        signalPausedThreads();
    }

    public void waitForPauseAndSetData(int[] bytes) {
        waitForPauseAndSetData(bytes, 1L, TimeUnit.SECONDS);
    }

    public void waitForPauseAndSetData(int[] bytes, long timeout, TimeUnit timeUnit) {
        if (!waitForPause(timeout, timeUnit)) {
            throw new AssertionError("timed out waiting for input poller to read initial !PAUSE");
        }
        setData(bytes);
        signalPausedThreads();
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void blockUntilClose() {
        bufLock.lock();
        try {
            mNumBlocked += 1;

            while (!mIsClosed) {
                blocked.awaitUninterruptibly();
            }

            mNumBlocked -= 1;
            somethingUnblocked.signalAll();

        } finally {
            bufLock.unlock();
        }
    }

    private void signalBlockedThreads() {
        bufLock.lock();
        try {
            blocked.signalAll();
        } finally {
            bufLock.unlock();
        }
    }

    private static void log(String s) {
//        long threadId = Thread.currentThread().getId();
//        String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss.SSS").format(new Date());
//        System.out.printf("[thread: %08d, time:%s] %s%n", threadId, timestamp, s);
    }
}
