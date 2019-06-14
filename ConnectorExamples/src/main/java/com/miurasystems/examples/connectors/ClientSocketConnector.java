/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.examples.connectors;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;


/**
 * IP socket Connector which connects to a PED.
 * <p>
 * Wraps an underlying stream socket {@link Socket}.
 * This socket is a client, the PED is acting as a server.
 * </p>
 */
public final class ClientSocketConnector extends Connector {

    /**
     * IP address and port this Connector connects to.
     */
    @NonNull
    private final InetSocketAddress mAddress;

    /**
     * Timeout, in milliseconds. 0 = default
     */
    private final int mTimeoutMs;

    /**
     * IP Socket that this Connector wraps.
     * <p>
     * If it's null, we're definitely disconnected. If it's non-null, we're probably connected.
     */
    @Nullable
    private Socket mSocket;

    /**
     * Create a new ClientSocketConnector that wraps an IP Socket.
     *
     * <p>The socket connection attempt has infinite timeout.</p>
     *
     * @param address IP address to connect to
     * @param port    port number to connect to
     */
    public ClientSocketConnector(@NonNull InetAddress address, int port) {
        this(address, port, 0);
    }

    /**
     * Create a new ClientSocketConnector that wraps an IP Socket.
     *
     * <p>The socket connection attempt has optional timeout.</p>
     *
     * @param address             IP address to connect to
     * @param port                port number to connect to
     * @param connectionTimeoutMs Timeout, in milliseconds, used for connection attempt.
     *                            0 = infinite/blocking. non-0 = timeout in milliseconds.
     *                            All read() and write() operations will still be blocking
     *                            regardless of the value of connectionTimeoutMs.
     */
    public ClientSocketConnector(
            @NonNull InetAddress address,
            int port,
            int connectionTimeoutMs
    ) {
        mAddress = new InetSocketAddress(address, port);
        mTimeoutMs = connectionTimeoutMs;
        mSocket = null;
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

        Socket socket = new Socket();
        socket.connect(mAddress, mTimeoutMs);
        mSocket = socket;
    }

    @Override
    protected void disconnect(@NonNull MpiProtocolSession closingSession) throws IOException {
        if (mSocket == null || mSocket.isClosed()) {
            return;
        }
        mSocket.close();
        mSocket = null;
    }

    @NonNull
    @Override
    protected InputStream getInputStream() throws IOException {
        if (mSocket == null) {
            throw new IOException("Socket is closed");
        }

        return mSocket.getInputStream();
    }

    @NonNull
    @Override
    protected OutputStream getOutputStream() throws IOException {
        if (mSocket == null) {
            throw new IOException("Socket is closed");
        }

        return mSocket.getOutputStream();
    }
}
