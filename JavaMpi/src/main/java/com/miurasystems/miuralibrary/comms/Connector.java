/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * A re-openable, fully-duplex, socket-like object that connects to an MPI device.
 *
 * <p>
 * {@code Connector} abstracts the network & data link part of connecting to a Miura device.
 * This allows the Miura SDK to talk (using MPI) over a byte stream to the device
 * without worrying about the physical or platform specific way of connecting to it.
 * </p>
 *
 * <p>
 * To allow the SDK to connect to a Miura Device
 * integrators need to {@code extend Connector} and implement the abstract methods to
 * provide the functionality required by their platform and chosen connection type.
 * The {@code Connector} is then passed by the app to the main SDK classes,
 * (i.e. {@link MiuraManager} and {@link MpiClient}),
 * which use the Connector-wrapped-link to open a byte stream and talk to a Miura device.
 * </p>
 *
 * <h3>Contract</h3>
 *
 * <p>
 * Unlike, e.g. {@link java.net.Socket}, a Connector is considered 're-openeable'.
 * In other words, a Connector can be opened and closed as many times as the SDK requires.
 * Integrating applications needs to be aware of this and act accordingly when managing their
 * resources.
 * {@link #connect()} will never been called twice in succession without calling
 * {@link #disconnect(MpiProtocolSession)} between them. {@link #disconnect(MpiProtocolSession)},
 * however, can be called whenever
 * the SDK likes and can be called multiple times in a row, so it should be idempotent if possible.
 * e.g. {@link ClientSocketConnector} has to create and destroy {@link java.net.Socket} objects
 * as appropriate.
 * </p>
 *
 * <p>
 * Open a Connector is "connected" a session is opened on the data streams
 * (see {@link MpiProtocolSession}). The MpiProtocolSession is responsible for
 * sending data over the streams.
 * Integrator's applications should not send <b>any data</b>
 * down the data link that a Connector object is wrapping
 * (even before that data link is wrapping in a Connector!),
 * as the other end will be a
 * Miura device expecting to communicate via the MPI protocol,
 * which is the responsibility of the Miura SDK.
 * </p>
 *
 * <p>
 * Integrators's classes extending Connectors <b>should not</b> call any of the non-abstract
 * methods that are part of the abstract class, including {@link #closeSession()}.
 * Just close the underlying stream resources. The SDK will "find out" and call closeSession
 * itself.
 * <br />
 * Integrator's other classes should not call <b>any</b> methods on a Connector,
 * except perhaps {@link #isConnected()}.
 * The SDK is responsible for calling the methods.
 * </p>
 *
 * <p>
 * An extending class also needs to take care of the exceptions throw by their
 * wrapped resources and translate them into what Connector expects.
 * </p>
 *
 *
 * <h3> Notes </h3>
 *
 * <p>Some common and platform-agnostic Connector's
 * are already available in the SDK comms package, and other examples
 * are available in the SDK tests and the App. e.g. {@link ClientSocketConnector}.
 * </p>
 *
 * <p>
 * Connector is an abstract class rather than an interface as the final methods present
 * set behaviour the SDK would like to perform on all Connectors. The SDk also targets Java < 8
 * so 'default' methods can't be used.
 * </p>
 */
public abstract class Connector {

    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);

    /**
     * If not null: The currently active session using this Connector
     * If null: There is not Session active on this Connector.
     */
    @Nullable
    private MpiProtocolSession mSession;

    /**
     * Open a new session on a Connector.
     * <p>
     * Causes {@link #connect} to be called on the extended Connector.
     * </p>
     * <p>
     * If a Connector currently has an outstanding session then
     * {@link #closeSession()} is called and IllegalStateException is thrown.
     * </p>
     * <p>
     * See {@link MpiProtocolSession#open()} for more information on session and the parameters.
     * </p>
     * <p>
     * <b>NOTE: This not a final method, but it's also not abstract, so
     * don't override this in your extending class.</b>
     * </p>
     *
     * @param unsolicitedResponseCallback The method to call whenever a <b>unsolicited</b>
     *                                    ResponseMessage is read from the Connector.
     *                                    Note that callbacks will happen in a different thread
     *                                    of execution than the one calling this function.
     * @param connectionStateCallback     The method to call whenever a connection event occurs,
     *                                    i.e. when the session connects and disconnects.
     *                                    Can be called from multiple threads.
     * @return The newly opened session
     * @throws IllegalStateException If a session is already open
     * @throws IOException           if the session cannot be opened
     */
    @NonNull
    public MpiProtocolSession openSession(
            @NonNull UnsolicitedResponseCallback unsolicitedResponseCallback,
            @NonNull ConnectionStateCallback connectionStateCallback
    ) throws IOException {
        // NOTE: This not final, but don't override this in your code
        //noinspection VariableNotUsedInsideIf
        if (mSession != null) {
            closeSession();
            throw new IOException("Session already open.");
        } else if (isConnected()) {
            closeSession();
            throw new IOException("Connector already connected?");
        }

        connect();

        mSession = MpiProtocolSession.makeMpiProtocolSession(
                this, unsolicitedResponseCallback, connectionStateCallback);
        if (mSession == null) {
            closeSession();
            throw new IOException("Failed to open session on Connector");
        }

        return mSession;
    }

    /**
     * Closes any currently open session.
     *
     * <p>
     * <b>May</b> cause {@link #disconnect} to be called.
     * <p>
     * <b>NOTE: This not a final method, but it's also not abstract, so
     * don't override this in your extending class.</b>
     * <b>Only MiuraManager/MpiClient should be calling this. Don't call it from App code or from
     * Connector.</b>
     * </p>
     */
    public void closeSession() {
        // not final as it's being abused by a test class. but don't override this in your code
        if (mSession == null) {
            return;
        }
        mSession.close(); // results in sessionIsClosing being called.
    }

    /**
     * Called from the session whenever it is closing.
     *
     * <p>
     * The session is still "active" at this time and may be used to assist in clean-up.
     * The session expects the Connector to be disconnected after this call.
     * After the return of this function the session will clean-up and close.
     * </p>
     * @param session The session that is closing
     */
    final void sessionIsClosing(@NonNull MpiProtocolSession session) {
        if (mSession != null && !mSession.equals(session)) {
            LOGGER.warn("sessionIsClosing called from another Connector's session?!");
            mSession.close();
        }
        mSession = null;

        try {
            disconnect(session);
        } catch (IOException e) {
            LOGGER.debug("Exception in disconnect.", e);
        }

    }

    /**
     * Returns the connected state of the Connector and its underlying data link
     *
     * see {@link #connect()} and {@link #disconnect(MpiProtocolSession)} for more.
     *
     * @return true if the Connector is currently connected and usable
     * false if the Connector is "broken" and needs to be re-connected.
     */
    public abstract boolean isConnected();

    /**
     * Connect the Connector's data link
     *
     * <p>
     * Rules extending classes should be aware of:
     * </p>
     * <ul>
     *
     * <li>
     * A Connector is re-openable, which means {@code connect()} can be called multiple times
     * during the lifetime of the Connector/app.
     * </li>
     * <li>
     * Before {@code connect()} has been called {@link #isConnected()} is expected to return false.
     * </li>
     * <li>
     * Once {@code connect()} has been called {@link #isConnected()} is expected to return true,
     * until the data link is broken in some way
     * (either via {@link #disconnect(MpiProtocolSession)}
     * or when the other side terminates the connection etc).
     * </li>
     * <li>
     * {@code connect()} will never been called twice in succession without the SDK calling
     * {@link #disconnect(MpiProtocolSession)} between them, even if something else caused the data
     * link to die
     * (e.g. the other side terminating the connection)
     * </li>
     * <li>
     * Exceptions encountered when connecting the data link should be wrapped in
     * {@code IOException} if they're not already of that type.
     * </li>
     * </ul>
     *
     *
     * <p>
     * These rules give extending classes explicit bounds on how to manage resources.
     * e.g. {@link ClientSocketConnector} has to create and destroy {@link java.net.Socket} objects
     * as appropriate.
     * </p>
     *
     * @throws IOException If an error was encountered when connecting
     */
    protected abstract void connect() throws IOException;


    /**
     * Disconnect the Connector's data link during a session close
     *
     * <p>
     * Rules extending classes should be aware of:
     * </p>
     * <ul>
     *
     * <li>
     * A Connector is re-openable, which means {@code disconnect()} can be called multiple times
     * during the lifetime of the Connector/app, followed by a {@link #connect()}.
     * </li>
     * <li>
     * The SDK may possibly call {@code disconnect()} multiple times in a row, with no
     * intervening call to any other Connector method (e.g. {@link #connect()},
     * so {@code disconnect} should be idempotent.
     * </li>
     * <li>
     * The underlying data link <b>may</b> already be broken by the time
     * {@code disconnect} is called (infact this might be the reason why the SDK calls it!).
     * </li>
     * <li>
     * {@code disconnect} should close the input and output streams (if they're open) to ensure
     * that any threads waiting on those resources are unblocked.
     * </li>
     * <li>
     * If {@link #isConnected()} is currently returning {@code true} then after a call to
     * {@code disconnect()} it should return false.
     * </li>
     * <li>
     * Exceptions encountered when disconnecting the data link should be wrapped in
     * {@code IOException} if they're not already of that type.
     * Every attempt should be made to close both streams,
     * even if an exception is encountered whilst closing one of them, as this helps prevent
     * zombie threads waiting on those resources.
     * </li>
     * <li>
     * The Session that is currently closing is provided as a parameter incase any 'clean up'
     * operations need to be performed over the connection. e.g. sending a SERIAL_DISCONNECT.
     * </li>
     * <li>
     * As this MpiProtocolSession is in the process of closing DO NOT call things
     * such as {@link MpiProtocolSession#close()}, {@link MpiProtocolSession#open()}, etc.
     * Stick to send and receive.
     * </li>
     * <li>
     * After disconnect has returned the Session will expect the connection to be closed
     * and will 'clean-up' any resources it used.
     * </li>
     * </ul>
     *
     * @param closingSession The session that is in the process of closing.
     * @throws IOException If an error was encountered whilst disconnecting
     */
    protected abstract void disconnect(@NonNull MpiProtocolSession closingSession)
            throws IOException;

    /**
     * Return a valid input stream for the Connector
     *
     * <p>
     * {@code getInputStream()} will only ever be called after {@link #isConnected()} returns true.
     * Due to the transitory nature of the 'connected' status it's possible that
     * {@link #isConnected()} it returns false by the time {@code getInputStream()} is called,
     * in which case an exception can be raised.
     * </p>
     *
     * @return A valid input stream
     * @throws IOException If there was a problem opening the input stream.
     */
    @NonNull
    protected abstract InputStream getInputStream() throws IOException;

    /**
     * Return a valid output stream for the Connector
     *
     * <p>
     * {@code getOutputStream()} will only ever be called after {@link #isConnected()} returns true.
     * Due to the transitory nature of the 'connected' status it's possible that
     * {@link #isConnected()} it returns false by the time {@code getOutputStream()} is called,
     * in which case an exception can be raised.
     * </p>
     *
     * @return A valid output stream
     * @throws IOException If there was a problem opening the output stream.
     */
    @NonNull
    protected abstract OutputStream getOutputStream() throws IOException;
}
