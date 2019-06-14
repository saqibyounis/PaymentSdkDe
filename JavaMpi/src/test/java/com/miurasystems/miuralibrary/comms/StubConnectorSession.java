/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StubConnectorSession extends Connector {

    @Nullable
    private MpiProtocolSession mMockSession;
    private boolean mOpened;
    private boolean mThrowOnOpen;

    public StubConnectorSession() {
        mMockSession = null;
        mOpened = false;
    }

    public StubConnectorSession(@Nullable MpiProtocolSession mockSession) {
        mMockSession = mockSession;
        mOpened = false;
    }

    @NonNull
    @Override
    public MpiProtocolSession openSession(
            @NonNull UnsolicitedResponseCallback unsolicitedResponseCallback,
            @NonNull ConnectionStateCallback connectionStateCallback
    ) throws IOException {

        if (mThrowOnOpen) {
            throw new IOException("Throwing on open: Failed to open session on connector");
        }

        if (mOpened) {
            throw new IllegalStateException("Session already open.");
        } else {
            mOpened = true;
        }

        if (mMockSession == null) {
            throw new AssertionError("Bad test: mMockSession not set");
        }
        return mMockSession;
    }

    @Override
    public void closeSession() {
        mOpened = false;
    }

    @Override
    public boolean isConnected() {
        throw new AssertionError("Bad test: shouldn't be calling this?");
    }

    @Override
    protected void connect() throws IOException {
        throw new AssertionError("Bad test: shouldn't be calling this?");
    }

    @Override
    protected void disconnect(@NonNull MpiProtocolSession closingSession) throws IOException {
        throw new AssertionError("Bad test: shouldn't be calling this?");
    }

    @NonNull
    @Override
    protected InputStream getInputStream() throws IOException {
        throw new AssertionError("Bad test: shouldn't be calling this?");
    }

    @NonNull
    @Override
    protected OutputStream getOutputStream() throws IOException {
        throw new AssertionError("Bad test: shouldn't be calling this?");
    }

    public void setThrowOnOpen(boolean throwOnOpen) {
        mThrowOnOpen = throwOnOpen;
    }

    public void setMockSession(@NonNull MpiProtocolSession mockSession) {
        mMockSession = mockSession;
    }
}
