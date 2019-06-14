/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.CommandType;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.CancelledKeyException;

public final class StubConnector extends Connector {

    @Nullable
    private final MockInputStream mInput;
    @Nullable
    private final MockOutputStream mOutput;

    private boolean mDontConnectProperly = false;
    private boolean mConnected = false;
    private boolean mThrowOnConnect = false;
    private boolean mThrowOnDisconnect = false;
    private boolean mDisconnectCalled = false;
    private boolean mThrowOnGetInputStream = false;
    private boolean mThrowOnGetOutputStream = false;
    private boolean mThrowRuntimeOnConnect = false;
    private boolean mUseSessionInDisconnect = false;
    private @Nullable CommandApdu mCommandApdu;
    private @Nullable InterfaceType mNad;

    public StubConnector() {
        this(null, null);
    }

    public StubConnector(
            @Nullable MockInputStream input,
            @Nullable MockOutputStream output) {
        mInput = input;
        mOutput = output;
    }

    public void setThrowOnConnect(boolean value) {
        mThrowOnConnect = value;
    }

    public void setThrowRuntimeExceptionOnConnect(boolean value) {
        mThrowRuntimeOnConnect = value;
    }

    public void setDontConnectProperly(boolean value) {
        // This is really "connect, but then disconnect instantly"
        mDontConnectProperly = value;
    }

    public void setThrowOnGetInputStream(boolean value) {
        mThrowOnGetInputStream = value;
    }

    public void setThrowOnGetOutputStream(boolean value) {
        mThrowOnGetOutputStream = value;
    }

    public void setThrowOnDisconnect(boolean throwOnDisconnect) {
        mThrowOnDisconnect = throwOnDisconnect;
    }

    public void setUseSessionInDisconnect(@Nullable InterfaceType nad, @Nullable CommandApdu apdu) {
        if (nad != null && apdu != null) {
            mNad = nad;
            mCommandApdu = apdu;
            mUseSessionInDisconnect = true;
        } else {
            mUseSessionInDisconnect = false;
        }
    }

    @Override
    public boolean isConnected() {
        return mConnected;
    }

    @Override
    public void connect() throws IOException {
        if (mThrowRuntimeOnConnect) {
            throw new RuntimeException("Throwing a runtime error on connect");
        } else if (mThrowOnConnect) {
            throw new IOException("Throwing on connect!");
        } else if (mDontConnectProperly) {
            return;
        }

        if (mInput != null) {
            mInput.reopen();
        }
        if (mOutput != null) {
            mOutput.reopen();
        }

        mConnected = true;
    }

    @Override
    public void disconnect(@NonNull MpiProtocolSession closingSession) throws IOException {
        disconnectImpl(closingSession);
    }

    void forceDisconnect() throws IOException {
        disconnectImpl(null);
    }

    private void disconnectImpl(@Nullable MpiProtocolSession session) throws IOException {
        mDisconnectCalled = true;

        if (mThrowOnDisconnect) {
            throw new IOException("Throwing on disconnect!");
        }

        if (mInput != null) {
            mInput.close();
            // Sleep for a bit to let input poller 'catch up'
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        if (mUseSessionInDisconnect) {
            if (session == null || mNad == null || mCommandApdu == null) {
                throw new AssertionError("Invalid state for mUseSessionInDisconnect");
            }
            session.sendCommandAPDU(mNad, mCommandApdu);
        }
        if (mOutput != null) {
            mOutput.close();
        }
        mConnected = false;
    }

    @NonNull
    @Override
    public InputStream getInputStream() throws IOException {
        if (mInput == null) {
            throw new AssertionError("getInputStream == null. Bad test");
        } else if (mThrowOnGetInputStream) {
            throw new IOException("Throwing on getInputStream!");
        }
        return mInput;
    }

    @NonNull
    @Override
    public OutputStream getOutputStream() throws IOException {
        if (mOutput == null) {
            throw new AssertionError("getOutputStream == null. Bad test");
        } else if (mThrowOnGetOutputStream) {
            throw new IOException("Throwing on getOutputStream!");
        }
        return mOutput;
    }

    public boolean wasDisconnectCalled() {
        return mDisconnectCalled;
    }
}
