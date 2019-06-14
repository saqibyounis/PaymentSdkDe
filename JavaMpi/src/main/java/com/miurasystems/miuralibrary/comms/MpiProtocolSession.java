/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.miurasystems.miuralibrary.CommandType;
import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.EnumMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An session belongs to a Connector and handles MPI messages over it.
 *
 * <p>
 * MpiProtocolSession is the primary communication interface to an MPI-speaking device.
 * It doesn't care about the contents of the messages, only that they are messages.
 * </p>.
 *
 * <p> Note that a {@link Connector} can be opened again after it's closed.
 * Each Connector can only have a single session running at a time.
 * The session is responsible for calling {@link Connector#disconnect(MpiProtocolSession)}
 * </p>
 *
 * <p>
 * A session, once created and 'opened', will live for the life-time of the connection.
 * Once the connection is broken (either by calling {@link #close()} or the remote Connector
 * breaking) the session is finished and no more activity can take place on it. If more commands
 * wish to be sent over the same Connector then a new session needs to be opened on it. If
 * any activity other than {@link #close()} is performed on the session once it's closed then
 * an {@code IllegalStateException} will be thrown.
 * </p>
 * <p>
 * <b>Importantly</b>, in the case of any error when occurring whilst using the session's
 * methods then the session will immediately close itself, breaking the connection.
 * </p>
 *
 * <p>
 * The session makes use a separate thread running an InputResponsePoller. The poller's job is to
 * read the MPI ResponseMessages from the Connector's input stream
 * ({@link Connector#getInputStream()} as soon as they arrive,
 * whilst the session's job is to keep those messages around until the client asks for them.
 * The main use of this "urgency" via the thread
 * is to support the unsolicited messages in a low-latency,
 * interrupting fashion, and to ensure no messages are lost if so many
 * are sent before being read that e.g. a small network buffer on a phone fills up.
 *
 * The thread lifetime is handled by the session -- i.e. When the session is closed the
 * Thread is cleaned up.
 * </p>
 *
 * <p>The Poller thread passes the messages over a BlockingQueue ({@link #mQueues},
 * one queue for each MPI channel ({@link InterfaceType}).
 * Which channel is used is up to the devices, and if an device
 * type is not present in the system (e.g. no POSzle) then the associated channel will not be used
 * and no ResponseMessages will arrive on it, but the session doesn't know that.
 * </p>
 *
 * <p>
 * The MPI protocol allows a device to send "unsolicited messages" at any time, and an application
 * <i>may</i> wish to perform an action when it sees these messages, so the MpiProtocolSession
 * (and in-turn the InputResponsePoller) supports a callback mechanism that allows the client
 * to be notified whenever these messages arrive.
 * </p>
 * <p> The unsolicited messages are not placed in the normal queue and will not be returned
 * when {@link #receiveResponse} is called. They are only passed back via the callback.
 * <b>Note</b> that the callback will be executed in
 * the Poller's thread, not the thread of the client, and due to its "interrupting" nature
 * the unsolicited message may therefore be "seen" by a client before a related message has been
 * received by it.
 * </p>
 *
 * <p>The session will also report when the Connector is known to have connected and disconnected.
 * It will only report each event once, as the session can only do each thing once.
 * It will only report the disconnect event if it sent the connected event.
 * See {@link ConnectionStateCallback#handle(boolean)} for more.
 * </p>
 *
 * <pre>{@code
 *             Current Thread               ║║        New Thread
 *       ═══════════════════════════════════╬╬════════════════════════════════════
 *                                          ║║
 *                              ┌───────────╨╨───────┐
 *                              │ Connector ¦¦       │
 *                              │           ¦¦       │
 *                       ┌──────┴───────┐   ¦¦  ┌────┴─────────┐
 *  ┌─────────┐          │ OutputStream ├───╥╥──┤ InputStream  ├►─────────────────────┐
 *  │  Client ├─[New]─┐  └─────▲────────┘   ║║  └──────────────┘                      │
 *  └──────┬──┘       │        │            ║║                                        │
 *      ▲  │       ╔══╧════════╧════════╗   ║║    ╔═══════════════════════════════════╪══╗
 *      │  │       ║ MpiProtocolSession ║   ║║    ║   InputResponsePoller             │  ║
 *      │  │       ║          ─┬─       ║   ║║    ║                                   │  ║
 *      │  │   ┌───╨────┐      │        ║   ║║    ║                                   │  ║
 *      │  └──►│ Send()○───────┘        ║   ║║    ║                                   │  ║
 *      │      └───╥────┘               ║   ║║    ║                                   │  ║
 *      │          ║                    ║   ║║    ║ ┌─Run()────────────────────────┐  │  ║
 *      │          ║ ┌────────────────┐ ║   ║║    ║ │ 1. Read from InputStream  ◄──┼──┘  ║
 *      │          ║ │ BlockingQueue ◄┼─╫───╫╫────╫○│ 2. a) Add to Queue           │     ║
 *      │          ║ └─────────┬──────┘ ║   ║║    ║ │         or                   │     ║
 *      │          ║           │        ║   ║║    ║ │  ○ b) Call callback          │     ║
 *      │    ┌─────╨─────┐     │        ║   ║║    ║ └──┼───────────────────────────┘     ║
 *      └────┤ Receive() │◄────┘        ║   ║║    ║    │                                 ║
 *           └─────╥─────┘              ║   ║║    ║    │                                 ║
 *                 ║                    ║   ║║    ╚════╪═════════════════════════════════╝
 *                 ╚═════════════╤══════╝   ║║         │
 *                               │          ║║         │
 *    ╔════════════════════╗     │          ║║    ╔════▼══════════════════╗
 *    ║  Connection state  ║◄────┘          ║║    ║  Unsolicited message  ║
 *    ║      callback      ║                ║║    ║        callback       ║
 *    ╚════════════════════╝                ║║    ╚═══════════════════════╝
 *                                          ║║
 * }</pre>
 *
 * <p>
 * Creating a MpiProtocolSession is a multi-stage process  so
 * the supported way of creating a session is via {@link #makeMpiProtocolSession}, which, when
 * given a connected Connector, will create a MpiProtocolSession, create the poller and thread, and
 * finally {@link #open()} the session. Once opened commands can be sent and responses received.
 * Note that commands and responses are symmetrical. For each command sent there will be a response,
 * assuming the MPI device doesn't explode, and there should be no solicited responses without a
 * command. Therefore the session enforces the fact that responses can only be read
 * if there are "outstanding" commands that have not yet been answered.
 * </p>
 *
 * <p>
 * The session tracks the commands and responses it sends and issues each one an id.
 * The command/response pair will have the same matching ids.
 * </p>
 */
public class MpiProtocolSession {

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MpiProtocolSession.class);

    /**
     * Non-blocking read. Get value if present, if not, return null.
     *
     * <p>Used to control the timeout behaviour of {@link #receiveResponse}
     */
    private static final long NO_TIMEOUT_QUICK = 0L;

    /**
     * Blocking read with no timeout period. Block until a value is present.
     *
     * <p>Used to control the timeout behaviour of {@link #receiveResponse}
     */
    private static final long NO_TIMEOUT_BLOCK = -1L;

    /**
     * The ResponseMessage queues, once queue for each Interface type.
     *
     * <p>We read from these queues. {@link #mPollerThread} will write to these queues.
     *
     * <p>(The ResponseMessages are wrapped in a PollerMessage)
     */
    @NonNull
    private final EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> mQueues;

    /**
     * The current status of the {@link InputResponsePoller} running in {@link #mPollerThread}.
     * <p><b>Note:</b> settings this won't make the thread stop!
     */
    @NonNull
    private final AtomicBoolean mInputPollerIsActive;

    /**
     * The Connector to talk to the MPI device on.
     *
     * <p> Note that we now "own" this Connector, and so are responsible for data to/from,
     * closing it, and tracking its state.
     *
     * <p> The input is handled by a {@link InputResponsePoller} running in {@link #mPollerThread}.
     * <p> The output is handled by this class in {@link #sendCommandAPDU}
     */
    @NonNull
    private final Connector mConnector;

    /**
     * Callback that is called every time an unsolicited message is read.
     *
     * <p>We currently don't do anything with this and simply pass it from the constructor to
     * the new {@link InputResponsePoller}.
     */
    @NonNull
    private final UnsolicitedResponseCallback mUnsolicitedResponseCallback;


    /**
     * Have we sent the disconnect event yet?
     *
     * <p>Used to ensure the connection event is only sent once</p>
     */
    @NonNull
    private final AtomicBoolean mDisconnectEventSent;

    /**
     * Have we sent the connect event yet?
     *
     * <p>Used to ensure the connection event is only sent once</p>
     */
    @NonNull
    private final AtomicBoolean mConnectEventSent;

    /**
     * Callback that is called when the session connects and disconnect.
     *
     * see {@link ConnectionStateCallback#handle(boolean)} for more
     */
    @NonNull
    private final ConnectionStateCallback mConnectionStateCallback;

    /**
     * The Thread that will be running the {@link InputResponsePoller}.
     *
     * <p>Will be null until {@link #startInputPollerThread()} is called
     * (via {@link #makeMpiProtocolSession},
     * and will be made null again when the session is closed ({@link #close()}
     *
     * <p>The InputResponsePoller will :
     * <ul>
     * <li>Reports it running/stopped status via {@link #updatePollerStatus}</li>
     * <li>Post ResponseMessages to the relevant {@link #mQueues}</li>
     * <li>Post unsolicited ResponseMessages to the {@link #mUnsolicitedResponseCallback}</li>
     * </ul>
     */
    @Nullable
    private Thread mPollerThread;

    /** Has {@link #open()} been called? */
    private boolean mOpened;

    /** has {@link #close()} been called (and returned)? */
    private boolean mClosed;

    /** has {@link #close()} been called? */
    private boolean mSessionIsClosing;

    /**
     * ID of the last solicited response we read from any queue.
     * -1 if no response has been read yet
     */
    private int mPreviousSolicitedResponseId;

    /**
     * ID of the last command we sent through the Connector.
     * -1 if no command has been sent yet
     */
    private int mPreviousCommandId;

    /**
     * Create a new MpiProtocolSession.
     *
     * <p> First part of the startup sequence.
     * {@link #startInputPollerThread()} ()} is next.
     * Though it's recommended to use {@link #makeMpiProtocolSession} instead.
     * </p>
     *
     * @param connector                   The connector the MpiProtocolSession belongs to. The
     *                                    MpiProtocolSession should be
     *                                    the only thing reading/writing to it and will call
     *                                    connector.disconnect as appropriate.
     * @param unsolicitedResponseCallback The method to call whenever a <b>unsolicited</b>
     *                                    ResponseMessage is read from the connector.
     *                                    Note that callbacks will happen in a different thread
     *                                    of execution than the one calling this function.
     * @param connectionStateCallback     The method to call whenever a connection event occurs,
     *                                    i.e. when the session connects and disconnects.
     *                                    Can be called from multiple threads.
     * @param queues                      One queue for each MPI channel / node address.
     *                                    All enum values must have a valid queue entry.
     */
    MpiProtocolSession(
            @NonNull Connector connector,
            @NonNull UnsolicitedResponseCallback unsolicitedResponseCallback,
            @NonNull ConnectionStateCallback connectionStateCallback,
            @NonNull EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> queues
    ) {
        mConnector = connector;
        mUnsolicitedResponseCallback = unsolicitedResponseCallback;
        mConnectionStateCallback = connectionStateCallback;
        mQueues = queues;
        mInputPollerIsActive = new AtomicBoolean(false);
        mDisconnectEventSent = new AtomicBoolean(false);
        mConnectEventSent = new AtomicBoolean(false);
        mPollerThread = null;

        mOpened = false;
        mClosed = false; // whilst it is not-open, it hasn't been closed()
        mSessionIsClosing = false;

        mPreviousSolicitedResponseId = -1;
        mPreviousCommandId = -1;
    }

    /**
     * Callback that the Poller thread will use to tell us its status.
     *
     * @param status        The new status of the Poller
     * @param lastHandledID the last solicited response that the Poller processed.
     */
    void updatePollerStatus(
            PollerStatusCallback.PollerStatus status,
            int lastHandledID
    ) {
        LOGGER.trace("updatePollerStatus called! status:{} id:{}", status, lastHandledID);
        switch (status) {
            case Running:
                mInputPollerIsActive.set(true);
                break;
            case StoppedCallbackError:
            case StoppedQueuePostTimedOut:
            case StoppedQueuePostInterrupted:
            case StoppedStreamBroken:
                mInputPollerIsActive.set(false);
                sendDisconnectEvent();
                break;
        }
    }

    /**
     * Send a connection event.
     *
     * <p>Ensures that only a single connection event is ever sent from a session
     */
    private void sendConnectionEvent() {
        if (mDisconnectEventSent.get()) {
            throw new AssertionError("Disconnected before connected?");
        }

        boolean justSet = mConnectEventSent.compareAndSet(false, true);
        if (justSet) {
            mConnectionStateCallback.handle(true);
        } else {
            throw new AssertionError("Double connection event?");
        }
    }

    /**
     * Send a disconnection event.
     *
     * <p>Ensures that only a single disconnection event is ever sent from a session
     */
    private void sendDisconnectEvent() {
        boolean justSet = mDisconnectEventSent.compareAndSet(false, true);
        boolean connectCallbackSent = mConnectEventSent.get();
        if (justSet && connectCallbackSent) {
            mConnectionStateCallback.handle(false);
        }
    }

    /**
     * Is the wrapped Connector connected and available for use?
     *
     * <p> This isn't all that helpful, as TOCTOU problems persist, so just because
     * this returns true it doesn't mean the next use of the Connector won't explode.
     *
     * @return true if it is connected, false otherwise
     */
    public boolean isConnected() {
        return mConnector.isConnected() && mInputPollerIsActive.get();
    }

    /**
     * Raise an exception if the session has already been closed.
     *
     * @throws IOException If session is closed
     */
    private void validateIsNotClosed() throws IOException {
        if (mClosed) {
            close();
            throw new IOException("Session already closed!");
        }
    }

    /**
     * Raise an exception, and close the session, if the session is not yet 'active'.
     *
     * <p> Active means it has been opened and but yet closed. See {@link #isActive()}.
     *
     * @throws IOException is session is not active
     */
    private void validateSessionIsActive() throws IOException {
        validateIsNotClosed();
        if (!mOpened) {
            close();
            throw new IOException("Session not opened!");
        }
    }

    /**
     * Raise an exception, and close the session, if the session is not yet in the 'startup' phase.
     *
     * <p> i.e. startup means it has not been opened yet
     *
     * @throws IOException is session is not in startup phase
     */
    private void validateSessionInStartup() throws IOException {
        validateIsNotClosed();
        if (mOpened) {
            close();
            throw new IOException("Session already open!");
        }
    }

    /**
     * Is the session currently "active"?
     *
     * <p> An active session is one that has been opened and but yet closed.
     * See {@link #open()} and {@link #close()}.
     *
     * @return true if session is active, false otherwise.
     */
    public boolean isActive() {
        return !mClosed && mOpened;
    }

    /**
     * Opens a session
     *
     * Final part of the startup sequence. Can now send/receive if it opens ok.
     *
     * <ul>
     * <li>Commands/responses cannot be sent/received on a session until it is <b>opened</b></li>
     * <li>A Connector must still be connected for a session to be opened</li>
     * <li>A session can only be opened once</li>
     * <li>A session is 'active' once it has been opened until it is 'closed'</li>
     * </ul>
     *
     * <p> If there is an error whilst opening the session, it is closed
     *
     * @return true if the session opened ok, false if an error occurred.
     */
    boolean open() {

        try {
            validateSessionInStartup();
        } catch (IOException ignore) {
            LOGGER.trace("Session not in startup phase?!");
            return false;
        }

        if (!isConnected()) {
            LOGGER.trace("Socket not connected?");
            close();
            return false;
        }

        this.mOpened = true;
        sendConnectionEvent();

        return true;
    }

    /**
     * Close the session
     *
     * <p>Disconnect the Connector and clean up any resources used by the session.
     *
     * <ul>
      * <li>
     *     When a session is closed the {@link Connector#disconnect(MpiProtocolSession)} is called
     * </li>
     * <li>A session is no longer 'active' once closed. No operations, e.g. send/receive,
     * are valid on a closed session, and it cannot be re-opened</li>
     * <li>A session is only closed if {@code closed} is called. A session that has yet to be
     * opened does not start as "closed", it's simply "not-opened" yet.</li>
     * <li>A session does not have to have be active  yet to be closed</li>
     * <li>{@code close} can be called multiple times with no side-effects</li>

     *
     * </ul>
     */
    void close() {
        LOGGER.trace("session close.");
        if (mClosed) {
            LOGGER.trace("...already closed");
            return;
        } else if (mSessionIsClosing) {
            // avoid recursive calls
            LOGGER.trace("...already closing!");
            return;
        }

        // By setting this, and not setting mClosed, we can allow the
        // session to be used during the closing process and avoid recursive calls in the
        // case of a session error calling close()
        mSessionIsClosing = true;
        mConnector.sessionIsClosing(this);

        sendDisconnectEvent();

        if (mPollerThread != null) {
            mPollerThread.interrupt();
            try {
                LOGGER.trace("Joining thread id:{} to id:{}",
                        Thread.currentThread().getId(), mPollerThread.getId());
                mPollerThread.join(500L);
                LOGGER.trace("Thread joined! ({})", mPollerThread.getId());
            } catch (InterruptedException e) {
                // This really should not happen. But there's not a lot we can do about it?
                // We could try again?
                // Todo should we set the interrupt flag back after this?
                // or throw interrupted exception?
                LOGGER.warn("Thread join interrupted!", e);
                try {
                    mPollerThread.join(10L);
                } catch (InterruptedException e1) {
                    LOGGER.warn("Thread join interrupted again?!", e1);
                }
            }

            if (mInputPollerIsActive.get()) {
                // Not a lot we can do about this. If the input poller won't die
                // we just hope that it's not holding some crucial resource that locks everything
                // up.
                //
                // The disconnection event was sent as part of close, so we
                // don't need to worry about the Poller thread later causing it.
                //
                // We can't (currently) stop it sending unsolicited events, however. But if it's
                // "stuck" somehow (e.g. blocked on input stream that doesn't obey interrupts)
                // it's highly unlikely that it'll:
                // a) read more data from its input stream
                // b) read an unsolicited event.
                //
                // This is seen with Android UsbAccessory code.
                // todo we could add a .cancel() method or something to ensure that the thread
                // stays silent, assuming it ever wakes up
                LOGGER.warn("Poller thread didn't close properly?");
                mPollerThread.setName("InputResponsePoller (zombie)");
            }
            mPollerThread = null;
        }

        mClosed = true;
    }

    /**
     * Send a command to the given device/channel.
     *
     * <ul>
     * <li>A session must be active to send a command {@link #isActive()}</li>
     * <li>An ID will be assigned to the command, and the ID returned.
     * The first command sent will have ID 0, the second 1, etc
     * </li>
     * <li>If there is a problem writing to a Connector then IOException will be thrown
     * and the session will be closed
     * </li>
     * </ul>
     *
     * @param nad  The device/channel/node address to send the command to
     * @param apdu The command to send.
     * @return The id of the command. The returned value is always a valid id,
     * there are no sentinel values. See also {@link #receiveResponseId(InterfaceType, int)}.
     * @throws IOException If there was an error writing to the Connector.
     */
    public int sendCommandAPDU(@NonNull InterfaceType nad, @NonNull CommandApdu apdu)
            throws IOException {

        validateSessionIsActive();

        byte[] bytes = apdu.getBytes();
        LOGGER.trace("sendCommandAPDU: nad:{} cmd:{} p1:{} p2:{}",
                nad,
                CommandType.valueOf(bytes[0], bytes[1]),
                String.format("0x%02x", bytes[2]),
                String.format("0x%02x", bytes[3]));

        OutputStream outputStream = mConnector.getOutputStream();

        if (!MpiPacket.writeToStream(nad, bytes, outputStream)) {
            close();
            throw new IOException("Failed to write to stream");
        }

        LOGGER.trace(
                "sendCommandAPDU. prev id = {}, returning id = {}",
                mPreviousCommandId, mPreviousCommandId + 1);
        mPreviousCommandId += 1;
        return mPreviousCommandId;
    }


    /**
     * Send a binary stream to the device.
     *
     * Do not call this unless the previous CommandApdu sent to a device was STREAM_BINARY.
     *
     * @param nad   The device/channel/node address to send the command to
     * @param bytes The binary data to send
     * @param len   The length of `bytes` to send.
     * @throws IOException If there was an error writing to the Connector.
     */
    public void sendBinaryStream(
            @NonNull InterfaceType nad,
            @NonNull @Size(min = 1) byte[] bytes,
            int len
    ) throws IOException {

        validateSessionIsActive();
        if (!isConnected()) {
            close();
            throw new IOException("Connector is not connected");
        }

        try {
            OutputStream outputStream = mConnector.getOutputStream();
            outputStream.write(bytes, 0, len);
            outputStream.flush();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /**
     * Block until a solicited response is available on the given channel.
     *
     * @param nad Which device/channel to receive the response on
     * @return The next ResponseMessage read from the channel
     * @throws IOException          In case of a Connector error whilst waiting
     * @throws InterruptedException If the thread is interrupted whilst waiting.
     */
    @NonNull
    public ResponseMessage receiveResponse(@NonNull InterfaceType nad)
            throws IOException, InterruptedException {
        ResponseMessage rm = receiveResponse(nad, NO_TIMEOUT_BLOCK, null);
        if (rm == null) throw new AssertionError("NO_TIMEOUT_BLOCK timed out?");
        return rm;
    }

    /**
     * Block, for `timeout` ms, until a solicited response is available on the given channel.
     *
     * @param nad     Which device/channel to receive the response on
     * @param timeout The number of milli-seconds to wait before timing out.
     * @return The next ResponseMessage read from the channel. If no ResponseMessage was available
     * before the timeout period expired then null is returned.
     * @throws IOException          In case of a Connector error during the timeout period
     * @throws InterruptedException If the thread is interrupted whilst waiting.
     */
    @Nullable
    public ResponseMessage receiveResponseTimeout(@NonNull InterfaceType nad, long timeout)
            throws IOException, InterruptedException {
        if (timeout <= 0L) {
            throw new IllegalArgumentException("timeout must be greater-than 0");
        }
        return receiveResponse(nad, timeout, null);
    }

    /**
     * Block until a solicited ResponseMessage is available, and validate it matches the given id.
     *
     * Equivalent to {@code receiveResponse(nad, NO_TIMEOUT_BLOCK, id}
     *
     * See {@link #receiveResponse(InterfaceType, long, Integer)} for more information.
     *
     * @param nad Which device/channel to receive the response on
     * @param id  The id to validate this ResponseMessage against
     * @return The next ResponseMessage read from the channel
     * @throws IOException          In case of a Connector error whilst waiting
     * @throws InterruptedException If the thread is interrupted whilst waiting.
     */
    @NonNull
    public ResponseMessage receiveResponseId(@NonNull InterfaceType nad, int id)
            throws IOException, InterruptedException {
        ResponseMessage rm = receiveResponse(nad, NO_TIMEOUT_BLOCK, id);
        if (rm == null) throw new AssertionError("NO_TIMEOUT_BLOCK timed out?");
        return rm;
    }

    /**
     * Receive a solicited response on the given channel.
     *
     * <p>A response can be received in one of three modes:
     * <ul>
     * <li>Blocking (no timeout).
     * Waits until a response is available and returns it.
     * {@code timeout =} {@link #NO_TIMEOUT_BLOCK}
     * </li>
     * <li>Blocking with timeout.
     * Will return ResponseMessage or null if non available
     * when timeout limit hit. {@code timeout > 0}</li>
     * <li>Non-blocking (no timeout).
     * Will return ResponseMessage or null if non available.
     * {@code timeout =} {@link #NO_TIMEOUT_QUICK}</li>
     * </ul>
     *
     * In the blocking modes it's possible for an {@code InterruptedException} to be thrown.
     * </p>
     *
     * <p> A session must be active to be able receive a response {@link #isActive()} and an
     * outstanding command must have been sent that has not yet had its response received. If there
     * are no outstanding commands then an {@code IllegalStateException} is thrown.
     *
     * <p> If there are no responses left in the queue and the InputResponsePoller has stopped
     * (aka the Connector is closed), then an {@code IOException} will be thrown. If there are still
     * responses left in the queue when the Poller stops you will still be allowed to read them
     * until they are exhausted, at which point the next read will cause an {@code IOException}.
     *
     * <p> If there are problems with the InputResponsePoller then an {@code IOException}
     * will be thrown no matter what.
     *
     * <p> Each ResponseMessage read from the queue is given an id, stating at 0. This id
     * will match the id of the command that this is a response to.
     *
     * <p> If the {@code id} parameter is provided (i.e. non-null) then it must be the
     * next expected id. No skipping is allowed.
     * If an id is provided that is not the one expected then an {@code IllegalStateException} is
     * thrown.
     * Therefore responses can only be received one id at a time, in order. The id
     * parameter is mostly used to confirm that the caller is in sync with the expected responses.
     *
     * <p> If any exception is thrown then the session is closed and
     * no more responses can be received on a closed session.
     *
     * @param nad     Which device/channel to receive the response on
     * @param timeout The timeout to use. Valid timeout values are:
     *                any int >0, {@link #NO_TIMEOUT_BLOCK} and
     *                {@link #NO_TIMEOUT_QUICK}.
     * @param id      If null, the next response is returned as normal. If non-null the given id is
     *                verified to match the next response's id, and if they don't match an
     *                {@code IOException} is raised.
     * @return For NO_TIMEOUT_BLOCK: A valid response. For {@code timeout > 0} or NO_TIMEOUT_QUICK:
     * a ResponseMessage, or null if no message was available.
     * @throws IOException          Two cases:
     *                              1. If there are no messages left to read and the Connector is
     *                              closed;
     *                              2. In case of some other error during read
     * @throws InterruptedException If the thread is interrupted during a blocking read.
     */
    @Nullable
    private ResponseMessage receiveResponse(
            @NonNull InterfaceType nad,
            long timeout,
            @Nullable Integer id
    ) throws IOException, InterruptedException {

        // the reason for the different names, rather than just using overloads, is that
        // java's implicit promotion means that choosing between the Timeout and ID version
        // is bug-prone as the different is subtle (int/long).

        if (timeout != NO_TIMEOUT_BLOCK && timeout != NO_TIMEOUT_QUICK && timeout < 0L) {
            throw new IllegalArgumentException("timeout must be greater-than 0");
        }

        LOGGER.trace("receiveResponse({}, {}, {})", nad, timeout, id);
        validateSessionIsActive();

        LinkedBlockingQueue<PollerMessage> queue = mQueues.get(nad);

        if (!isConnected()) {
            LOGGER.trace("receiveResponse: !isConnected()");
            // don't want to abort too early -- still might be messages available to return.
            PollerMessage msg = queue.peek();
            if (msg == null || msg.response == null) {
                close();
                throw new IOException("Connector is not connected");
            }
        }

        // mPreviousCommandId is the only thing shared between sendCommandAPDU and receiveResponse
        // Currently mPreviousCommandId is only ever incremented in send and only read as
        // an upper bound in receive, so there shouldn't be a problem when sending and receiving
        // from multiple threads at the same time, as MpiClientMTAbort does.
        if (mPreviousSolicitedResponseId >= mPreviousCommandId) {
            // Though it would take around 500 days at 20ms per message to hit this limit
            String msg = "Trying to read unsolicited response but there"
                    + " are no outstanding commands";
            close();
            throw new IOException(msg);
        }

        final int nextExpectedId = mPreviousSolicitedResponseId + 1;
        LOGGER.trace("receiveResponse: nextExpectedId={}", nextExpectedId);
        if (id != null && id != nextExpectedId) {
            /*
                For now the client is limited to requesting the next id,
                    mainly because I'm not sure what should happen to the
                    IDs between the current ID and their ID.
                Also by limited the app to the nextExpectedId we make the later logic simpler
            */
            close();
            throw new IOException("id != nextExpectedId");
        }

        PollerMessage msg;
        try {
            if (timeout == NO_TIMEOUT_BLOCK) {
                msg = queue.take();
            } else if (timeout == NO_TIMEOUT_QUICK) {
                msg = queue.poll();
            } else {
                msg = queue.poll(timeout, TimeUnit.MILLISECONDS);
            }
            LOGGER.trace("receiveResponse: msg={}", msg);
            if (msg == null) {
                return null;
            }
        } catch (InterruptedException exception) {
            close();
            throw exception;
        }

        LOGGER.trace("receiveResponse: msg.response={}", msg.response);
        if (msg.response == null) {
            // A null response signifies the end-of-queue.
            // assert solicitedResponseId == mPreviousSolicitedResponseId
            close();
            throw new IOException("Input ResponseMessage queue closed");
        }

        LOGGER.trace("receiveResponse: msg.solicitedResponseId={}", msg.solicitedResponseId);
        // this comparison avoids roll-over issues and covers all out-of-sync conditions
        if (msg.solicitedResponseId != nextExpectedId) {
            close();
            throw new IOException("Inconsistent queue producer and consumer?");
        }

        LOGGER.trace(
                "receiveResponse: mPreviousSolicitedResponseId=nextExpectedId={}",
                nextExpectedId);
        mPreviousSolicitedResponseId = nextExpectedId;
        return msg.response;
    }

    /**
     * Start the {@code InputResponsePoller} for this session.
     *
     * Third part of the startup sequence. {@link #open()} is next.
     *
     * <p> The session must be in the startup phase (i.e. not yet open) and the Connector connected.
     * <p> Only a single InputPoller + Thread can be associated with this session -- i.e. this
     * can only be called once for each session.
     *
     * <p> The thread started here will be terminated when {@link #close} is called.
     *
     * <p> In case of an error, {@link #close} is called.
     *
     * @return true if the thread started ok, false otherwise.
     */
    private boolean startInputPollerThread() {

        try {
            validateSessionInStartup();
        } catch (IOException ignore) {
            LOGGER.trace("Session not in startup phase?!");
            return false;
        }

        if (mPollerThread != null) {
            throw new AssertionError("mPollerThread already set? " + mPollerThread.toString());
        }

        final CountDownLatch pollerStartedSignal = new CountDownLatch(1);

        PollerStatusCallback pollerStatusCallback = new PollerStatusCallback() {
            @Override
            public void handle(PollerStatus status, int lastHandledID) {
                updatePollerStatus(status, lastHandledID);
                pollerStartedSignal.countDown(); // on every call? Or should it just be on start?
            }
        };

        if (!mConnector.isConnected()) {
            close();
            //throw new IOException("Connector is not connected");
            return false;
        }

        InputStream inputStream;
        try {
            inputStream = this.mConnector.getInputStream();
        } catch (IOException ignore) {
            close();
            return false;
        }

        ResponseReader reader = new ResponseReader(inputStream);
        InputResponsePoller pollerRunnable = new InputResponsePoller(
                reader, this.mQueues,
                this.mUnsolicitedResponseCallback, pollerStatusCallback,
                100L, TimeUnit.MILLISECONDS
        );
        mPollerThread = new Thread(pollerRunnable);
        mPollerThread.setName("InputResponsePoller");

        // Wait for the Thread to call pollerStatusCallback. Not /entirely/ necessary,
        // but useful as it allows us to "ensure" mInputPollerIsActive is set before
        // the client does anything.
        mPollerThread.start();
        awaitSignal(pollerStartedSignal, "InputResponsePoller thread starting");

        if (!mInputPollerIsActive.get()) {
            // I can't imagine anything going wrong between Thread.start and the started callback,
            // but in-case it does...
            close();
            throw new AssertionError(
                    "We got a signal from Response poller thread,"
                            + " but mInputPollerIsActive == false ??");
            // return false;
        }
        return true;
    }

    /**
     * Create and open a new {@code MpiProtocolSession} and attach an {@code InputResponsePoller}.
     *
     * @param connector                   The Connector the MpiProtocolSession belongs to. The
     *                                    MpiProtocolSession should be
     *                                    the only thing reading/writing to it and will call
     *                                    {@link Connector#disconnect(MpiProtocolSession)} as appropriate.
     *                                    The Connector should already be connected.
     * @param unsolicitedResponseCallback The method to call whenever a <b>unsolicited</b>
     *                                    ResponseMessage is read from the Connector.
     *                                    Note that callbacks will happen in a different thread
     *                                    of execution than the one calling this function.
     * @param connectionStateCallback     The method to call whenever a connection event occurs,
     *                                    i.e. when the session connects and disconnects.
     *                                    Can be called from multiple threads.
     * @return In case of an error, null is returned. Otherwise, if everything started ok, a
     * new, opened, valid MpiProtocolSession is returned.
     */
    @Nullable
    static MpiProtocolSession makeMpiProtocolSession(
            @NonNull Connector connector,
            @NonNull UnsolicitedResponseCallback unsolicitedResponseCallback,
            @NonNull ConnectionStateCallback connectionStateCallback
    ) {
        EnumMap<InterfaceType, LinkedBlockingQueue<PollerMessage>> queues =
                new EnumMap<>(InterfaceType.class);
        for (InterfaceType nad : InterfaceType.values()) {
            queues.put(nad, new LinkedBlockingQueue<PollerMessage>(3));
        }

        MpiProtocolSession session = new MpiProtocolSession(
                connector, unsolicitedResponseCallback, connectionStateCallback, queues);

        try {
            if (session.startInputPollerThread()) {
                if (session.open()) {
                    return session;
                } else {
                    LOGGER.debug("session.open failed");
                }
            } else {
                LOGGER.debug("session.startInputPollerThread failed");
            }
        } catch (Throwable exception) {
            LOGGER.debug("makeMpiProtocolSession:", exception);
        }
        session.close();
//            throw new AssertionError(ignore);
        return null;
    }

    /**
     * Wait for a CountDownLatch to hit zero.
     *
     * @param signal latch to wait on
     * @param what   A human readable string of what we're waiting for
     */
    private static void awaitSignal(CountDownLatch signal, String what) {
        LOGGER.trace("Session awaitSignal for '{}'", what);
        try {
            // use a guarded block to wait for a signal
            boolean signaledInTime = signal.await(500L, TimeUnit.MILLISECONDS);

            if (!signaledInTime) {
                throw new AssertionError(String.format("No timely signal for %s?!", what));
                // return false;
            }
            LOGGER.trace("Session awaitSignal completed");
        } catch (InterruptedException ignore) {
            /*
                This should never happen. CountDownLatch.await() states it will
                only throw this exception if another thread explicitly .interrupt()s
                this one, and nothing is designed to do that.

                Also, we shouldn't suffer the usual spurious wakeups you get from Object.await()
                http://stackoverflow.com/a/31574129/
            */
            String err = "InterruptedException on signal.await()?";
            throw new AssertionError(err);
            // return false;
        }
        // return true;
    }
}


