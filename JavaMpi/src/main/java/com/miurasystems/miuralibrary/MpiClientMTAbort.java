/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.api.objects.BatteryData;
import com.miurasystems.miuralibrary.api.objects.Capability;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.api.utils.SerialPortProperties;
import com.miurasystems.miuralibrary.comms.CommandApdu;
import com.miurasystems.miuralibrary.comms.ConnectionStateCallback;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;
import com.miurasystems.miuralibrary.comms.ResponseMessage;
import com.miurasystems.miuralibrary.comms.UnsolicitedResponseCallback;
import com.miurasystems.miuralibrary.enums.BacklightSettings;
import com.miurasystems.miuralibrary.enums.CashDrawer;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.OnlinePINError;
import com.miurasystems.miuralibrary.enums.RKIError;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.enums.SelectFileMode;
import com.miurasystems.miuralibrary.enums.StatusSettings;
import com.miurasystems.miuralibrary.enums.SystemLogMode;
import com.miurasystems.miuralibrary.enums.TransactionResponse;
import com.miurasystems.miuralibrary.enums.TransactionType;
import com.miurasystems.miuralibrary.events.MpiEvents;
import com.miurasystems.miuralibrary.tlv.BinaryUtil;
import com.miurasystems.miuralibrary.tlv.TLVObject;
import com.miurasystems.miuralibrary.tlv.TLVParser;
import com.miurasystems.miuralibrary.tlv.Track2Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.Exchanger;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Extends MpiClient to provide support for mid-transaction aborts via multi-threading.
 *
 * <p>
 * MpiClient can only send an abort after startTransaction has returned (e.g. whilst the PED
 * is waiting for a continueTransaction). This class extends MpiClient to make it "abort aware"
 * and allows an abort to be sent concurrently via another thread
 * before startTransaction has returned.
 * This is useful if a transaction might be a "blocking" one (e.g. asking for PIN entry)
 * but also needs to be cancelable by the app and the app doesn't want to go to the effort
 * of fully using multi-threading for any other part of its use of MpiClient.
 * </p>
 *
 * <h4>Threading and Race conditions</h4>
 * <p>
 * Note that MpiClient offers some simple multi-threading protection for non-transaction and
 * non-abort methods, but this is very rudimentary in the sense that it only prevents
 * two methods from concurrently executing. It offers no guarantees on
 * the ordering of commands sent to the device if multiple threads decide to try and all
 * send commands at once.
 * </p>
 *
 * <p>
 * <b>
 * Additionally, be aware there is an implicit race condition when sending aborts to the PED from
 * the SDK.
 * </b>
 * Just because {@link #abortTransaction(InterfaceType)} was called it doesn't mean that the
 * transaction will be aborted, it's still possible for it to succeed before the PED acknowledges
 * the abort. And just because the startTransaction command returns
 * error with {@link TransactionResponse#USER_CANCELLED} it doesn't mean that the abort caused
 * that -- the user could have pressed the 'cancel' key etc.
 * </p>
 *
 * <p>Additionally, apps need to be wary about calling commands, or even things like
 * closeSession, before it deals with its abort and transaction threads. If a session is closed
 * in the abort thread, don't expect the transaction thread to be happy!
 * </p>
 *
 * <h4>More details</h4>
 *
 * <p>
 * {@link MpiClient}, and the underlying {@link MpiProtocolSession}, aren't thread safe.
 * Both classes are abstractions over a single communication channel to a Miura Device, and that
 * communication channel works on a series of stateful commands that change the device's state.
 * Each command sent to a Miura Device has an expected response,
 * and whilst commands can be 'queued up' in the communications pipe
 * the Miura Device won't process a command until it has sent a response to the previous command.
 * </p>
 * <p>
 * Therefore, even if those classes <i>were</i> thread-safe,
 * an app sending commands from multiple threads
 * would still be required to use the classes in a synchronised manner,
 * otherwise the sequence of commands read by the PED would be chaotic and the app's threads
 * would not know which response goes with which command.
 * From MpiClient's point of view, the app synchronising access to MpiClient's command is
 * effectively the same as calling it all from a single thread, with serialised access to the
 * device communication.
 * </p>
 *
 * <p> <b>There is an exception to this, however, and that is aborting</b>.
 * From the MPI connection's point-of-view the only time it ever makes sense to overlap
 * commands & responses is when there is a need to abort a transaction.
 * </p>
 *
 * <h4>Why aborts are different</>
 * <p>
 * If a transaction is started but not completed (e.g. via {@link #startTransaction},
 * which then waits for a person to type in a pin), then MpiClient will not return
 * until a ResponseMessage is read from the PED, and the PED will not send a ResponseMessage
 * to the SDK until the EMV Kernel handling the transaction says it is finished/needs more data
 * from the app.
 * </p>
 *
 * <p>
 * Unlike with non-transaction the PED is constantly listening out for an ABORT command
 * whenever it is processing a transaction. If it receives an ABORT before the transaction
 * has returned then it will cancel that transaction, response to the ABORT first and <b>then</b>
 * respond to the transaction with the appropriate error code.
 * </p>
 *
 * <p>
 * So in this case the thread that called startTransaction will be blocking for
 * a very long time, and as it's blocked it can't send an ABORT. If the app wants to abort at this
 * point (and therefore free up the blocked thread) it needs some way to interrupt the thread
 * and also send the PED an abort, which it can only do via another thread.
 * </p>
 * <p>
 * So MpiClientMTAbort exists to offer the multi-threaded/mid-transaction abort functionality.
 * </p>
 */
public final class MpiClientMTAbort extends MpiClient {

    /*
     * Synchronisation implementation details
     * ---------------------------------------
     *
     * The class uses mSemaphore with a permit count of 3.
     * Notionally, one permit is for "sending", one is for "receiving",
     * and one is for "exclusive access". (Or even: a count of one for
     * sending, a count of two for receiving, a count of 3 for mutex).
     *
     * All methods, with the exception of abort and transaction methods,
     * will acquire all 3 permits as an atomic action
     * (blocking until they have them) and atomically release all 3 once they're done.
     * In this way "normal" methods treat the semaphore as a binary semaphore, aka a
     * mutual exclusion lock, and take no efforts to interleave with each other.
     *
     * If an app is silly enough to try and launch commands from multiple threads then
     * they'll all hit the mutex and they'll all execute in whatever order the mutex likes.
     * No effort is put in to solve that problem.
     *
     * Transaction methods take all 3 permits, but will return the "send" permit
     * once the transaction command has been sent. After it has received a response,
     * if the sent permit was taken by something else, then it will also return the "receive"
     * permit.
     * Transaction methods will only return the "exclusive access" permit once they've completely
     * finished.
     *
     * Abort is special in that it tries to take as many permits as it can and then figures out the
     * situation from there.
     * If it could take all 3 it's just a "normal" command that's not interrupting anything.
     * If it got just enough for the "send" permit, but not enough for "exclusive access"
     * then it's interrupting a transaction in another thread.
     * (Therefore an Abort doesn't actually need the exclusive access permit. As long as it's
     *  holding at least one permit all other threads will be blocked)
     *
     * It won't ever get *just* enough for send+receive without
     * actually getting all 3, as transaction methods will only release 'receive' if something else
     * took the send permit. (Why? If the abort can take a 'receive' permit straight away
     * then it's too late to do any mid-transaction aborting!
     * So we might as well treat it as a normal '3-permit' method)
     *
     * In this way the abort and transaction methods operate in a kind of lock-stepped
     * "co-routine" fashion.
     *
     * Whilst race conditions are prevented in the SDK, race conditions still exist in the PED.
     * i.e. the user might mash the "cancel" button moments before the ABORT command arrives.
     * Due to this situation the SDK can never truly "know" if the response it got is for
     * ABORT or for the outstanding transaction command, and so must always check
     * which command is which and perform a synchronised data exchange between thread.
     *
     * Timing diagram illustrating the race condition in the PED and the reason for exchange:
     *  (In this example ABORT is seen first in the race condition)
     *
     *        SDK System                   ║║                  Miura Device
     *  ═══════════════════════════════════╬╬════════════════════════════════════════════════
     * ┌───────┐        ┌────────────┐     ║║   ┌────────────┐              ┌──────────┐
     * │ Main  │        │Abort thread│     ║║   │ Event Loop │              │EMV Kernel│
     * └───┬───┘        └─────┬──────┘     ║║   └─────┬──────┘              └────┬─────┘
     *    ┌┴┐           START_TRANSACTION  ║║        ┌┴┐                         │
     *    │ │───────────────────────────────────────>│┌┴┐ Read Command           │
     *    │ │                 │            ║║        ││ │                        │
     *    │ │                 │            ║║        ││ │                        │
     *    │ │                 │            ║║        ││ │ Start EMV Transaction  │
     *    │ │                 │            ║║        │└┬┴──────────────────────>┌┴┐
     *    │ │                 │            ║║        │ │                        │ │ Ask User
     *    │ │                 │            ║║        │ │                        │ │   for Input
     *    │ │                 │            ║║        │ │                        │ ├───────────>
     *    └┬┘                 │            ║║        └┬┘                        └┬┘
     *     .                  .            ║║         .                          .
     *     .                  .         seconds later .                          .
     *     .                  .            ║║         .                          .
     *    ┌┴┐       Abort ──>┌┴┐           ║║        ┌┴┐                        ┌┴┐ Cancel
     *    │ │       started  │ │           ║║        │ │                        │ │    pressed
     *    │ │       by app   │ │           ║║        │ │                        │ │<─ ─ ─ ─ ─ ─
     *    │ │                │ │           ║║        │ │                        │ │
     *    │ │                │ │        ABORT        │ │   Set 'Cancel' flag    \ /
     *    │ │                │ ├────────────────────>│ │ <─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │X│
     *    │ │                │ │           ║║        │ │                        /┬\
     *    │ │                │ │           ║╔═════════════════════╗              │
     *    │ │                │ │           ║║ **Race condition!** ║              │
     *    │ │                │ │           ║╚═════════════════════╝              │
     *    │ │                │ │           ║║        │┌┴┐                        │
     *    │ │                │ │   ABORT response    ││ │ Read Command           │
     *    │ │                │ │<─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─└┬┘                        │
     *    │ │                │ │           ║║        │┌┴┐                        │
     *    │ │        START_TRANSACTION response      ││ │ Notice Cancel flag     │
     *    │ │ <─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─└┬┘                        │
     *    │ │    Exchange    │ │           ║║        │ │                         │
     *    │ │<──────────────>│ │           ║║        │ │                         │
     * ┌──┴─┴──┐        ┌────┴─┴─────┐     ║║   ┌────┴─┴─────┐              ┌────┴─────┐
     * │ Main  │        │Abort Thread│     ║║   │ Event Loop │              │EMV Kernel│
     * └───────┘        └────────────┘     ║║   └────────────┘              └──────────┘
     *                                     ║║
     *
     */

    /** Maximum number of permits {@link #mSemaphore} will use. */
    private static final int NUM_PERMITS_MAX = 3;

    /**
     * How many permits a "normal" method will acquire.
     * <p>
     * Normal method means not-an-abort or not-a-transaction
     * </p>
     */
    private static final int PERMITS_NORMAL_METHOD = NUM_PERMITS_MAX;

    /** How many permits a transaction will acquire */
    private static final int PERMITS_TRANSACTION = NUM_PERMITS_MAX;

    /** How many permits a transaction releases after it has sent */
    private static final int PERMITS_TRANSACTION_RELEASE_AFTER_SEND = 1;

    /** How many permits a transaction releases after it has received */
    private static final int PERMITS_TRANSACTION_RELEASE_AFTER_RECEIVE = 1;

    /** How many permits an abort needs to be holding in order to send */
    private static final int PERMITS_ABORT_NUM_TO_SEND = PERMITS_TRANSACTION_RELEASE_AFTER_SEND;

    /** How many permits an abort needs to be holding in order to receive */
    private static final int PERMITS_ABORT_NUM_TO_RECEIVE =
            PERMITS_TRANSACTION_RELEASE_AFTER_SEND +
                    PERMITS_TRANSACTION_RELEASE_AFTER_RECEIVE;

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MpiClientMTAbort.class);

    /**
     * The semaphore used to synchronise control to the session and ensure that
     * aborts can happen even when a transaction is currently blocking to receive.
     * <p>
     * Specifically initialised in non-fair mode to allow an abort the _chance_ to jump
     * to the head of the queue if there were multiple things waiting on the Semaphore.
     * </p>
     * <p>
     * See {@link #abortTransaction(InterfaceType)} for more information.
     * </p>
     */
    @NonNull
    private final Semaphore mSemaphore = new Semaphore(NUM_PERMITS_MAX, false);

    /**
     * The synchronisation object that the "abort thread" and the "transaction thread" will
     * use to swap ResponseMessages, if each thread received the other thread's response.
     */
    @NonNull
    private final Exchanger<ResponseMessage> mExchanger = new Exchanger<>();

    /**
     * Flag set when an {@link #abortTransaction} is currently in the sending process.
     * <p>
     * Used to prevent any ABORT commands that might be arriving at the same time
     * from stealing each other's permits and therefore deadlocking their respective threads.
     * </p>
     *
     * <p>
     * (Those concurrent aborts would also be useless -- the PED will abort what it's doing on the
     * first one and therefore there's nothing to abort for the others)
     * </p>
     */
    @NonNull
    private final AtomicBoolean mAbortSending = new AtomicBoolean(false);

    /**
     * Create a new MpiClientMTAbort on the given connector that can handle mid-transaction aborts.
     *
     * @param connector Uses the given Connector to interact with the Miura device at the other
     *                  end. This client now <b>exclusively</b> "owns" this Connector
     *                  (and therefore the underling data link).
     *                  <b>Do not</b> interact with the Connector or data-link after
     *                  constructing an MpiClient with it.
     * @param mpiEvents The MpiEvents object to publish events to.
     *                  Events will only begin to arrive whilst a session is active.
     *                  See {@link #openSession()}
     */
    public MpiClientMTAbort(@NonNull Connector connector, @NonNull MpiEvents mpiEvents) {
        super(connector, mpiEvents);
    }

    @Override
    public void openSession() throws IOException {

        try {
            boolean acquired = mSemaphore.tryAcquire(NUM_PERMITS_MAX, 250L, TimeUnit.MILLISECONDS);
            if (!acquired) {
                String format = String.format(Locale.ENGLISH,
                        "openSession: Couldn't get up all permits! Only %d available?",
                        mSemaphore.availablePermits());
                LOGGER.warn(format);
                throw new AssertionError(format);
            }
        } catch (InterruptedException e) {
            LOGGER.trace("InterruptedException in openSession!");
            throw new IOException(e);
        }

        try {
            super.openSession();
        } finally {
            mSemaphore.release(NUM_PERMITS_MAX);
        }
    }

    @Override
    public void closeSession() {

        LOGGER.trace("closeSession. permits: {} aborting: {}",
                mSemaphore.availablePermits(), mAbortSending);
        try {
            boolean acquired = mSemaphore.tryAcquire(NUM_PERMITS_MAX, 1500L, TimeUnit.MILLISECONDS);
            if (!acquired) {
                String format = String.format(Locale.ENGLISH,
                        "closeSession: Couldn't clean up all permits! Only %d available?",
                        mSemaphore.availablePermits());
                LOGGER.warn(format);
                throw new AssertionError(format);
            }
        } catch (InterruptedException e) {
            LOGGER.trace("InterruptedException in closeSession!");
            throw new AssertionError(e);
        }

        LOGGER.trace("closeSession: permits acquired");
        try {
            super.closeSession();
        } finally {
            mSemaphore.release(NUM_PERMITS_MAX);
        }
    }

    /**
     * Acquire permits from the semaphore
     *
     * <p>
     * Intended to be used by any method that isn't abortTransaction.
     * </p>
     *
     * @param permits Number of permits to acquire
     * @throws InterruptedException If the permit acquisition is interrupted
     */
    private void lockNonAbort(int permits) throws InterruptedException {
        LOGGER.trace("acquiring permits for non-Abort");

        boolean acquired = mSemaphore.tryAcquire(permits);
        if (!acquired) {
            LOGGER.warn("Couldn't acquire permits. "
                    + "App is using multiple MpiClientMTAbort methods at once");

            try {
                mSemaphore.acquire(permits);
            } catch (InterruptedException e) {
                LOGGER.trace("InterruptedException!");
                throw e;
            }
        }
        LOGGER.trace("permits acquired");
    }

    /**
     * Release permits on the semaphore
     *
     * <p>
     * Intended to be used by any method that isn't abortTransaction.
     * </p>
     *
     * @param permits number of permits to release.
     */
    private void unlockNonAbort(int permits) {
        mSemaphore.release(permits);
    }

    /**
     * Swap ResponseMessages between the 'abort' thread and the 'transaction' thread.
     *
     * <p>
     * Should only be called if both threads "know" the other one exists and
     * that it is also willing to swap messages.
     * </p>
     *
     * @param rm The message this thread will pass to the other thread.
     * @return The message the other thread passed to this thread.
     * @throws TimeoutException If the swapping times out or fails in some manner for this thread.
     */
    @SuppressWarnings("ObjectToString")
    @NonNull
    private ResponseMessage swapMessages(@NonNull ResponseMessage rm) throws TimeoutException {
        LOGGER.trace("swapMessages({})", rm);

        ResponseMessage swappedRm = null;
        try {
            // This may seem long, but it can sometimes take a second for abort to get a response
            swappedRm = mExchanger.exchange(rm, 2000L, TimeUnit.MILLISECONDS);
            LOGGER.trace("swapMessages: mExchanger.exchange(...) = {}", swappedRm);
        } catch (InterruptedException | TimeoutException e) {
            LOGGER.trace("swapMessages: timed out?!", e.toString());
        }
        if (swappedRm == null) {
            String message = "swapMessages timed out for " + Thread.currentThread().getName();
            throw new TimeoutException(message);
        }

        LOGGER.trace("swapMessages returns {}", swappedRm);
        return swappedRm;
    }


    /**
     * Send a transaction command in a manner that still allows
     * {@link #abortTransaction(InterfaceType)} to also send a command.
     *
     * <p> See javadoc of MpiClientMTAbort for more information</p>
     *
     * @param interfaceType Which device to send to and receive from
     * @param command       The command to send
     * @param permitsUsed   The number of permits acquired. Should be enough to release one for
     *                      send, receive and still retain one.
     * @return Returns the ResponseMessage for the transaction.
     * Or returns null if there was an error reading the Response from the device, or if
     * an abort ResponseMessage was found but it was unable to be swapped with the abort thread.
     */
    @Nullable
    private ResponseMessage sendAndReceiveAbortAware(
            @NonNull InterfaceType interfaceType,
            @NonNull CommandApdu command,
            @NonNull MutableInt permitsUsed
    ) {
        if (permitsUsed.value <=
                (PERMITS_TRANSACTION_RELEASE_AFTER_SEND +
                        PERMITS_TRANSACTION_RELEASE_AFTER_RECEIVE)) {
            throw new AssertionError(
                    "Not enough permits to perform transaction and still hold exclusive lock?!");
        }

        LOGGER.trace("sendAndReceiveTransaction");
        if (mSemaphore.availablePermits() != 0) {
            // We should have all the permits at this point
            throw new AssertionError("mSemaphore.availablePermits() != 0");
        }

        int id = sendCommand(interfaceType, command);
        LOGGER.trace("sendAndReceiveTransaction sent command id = {}, releasing permit", id);

        /*
            Release enough permits to let the abort send its command.

            The abort thread doesn't have enough permits to receive a response, however,
            so it has to wait for us to receive a response before it can.

            There's a window of opportunity before releasing this permit and claiming it
            back later. The length of this window depends on the amount of time spent in
            receiveResponse. If there's some kind of "user action" required on the PED, e.g.
            typing in a PIN, then receiveResponse can potentially block "forever".
        */
        mSemaphore.release(PERMITS_TRANSACTION_RELEASE_AFTER_SEND);
        permitsUsed.value -= PERMITS_TRANSACTION_RELEASE_AFTER_SEND;
        ResponseMessage rm = receiveResponse(interfaceType);
        LOGGER.trace("sendAndReceiveTransaction, receiveResponse: {}", rm);
        if (rm == null) {
            // It's ok to return here without causing a deadlock or anything in the abort
            // thread, as this method's callers should all release permitsUsed.value in all cases
            // Should the abort thread receive a message and it needs exchanging then that's
            // ok as well, as it will time out in swapMessages()
            return null;
        }

        /*
            We want to know if an abort was sent and do so without any race conditions
            between threads.
                mAbortSending = big potential for race condition,
                mSomeOtherBooleanSetAfterAnAbortSends = subtle race condition
                checking available permits = TOCTOU problem

            So claim back the permit we gave out before receiveResponse.
                * If the abort thread has already claimed it, then we'll fail
                * If the abort thread has yet to claim it, then we'll get it back
            Only the abort method tries to claim a single permit at a time, and there should
            only be one abort thread in that method at a time.
            No other situation should be possible. Therefore, _if_ an abort thread exists
            its state will be either of:
                1. Before claiming the send permit, which it now can't do as we just took that
                    permit back
                2. After claiming the send permit (and has sent/is sending a command),
                    but before acquiring the receive permit.
                    It can't claim the receive permit until we release it.
        */
        LOGGER.trace("sendAndReceiveTransaction re-aquiring send permit");
        boolean acquired = mSemaphore.tryAcquire(PERMITS_TRANSACTION_RELEASE_AFTER_SEND);
        LOGGER.trace("sendAndReceiveTransaction: acquired = {}", acquired);

        if (acquired) {
            /* Acquired means there's no concurrent abort thread */
            permitsUsed.value += PERMITS_TRANSACTION_RELEASE_AFTER_SEND;
        } else {
            /* We didn't acquire the permit, which means the abort thread still has it.
               The abort thread will be waiting for the receive permit, so supply it.
           */
            mSemaphore.release(PERMITS_TRANSACTION_RELEASE_AFTER_RECEIVE);
            permitsUsed.value -= PERMITS_TRANSACTION_RELEASE_AFTER_RECEIVE;
            LOGGER.trace(
                    "sendAndReceiveTransaction released permits for abort's receive. "
                            + "permitsUsed = {}",
                    permitsUsed.value);

            /*
                Even though we _know_ an abort was sent both threads must still examine
                their responses and switch **if necessary**.
                We can never be sure if the PED saw the abort before returned
                the transaction response.
            */
            AbortTransactionExchangeType ourType = AbortTransactionExchangeType.TRANSACTION;
            final AbortTransactionExchangeType messageType = guessMessageRecipient(rm);
            boolean doSwap = ourType != messageType;
            if (doSwap) {
                LOGGER.trace("sendAndReceiveTransaction: swapping: {}", rm);
                try {
                    rm = swapMessages(rm);
                } catch (TimeoutException ignore) {
                    LOGGER.trace("sendAndReceiveTransaction: timed out");
                    return null;
                }
            }
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "sendAndReceiveTransaction: got from swap: {}, success: {}, data:{}",
                    rm,
                    String.format("0x%04X", rm.getStatusCode()),
                    BinaryUtil.parseHexString(rm.getBody())
            );
            LOGGER.trace(
                    "sendAndReceiveTransaction returning. used: {}, availablePermits: {}",
                    permitsUsed.value, mSemaphore.availablePermits());
        }
        return rm;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Unlike the method this overrides (MpiClient#startTransaction),
     * this method supports mid-transaction aborts via another thread using the
     * {@link #abortTransaction(InterfaceType)} command.
     * </p>
     *
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p> <b>The exception to this</b> is that this method is "abort aware" and therefore allows
     * {@link #abortTransaction(InterfaceType)} to be sent concurrently from another Thread
     * <i>after</i> this method has started execution and before it <i>receives</i> a Response.
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @NonNull
    @Override
    public Result<byte[], TransactionResponse> startTransaction(
            @NonNull InterfaceType interfaceType,
            @NonNull TransactionType transactionType,
            int amountInPennies,
            int currencyCode
    ) {
        MutableInt permitsUsed = new MutableInt(PERMITS_TRANSACTION);
        try {
            lockNonAbort(permitsUsed.value);
        } catch (InterruptedException ignore) {
            return processTransactionResponse(null);
        }

        LOGGER.trace("startTransaction");
        try {
            CommandApdu command = makeStartTransactionCommand(
                    transactionType, amountInPennies, currencyCode);
            ResponseMessage rm = sendAndReceiveAbortAware(interfaceType, command, permitsUsed);
            return processTransactionResponse(rm);
        } finally {
            unlockNonAbort(permitsUsed.value);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Unlike the method this overrides (MpiClient#startContactlessTransaction),
     * this method supports mid-transaction aborts via another thread using the
     * {@link #abortTransaction(InterfaceType)} command.
     * </p>
     *
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p> <b>The exception to this</b> is that this method is "abort aware" and therefore allows
     * {@link #abortTransaction(InterfaceType)} to be sent concurrently from another Thread
     * <i>after</i> this method has started execution and before it <i>receives</i> a Response.
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @NonNull
    @Override
    public Result<byte[], TransactionResponse> startContactlessTransaction(
            @NonNull InterfaceType interfaceType,
            @NonNull TransactionType transactionType,
            int amountInPennies,
            int currencyCode,
            @Nullable String languagePreference
    ) {
        MutableInt permitsUsed = new MutableInt(PERMITS_TRANSACTION);
        try {
            lockNonAbort(permitsUsed.value);
        } catch (InterruptedException ignore) {
            return processTransactionResponse(null);
        }

        LOGGER.trace("startContactlessTransaction");
        try {
            CommandApdu command = makeStartContactlessTransactionCommand(
                    transactionType, amountInPennies, currencyCode, languagePreference);
            ResponseMessage rm = sendAndReceiveAbortAware(interfaceType, command, permitsUsed);
            return processTransactionResponse(rm);
        } finally {
            unlockNonAbort(permitsUsed.value);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Unlike the method this overrides (MpiClient#continueTransaction),
     * this method supports mid-transaction aborts via another thread using the
     * {@link #abortTransaction(InterfaceType)} command.
     * </p>
     *
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p> <b>The exception to this</b> is that this method is "abort aware" and therefore allows
     * {@link #abortTransaction(InterfaceType)} to be sent concurrently from another Thread
     * <i>after</i> this method has started execution and before it <i>receives</i> a Response.
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @NonNull
    @Override
    public Result<byte[], TransactionResponse> continueTransaction(
            @NonNull InterfaceType interfaceType,
            @NonNull TLVObject transactionInfo
    ) {
        MutableInt permitsUsed = new MutableInt(PERMITS_TRANSACTION);
        try {
            lockNonAbort(permitsUsed.value);
        } catch (InterruptedException ignore) {
            return processTransactionResponse(null);
        }

        LOGGER.trace("continueTransaction");
        try {
            byte[] dataField = TLVParser.encode(transactionInfo);
            CommandApdu command = new CommandApdu(CommandType.Continue_Transaction, dataField);
            ResponseMessage rm = sendAndReceiveAbortAware(interfaceType, command, permitsUsed);
            return processTransactionResponse(rm);
        } finally {
            unlockNonAbort(permitsUsed.value);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Unlike the method this overrides (MpiClient.onlinePin),
     * this method supports mid-transaction aborts via another thread using the
     * {@link #abortTransaction(InterfaceType)} command.
     * </p>
     *
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p> <b>The exception to this</b> is that this method is "abort aware" and therefore allows
     * {@link #abortTransaction(InterfaceType)} to be sent concurrently from another Thread
     * <i>after</i> this method has started execution and before it <i>receives</i> a Response.
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @NonNull
    @Override
    public Result<OnlinePinResult, OnlinePINError> onlinePin(
            @NonNull InterfaceType interfaceType,
            int amountInPennies,
            int currencyCode,
            @NonNull Track2Data maskedTrack2Data,
            @NonNull String applicationLabel
    ) {
        MutableInt permitsUsed = new MutableInt(PERMITS_TRANSACTION);
        try {
            lockNonAbort(permitsUsed.value);
        } catch (InterruptedException ignore) {
            return processOnlinePinResult(null);
        }

        LOGGER.trace("onlinePin");
        try {
            CommandApdu command = makeOnlinePinCommand(
                    amountInPennies, currencyCode, maskedTrack2Data, applicationLabel);
            ResponseMessage rm = sendAndReceiveAbortAware(interfaceType, command, permitsUsed);
            return processOnlinePinResult(rm);
        } finally {
            unlockNonAbort(permitsUsed.value);
        }
    }


    /**
     * Issues an ABORT command to the Miura device
     *
     * <p>
     * Causes the device to 'Exit any wait loops, cancel any transactions in progress and
     * return to the idle state keeping any communications session active'.
     * (from MPI API doc '6.34 ABORT').
     * </p>
     *
     * <p>
     * This command can be used to abort a transaction. It can be sent:
     * <ol>
     * <li>
     * mid-transaction command,
     * e.g. concurrently from Thread A whilst Thread B is currently blocking in a
     * {@link #continueTransaction} call,
     * </li>
     * <li>
     * or simply between a series of transaction commands on the same Thread.
     * </li>
     * </ol>
     * </p>
     * <p>
     * Note that if multiple ABORTS are sent concurrently then the SDK ensures that only a single
     * one will be "active" at any one time, the rest are ignored.
     * Once the "active" ABORT has been seen by the PED and responded to then this "active" ABORT
     * will no longer be considered "active" and the SDK will honour any new calls to
     * abortTransaction. See also {@link #mAbortSending}
     * </p>
     *
     * <p>See the javadoc for MpiClientMTAbort for race and threading concerns</p>
     *
     * @param interfaceType The device to send the ABORT command to.
     * @return true if the abort worked ok, false is the command failed in the SDK or on the device
     */
    @Override
    public boolean abortTransaction(@NonNull InterfaceType interfaceType) {

        LOGGER.trace("abortTransaction");

        if (!mAbortSending.compareAndSet(false, true)) {
            /*
                There's already an abort sending, which means this one would have no effect.
                So just ignore this abort. This also protects against the massive deadlocking
                hassle that is "allowing multiple aborts from multiple threads", e.g.
                each of them acquires a permit to send, which means nothing can ever give them a
                permit to receive.
            */
            LOGGER.trace("mAbortSending already set");
            return true;
        }

        final boolean returnValue;

        int numPermits = mSemaphore.drainPermits();
        if (numPermits == 0) {
            LOGGER.trace("abortTransaction: drained 0");

            // if we get 0 permits then the possibilities are:
            // either a) there's a race condition between this and a transaction,
            //          and the transaction got there first.
            // Or b) A non-transaction command is happening.
            //
            // Both are the apps fault, in a way :)
            // We can't (easily) tell the difference between A) and B) and
            // it's probably best to ensure the abort goes through anyway, even in both cases,
            // to meet with user expectations in case there actually *is* something to abort.
            //
            // So we'll just wait for permits to be available and then send the abort as normal.
            try {
                mSemaphore.acquire(PERMITS_ABORT_NUM_TO_SEND);
            } catch (InterruptedException e) {
                LOGGER.trace("abortTransaction: acquire interrupted: {}", e.toString());
                return false;
            }
            numPermits = PERMITS_ABORT_NUM_TO_SEND;
        }
        LOGGER.trace("abortTransaction: drained {}", numPermits);

        try {
            final boolean swapCheck;
            switch (numPermits) {
                case PERMITS_NORMAL_METHOD:
                    /*
                     *  If we got all the permits then we're being invoked as a normal abort
                     *  command with no "interrupting" of a transaction. If there was a
                     *  transaction then it has already read its response.
                     *
                     *  (It's also possible there's a race condition between abort and a
                     *  transaction which means the abort will happen before the transaction,
                     *  but that's the app's fault. Such a condition will probably
                     *  result in the transaction command failing to acquire permits and
                     *  throwing a ConcurrentModificationException).
                     */
                    swapCheck = false;
                    break;
                case PERMITS_ABORT_NUM_TO_SEND:
                    /*
                        If we got just enough permits to send then it means a transaction has
                        not received a reply yet.

                        Note that it's entirely possible the transaction responds
                        in the time it takes us to get from here to sendCommand,
                        and therefore the PED sees the abort as a second command rather
                        than an interrupting one.

                        So we have to "check" if we're swapping, rather than "always" swapping.
                    */
                    swapCheck = true;
                    break;
                case PERMITS_ABORT_NUM_TO_RECEIVE:
                    /*
                        This should not happen!! A transaction will only release enough
                        for an Abort to receive if it knows the abort already sent!
                     */
                    throw new AssertionError("Drained PERMITS_ABORT_NUM_TO_RECEIVE?");
                default:
                    // Shouldn't happen?
                    String format = String.format(
                            Locale.ENGLISH, "Abort drained %d permits?!", numPermits);
                    throw new AssertionError(format);
            }

            CommandApdu command = new CommandApdu(CommandType.Abort);
            LOGGER.trace("abortTransaction before sendCommand");

            int id = sendCommand(interfaceType, command);
            LOGGER.trace("abortTransaction send id={}", id);

            /*
                Wait until we have 'permission' to receive.

                If there's an outstanding transaction we let it receive first.
                This isn't to make the response-detection easier, but to avoid
                entering the receiveResponse method from multiple threads, which it can't
                handle.

                Response-detection is always required as there may have been a race condition
                in the PED's reading of the message.
            */
            int numExtraPermitsRequiredToReceive = PERMITS_ABORT_NUM_TO_RECEIVE - numPermits;
            LOGGER.trace(
                    "abortTransaction needs {} extra permit(s)", numExtraPermitsRequiredToReceive);
            if (numExtraPermitsRequiredToReceive > 0) {
                try {
                    mSemaphore.acquire(numExtraPermitsRequiredToReceive);
                } catch (InterruptedException e) {
                    LOGGER.trace("abortTransaction: acquire interrupted: {}", e.toString());
                    return false;
                }
                numPermits += numExtraPermitsRequiredToReceive;
                LOGGER.trace("abortTransaction: permits acquired");
            }

            LOGGER.trace("abortTransaction calling receiveResponse");
            ResponseMessage rm = receiveResponse(interfaceType);
            LOGGER.trace("abortTransaction response: {}", rm);
            if (rm == null) {
                // We're bailing out of the swap procedure here.
                // If it needs exchanging but doesn't exist, the other side will time out.
                return false;
            }

            // If one side figures out it needs to swap, then the other side should also,
            // otherwise guessMessageRecipient is broken.
            AbortTransactionExchangeType ourType = AbortTransactionExchangeType.ABORT;
            if (swapCheck) {
                final AbortTransactionExchangeType messageType = guessMessageRecipient(rm);
                boolean doSwap = ourType != messageType;
                if (doSwap) {
                    LOGGER.trace("abortTransaction: swapping: {}", rm);
                    try {
                        rm = swapMessages(rm);
                    } catch (TimeoutException e) {
                        return false;
                    }
                    LOGGER.trace("abortTransaction: got from swap: {}", rm);
                }
            }

            returnValue = rm.isSuccess();

            // Now acquire all of the permits. This prevents a second abort
            // If we didn't do this, and just released both permits, it's possible for a
            // second abort to arrive before the transaction thread has released its final permit,
            // so the second abort thread would end up draining the SEND and RECEIVE permits.
            //
            // Whilst it's will work for it to get both permits, it's a waste of a
            // send/receive cycle, so don't let it have the chance.
            int numExtraPermitsRequiredForExclusive = PERMITS_NORMAL_METHOD - numPermits;
            LOGGER.trace(
                    "abortTransaction needs {} extra permit(s) for EXCLUSIVE permit",
                    numExtraPermitsRequiredForExclusive
            );
            if (numExtraPermitsRequiredForExclusive > 0) {
                try {
                    mSemaphore.acquire(numExtraPermitsRequiredForExclusive);
                } catch (InterruptedException e) {
                    LOGGER.trace("abortTransaction: final acquire interrupted:{}", e.toString());
                    return returnValue;
                }
                numPermits += numExtraPermitsRequiredForExclusive;
                LOGGER.trace("abortTransaction: final permits acquired");
            }

        } finally {
            mSemaphore.release(numPermits);
        }

        if (!mAbortSending.compareAndSet(true, false)) {
            // For this to fail is impossible?
            throw new AssertionError("Failed to release mAbortSending flag");
        }

        LOGGER.trace("abortTransaction: returning {}", returnValue);
        LOGGER.trace("abortTransaction: availablePermits: {}", mSemaphore.availablePermits());
        return returnValue;
    }

    // region non-transaction methods

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public ArrayList<Capability> getDeviceInfo(@NonNull InterfaceType interfaceType) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.getDeviceInfo(interfaceType);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public Date systemClock(@NonNull InterfaceType interfaceType) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.systemClock(interfaceType);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean systemClock(@NonNull InterfaceType interfaceType, @NonNull Date dateTime) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.systemClock(interfaceType, dateTime);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean displayText(@NonNull InterfaceType interfaceType, @NonNull String text,
            boolean isFourRow, boolean isBacklightOn, boolean isUTF8Encoding) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.displayText(interfaceType, text, isFourRow, isBacklightOn, isUTF8Encoding);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public BatteryData batteryStatus(@NonNull InterfaceType interfaceType, boolean intoSleep) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.batteryStatus(interfaceType, intoSleep);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public void cardStatus(@NonNull InterfaceType interfaceType, boolean enableUnsolicited,
            boolean enableAtr, boolean enableTrack1, boolean enableTrack2, boolean enableTrack3) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException e) {
            LOGGER.warn("cardStatus failed to acquire permits:", e.toString());
        }
        try {
            super.cardStatus(interfaceType, enableUnsolicited, enableAtr, enableTrack1,
                    enableTrack2,
                    enableTrack3);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public SoftwareInfo resetDevice(@NonNull InterfaceType interfaceType,
            @NonNull ResetDeviceType type) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.resetDevice(interfaceType, type);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public HashMap<String, String> getConfiguration() {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.getConfiguration();
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean keyboardStatus(@NonNull InterfaceType interfaceType,
            @NonNull StatusSettings statusSetting, @NonNull BacklightSettings backlightSetting) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.keyboardStatus(interfaceType, statusSetting, backlightSetting);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public int selectFile(@NonNull InterfaceType interfaceType, @NonNull SelectFileMode mode,
            @NonNull String fileName) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return -1;
        }
        try {
            return super.selectFile(interfaceType, mode, fileName);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean streamBinary(@NonNull InterfaceType interfaceType, boolean needMd5sum,
            byte[] binary, int offset, int size, int timeout) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.streamBinary(interfaceType, needMd5sum, binary, offset, size, timeout);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean systemLog(@NonNull InterfaceType interfaceType, @NonNull SystemLogMode mode) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.systemLog(interfaceType, mode);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public ResponseMessage readBinary(@NonNull InterfaceType interfaceType, int fileSize,
            int offset, int size) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.readBinary(interfaceType, fileSize, offset, size);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public P2PEStatus p2peStatus(@NonNull InterfaceType interfaceType) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.p2peStatus(interfaceType);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean p2peInitialise(@NonNull InterfaceType interfaceType) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.p2peInitialise(interfaceType);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @NonNull
    @Override
    public RKIError p2peImport(@NonNull InterfaceType interfaceType) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return RKIError.RkiMiuraInternalError;
        }
        try {
            return super.p2peImport(interfaceType);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public ArrayList<String> peripheralStatusCommand() {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.peripheralStatusCommand();
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean barcodeStatus(@NonNull InterfaceType interfaceType, boolean codeReporting) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.barcodeStatus(interfaceType, codeReporting);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean spoolText(@NonNull InterfaceType interfaceType, @NonNull String text) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.spoolText(interfaceType, text);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean spoolImage(@NonNull InterfaceType interfaceType, @NonNull String fileName) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.spoolImage(interfaceType, fileName);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean spoolPrint(@NonNull InterfaceType interfaceType) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.spoolPrint(interfaceType);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean printESCPOScommand(@NonNull InterfaceType interfaceType, @NonNull String text) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.printESCPOScommand(interfaceType, text);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Deprecated
    @Override
    public boolean printText(@NonNull InterfaceType interfaceType, @NonNull String text,
            boolean wait) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.printText(interfaceType, text, wait);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Deprecated
    @Override
    public boolean printImage(@NonNull InterfaceType interfaceType, @NonNull String image) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.printImage(interfaceType, image);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public CashDrawer cashDrawer(boolean openDrawer) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.cashDrawer(openDrawer);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean printerSledStatus(@NonNull InterfaceType interfaceType,
            boolean printerSledStatusEnabled) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.printerSledStatus(interfaceType, printerSledStatusEnabled);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Nullable
    @Override
    public HashMap<String, String> getBluetoothInfo() {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return null;
        }
        try {
            return super.getBluetoothInfo();
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean setSerialPort(@NonNull SerialPortProperties serialPortProperties) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.setSerialPort(serialPortProperties);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Note:</b> This method is multi-thread safe, but only in the sense that it prevents
     * other methods from executing whilst this method is executing
     * </p>
     *
     * <p>See the javadoc of MpiClientMTAbort for more information on threading concerns</p>
     */
    @Override
    public boolean sendDataToSerialPort(@NonNull byte[] data) {
        try {
            lockNonAbort(PERMITS_NORMAL_METHOD);
        } catch (InterruptedException ignore) {
            return false;
        }
        try {
            return super.sendDataToSerialPort(data);
        } finally {
            unlockNonAbort(PERMITS_NORMAL_METHOD);
        }
    }

    // endregion the-others

    /**
     * Example the ResponseMessage and "guess" if it's for an ABORT or a TRANSACTION command.
     *
     * @param rm The response message to guess the type of
     * @return The AbortTransactionExchangeType we guessed at
     */
    @NonNull
    private static AbortTransactionExchangeType guessMessageRecipient(ResponseMessage rm) {
        /*
            The guess is somewhat "educated". Even though all commands can return undocumented
            errors (as documented in the MPI spec) an abort will never fail in the actual MPI C
            code and always returns success. Therefore:

                * success with data -> transaction
                * success without data -> abort
                * failure with successCode of (9F, valid TransactionError) -> transaction
        */
        final AbortTransactionExchangeType messageType;
        boolean isSuccess = rm.isSuccess();
        byte[] body = rm.getBody();

        if (isSuccess && body.length == 0) {
            messageType = AbortTransactionExchangeType.ABORT;
        } else {
            messageType = AbortTransactionExchangeType.TRANSACTION;
        }

        LOGGER.trace(
                "guessMessageRecipient({}), success:{}, body len:{} body:{} => {}",
                rm, isSuccess, body.length, BinaryUtil.parseHexString(body), messageType);
        return messageType;
    }

    /**
     * Which command a a ResponseMessages is responding to,
     * and therefore which command should get in an Exchange.
     */
    private enum AbortTransactionExchangeType {
        /** ResponseMessage was from the ABORT command */
        ABORT,

        /** ResponseMessage was from a TRANSACTION command */
        TRANSACTION
    }

    /**
     * An mutable int
     *
     * <p>
     * Java's Integer is immutable and this is the easiest way
     * of passing in an int as an "out" value
     * </p>
     */
    private class MutableInt {
        /** The mutable value */
        public int value;

        /**
         * Make a new MutableInt with this initial value
         *
         * @param value Initial value of the MutableInt
         */
        private MutableInt(int value) {
            this.value = value;
        }
    }

}
