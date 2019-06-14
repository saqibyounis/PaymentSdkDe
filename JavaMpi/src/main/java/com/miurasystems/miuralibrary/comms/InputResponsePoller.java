/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.comms.PollerStatusCallback.PollerStatus;
import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Used as the response from {@link InputResponsePoller#postResponseToQueue}
 *
 * <p>Used instead of a simple boolean because:
 * <ol>
 * <li>We want to do different things depending upon the error.</li>
 * <li>Exceptions are bloaty and not fitted to that sort of logic</li>
 * </ol>
 */
enum PostingStatus {
    /** Message posted to Queue with no problems */
    Ok,

    /**
     * Message timed out whilst posting to Queue
     *
     * <p> See also {@link BlockingQueue#offer(Object, long, TimeUnit)}
     */
    TimedOut,

    /**
     * An InterruptedException was caught whilst trying to post to Queue
     *
     * <p> It's expected that the caller of postResponseToQueue does everything
     * required to kill this thread swiftly, as that's what
     * InterruptedException wants.
     *
     * <p> See also {@link BlockingQueue#offer(Object, long, TimeUnit)}
     */
    InterruptedException
}

/**
 * Callback used to update someone about the InputResponsePoller's status
 */
interface PollerStatusCallback {

    /**
     * {@code handle} is called whenever the InputResponsePoller's
     * status changes
     *
     * @param status        The new status
     * @param lastHandledID The last solicited ID read.
     *                      Will be {@link PollerMessage#INITIAL_RESPONSE_ID}
     *                      if nothing has yet to be read.
     */
    void handle(PollerStatus status, int lastHandledID);

    /** The InputResponsePoller thread's status */
    enum PollerStatus {
        /**
         * The InputResponsePoller is currently running
         *
         * <p> The InputResponsePoller is currently running and actively
         * polling the ResponseReader and taking the appropriate action:
         * e.g. posting to a queue or calling the unsolicited callback.
         */
        Running,

        /**
         * The InputResponsePoller stopped as a queue timed out
         *
         * <p> The InputResponsePoller had to stop because
         * {@link InputResponsePoller#postResponseToQueue}
         * timed out.
         *
         * <p> See also {@link PostingStatus#TimedOut}
         */
        StoppedQueuePostTimedOut,

        /**
         * The InputResponsePoller was interrupted when posting to a queue
         *
         * <p> The InputResponsePoller had to stop because
         * {@link InputResponsePoller#postResponseToQueue}
         * threw an InterruptedException.
         *
         * <p> See also {@link PostingStatus#InterruptedException}
         */
        StoppedQueuePostInterrupted,

        /**
         * The InputResponsePoller stopped as the ResponseReader "broke".
         *
         * <p> The InputResponsePoller had to stop because the ResponseReader stopped
         * returning valid ResponseMessages.
         * e.g. its InputStream reached EOF, or an un-recoverable error.
         *
         * <p> See {@link ResponseReader#nextResponse()} for more
         * information on why it may break.
         */
        StoppedStreamBroken,

        /**
         * The InputResponsePoller stopped as a callback threw an exception
         *
         * See {@link PollerStatusCallback}, {@link UnsolicitedResponseCallback}
         */
        StoppedCallbackError,
    }

}

/**
 * An InputResponsePoller will continually read ResponseMessages from an
 * ResponseReader and will post solicited message to the given queues and
 * unsolicited messages to the given callback.
 *
 * <p> InputResponsePoller supports message per-NAD. Messages are posted to
 * the queue matching their NAD.
 *
 * <p> The class is a Runnable and intended to be run in a separate thread
 * from the class(es) reading the other end of the message queues. The idea is
 * that the blocking {@code .read()} calls happen in InputResponsePoller,
 * so the client isn't stuck waiting around for data until it actually needs
 * it, and then it can wait on the other end of the message queues.
 *
 * <p>It is intended to only be run once. (i.e. Thread.start() is only called
 * once)
 *
 * <p>An InputResponsePoller will run forever until:
 * <ul>
 * <li>The underlying InputStream closes, reaches EOF, or produces data such that
 * {@link ResponseReader#nextResponse()} returns null. See
 * {@link PollerStatus#StoppedStreamBroken})
 * </li>
 * <li>Any queue becomes full and cannot be posted to in the timeout period.
 * See {@link PollerStatus#StoppedQueuePostTimedOut}) and
 * {@link BlockingQueue#offer(Object, long, TimeUnit)}</li>
 * <li>The thread is interrupted whilst trying to post to any queue (See
 * {@link PollerStatus#StoppedQueuePostInterrupted})</li>
 * </ul>
 *
 * <p>Before stopping the InputResponsePoller will post
 * terminal message to all queues. See {@link PollerMessage} for more.
 *
 * <p> As such there's no direct mechanism to stop this thread running other
 * than those conditions, but in all cases those conditions are able to be
 * enacted by the constructor of the {@code InputResponsePoller} object.
 * (i.e. it has the power and responsibility to close the InputStream,
 * or stop reading from the queues)
 *
 * <p> Note: It's implemented this way because there's no need to stop an
 * InputResponsePoller until the InputStream/ResponseReader is closed.
 * All data on that InputStream should be a valid ResponseMessages
 * that can be read by {@code InputResponsePoller}.
 * And if the InputStream is closing for whatever reason then the
 * {@code InputResponsePoller} will also want closing as well.
 *
 * <p> It's also because the Android bluetooth implementation is pretty
 * poor and there's no timeout facility when reading a bluetooth sockets
 * in the Android API, even though that functionality exists at lower
 * levels of the Android implementation.
 * There's also no way to kill a tread blocked by a
 * {@link InputStream#read()}, so even <i>if</i> an external thread
 * wanted to kill an {@code InputResponsePoller} that was stuck in a
 * {@code read()} they couldn't! They'd have to .close the stream
 * anyway to avoid the zombie thread.
 *
 * <p> So closing the stream, which closes the ResponseReader,
 * is <b>the</b> way to signal to
 * an InputResponsePoller that you want it to stop.
 *
 * <p> The canonical procedure is:
 * <ol>
 * <li>Close the InputStream / break the ResponseReader. </li>
 * <li>Wait a short time, e.g. 5 ms</li>
 * <li>Call {@link Thread#interrupt()} on the thread running the
 * InputResponsePoller, in-case it's waiting whilst posting to
 * a message queue
 * </li>
 * <li>Await the status callback to confirm it dies</li>
 * </ol>
 **/
class InputResponsePoller implements Runnable {
    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(InputResponsePoller.class);

    /**
     * The ResponseReader to poll for messages on.
     * This object should be the only thing reading from it
     */
    @NonNull
    private final ResponseReader mReader;

    /**
     * Queues to post solicited ResponseMessages + IDs to.
     *
     * <p>One queue for each NAD.
     *
     * <p>The last thing we should post to the queue is
     * a terminal message (a PollerMessage with response == null)
     * to mark the end of the queue.
     *
     * <p>All queues will receive a terminal message at the same time,
     * though there is no defined order as to which queue gets it first.
     */
    @NonNull
    private final Map<InterfaceType, ? extends BlockingQueue<PollerMessage>> mQueues;

    /**
     * Callback used to pass on unsolicited ResponseMessages. (all NADs)
     *
     * <p> {@code mUnsolicitedCallback.handle} should be called whenever an
     * unsolicited message is read from the ResponseReader.
     */
    @NonNull
    private final UnsolicitedResponseCallback mUnsolicitedCallback;

    /**
     * Callback used to update the constructing object about
     * changes in status. i.e. called when we enter and exit {@link #run()}
     */
    @NonNull
    private final PollerStatusCallback mPollerStatusCallback;

    /**
     * The timeout for {@link BlockingQueue#offer(Object, long, TimeUnit)}
     */
    private final long mPostingTimeout;

    /**
     * The time unit for {@link BlockingQueue#offer(Object, long, TimeUnit)}
     */
    @NonNull
    private final TimeUnit mPostingTimeUnit;


    /**
     * Construct an InputResponsePoller
     *
     * @param reader               The ResponseReader to poll for response messages.
     *                             The InputResponsePoller now "owns" this ResponseReader
     *                             and therefore its underlying InputStream.
     *                             Don't read from it anywhere else.
     * @param queues               The queues to post ResponseMessages to.
     *
     *                             <p>There is one queue for each NAD the system
     *                             is aware of. Messages from each nad are posted
     *                             to the relevant queue.
     *
     *                             <p>Queue users: always peek and check the
     *                             message isn't a terminal message (response == null)
     *                             before actually
     *                             reading the contents of the ResponseMessage.
     * @param unsolicitedCallback  Callback called whenever an unsolicited
     *                             message is read from the ResponseReader.
     *
     *                             <p> Note: {@code handle} will run in the
     *                             thread of execution
     *                             of InputResponsePoller. It will need to
     *                             synchronise itself appropriately.
     *
     *                             <p>Also ensure you check the NAD before
     *                             taking action.
     * @param pollerStatusCallback Called whenever the status of the
     *                             InputResponsePoller changes. Importantly,
     *                             it's called when {@link #run()}
     *                             stops and starts.
     *                             <p> Note: {@code handle} will run in the thread of execution
     *                             of InputResponsePoller. It will need to synchronise itself
     *                             appropriately.
     * @param queuePostingTimeout  The timeout for {@code queue.offer() }
     *                             {@link BlockingQueue#offer(Object, long, TimeUnit)}
     * @param queuePostingTimeUnit The time unit for {@code queue.offer() }
     *                             {@link BlockingQueue#offer(Object, long, TimeUnit)}
     */
    InputResponsePoller(
            @NonNull ResponseReader reader,
            @NonNull Map<InterfaceType, ? extends BlockingQueue<PollerMessage>> queues,
            @NonNull UnsolicitedResponseCallback unsolicitedCallback,
            @NonNull PollerStatusCallback pollerStatusCallback,
            long queuePostingTimeout,
            @NonNull TimeUnit queuePostingTimeUnit
    ) {
        mReader = reader;
        mQueues = queues;

        for (InterfaceType nad : InterfaceType.values()) {
            if (!mQueues.containsKey(nad) || mQueues.get(nad) == null) {
                String s = String.format("Queue for NAD:%s missing?", nad);
                throw new IllegalArgumentException(s);
            }
        }

        mUnsolicitedCallback = unsolicitedCallback;
        mPollerStatusCallback = pollerStatusCallback;

        mPostingTimeout = queuePostingTimeout;
        mPostingTimeUnit = queuePostingTimeUnit;
    }

    /**
     * Read responses from ResponseReader and handle them.
     * See javadoc of {@link #InputResponsePoller} for more.
     */
    @SuppressWarnings("OverlyBroadCatchBlock")
    @Override
    public void run() {
        LOGGER.trace("Poller's run() started!");

        int solicitedResponseID = PollerMessage.INITIAL_RESPONSE_ID;
        int unsolicitedCount = 0;

        PollerStatus threadStatus = PollerStatus.Running;
        try {
            mPollerStatusCallback.handle(threadStatus, solicitedResponseID);
        } catch (Throwable e) {
            threadStatus = PollerStatus.StoppedCallbackError;
            LOGGER.info("Initial PollerStatusCallback handler failed", e);
        }

        while (threadStatus == PollerStatus.Running) {
            LOGGER.trace("nextResponse for id:" + solicitedResponseID);
            ResponseMessage response = mReader.nextResponse();
            if (response == null) {
                threadStatus = PollerStatus.StoppedStreamBroken;
                LOGGER.trace("StoppedStreamBroken");
                break;
            }

            /* MPI 1-41 and RPI will return 'bad command' if it gets a bad command that fails the
             * LRC check.
             *
             * This is sent as an unsolicited message. We don't want to emit this as an
             * 'event', we want it to actually reply to the bad command we just sent.
             * (note seeing this means the session/client/manager layers are sending malformed
             * commands).
             *
             * We just treat it as a solicited message
             */
            boolean badCommand = response.getStatusCode() == 0x6F00;

            if (response.isUnsolicited() && !badCommand) {
                LOGGER.trace("isUnsolicited!");
                unsolicitedCount++;
                PollerMessage msg = new PollerMessage(solicitedResponseID, response);
                try {
                    mUnsolicitedCallback.handle(msg);
                } catch (Throwable e) {
                    threadStatus = PollerStatus.StoppedCallbackError;
                    LOGGER.info("UnsolicitedResponseCallback handler failed", e);
                    break;
                }
            } else {
                PollerMessage msg = new PollerMessage(solicitedResponseID + 1, response);
                InterfaceType nad = response.getNodeAddress();
                PostingStatus postingStatus = postResponseToQueue(nad, msg);

                if (postingStatus != PostingStatus.Ok) {
                    if (postingStatus == PostingStatus.TimedOut) {
                        threadStatus = PollerStatus.StoppedQueuePostTimedOut;
                        LOGGER.trace("StoppedQueuePostTimedOut");
                        break;
                    } else if (postingStatus == PostingStatus.InterruptedException) {
                        threadStatus = PollerStatus.StoppedQueuePostInterrupted;
                        LOGGER.trace("StoppedQueuePostInterrupted");
                        break;
                    }
                }

                // Not concerned about roll-over,
                // as there's no special values, e.g. INITIAL_RESPONSE_ID (-1), to protect
                solicitedResponseID++;
            }
        }

        PostingStatus postTerminalStatus = postTerminalMessageToAllQueues(solicitedResponseID);
        LOGGER.trace("InputResponsePoller closing: " + threadStatus);
        try {
            mPollerStatusCallback.handle(threadStatus, solicitedResponseID);
        } catch (Throwable e) {
            LOGGER.info("Final PollerStatusCallback handler failed", e);
        }

        LOGGER.trace("Poller's run() exiting with status {}.\n"
                        + "postTerminalMessageToAllQueues status: {}\n"
                        + "Processed {} solicited Messages, {} unsolicited",
                threadStatus, postTerminalStatus,
                solicitedResponseID + 1, unsolicitedCount);
    }

    /**
     * Post {@code msg} to specified message queue and return status.
     *
     * Will block until queue.offer succeeds or timeouts.
     *
     * Equivalent to {@code postResponseToQueue(nad, msg, "ResponseMessage")}
     *
     * @param nad Which queue to post to
     * @param msg Message to post to queue
     * @return Status of queue posting attempt
     */
    PostingStatus postResponseToQueue(InterfaceType nad, PollerMessage msg) {
        return postResponseToQueue(nad, msg, mPostingTimeout, mPostingTimeUnit, "ResponseMessage");
    }

    /**
     * Post {@code msg} to specified message queue and return status.
     *
     * Will block until queue.offer succeeds or timeouts.
     *
     * Equivalent to
     * {@code postResponseToQueue(nad, msg, mPostingTimeout, mPostingTimeUnit, what)}
     *
     * @param nad  Which queue to post to
     * @param msg  Message to post to queue
     * @param what Human readable string of "what" is being posted.
     *             Used in log messages in-case anything goes wrong.
     * @return Status of queue posting attempt
     */
    private PostingStatus postResponseToQueue(InterfaceType nad, PollerMessage msg, String what) {
        return postResponseToQueue(nad, msg, mPostingTimeout, mPostingTimeUnit, what);
    }

    /**
     * Post {@code msg} to specified message queue and return status.
     *
     * Will block until queue.offer succeeds or timeouts.
     *
     * @param nad      Which queue to post to
     * @param msg      Message to post to queue
     * @param timeout  How long to wait before timeout.
     *                 See {@link BlockingQueue#offer(Object, long, TimeUnit)}
     * @param timeUnit The time unit of {@code timeout}
     *                 See {@link BlockingQueue#offer(Object, long, TimeUnit)}
     * @param what     Human readable string of "what" is being posted.
     *                 Used in log messages in-case anything goes wrong.
     * @return Status of queue posting attempt
     */
    private PostingStatus postResponseToQueue(
            InterfaceType nad, PollerMessage msg, long timeout, TimeUnit timeUnit, String what
    ) {
        LOGGER.trace("postResponseToQueue({}, msg, {}, {}, '{}')",
                nad, timeout, timeUnit, what);
        BlockingQueue<PollerMessage> queue = mQueues.get(nad);
        try {
            boolean postedOk = queue.offer(msg, timeout, timeUnit);
            if (postedOk) {
                LOGGER.trace("mQueue.offer ok");
                return PostingStatus.Ok;
            } else {
                LOGGER.trace("{} mQueue.offer({}) timed out!?", nad, what);
                return PostingStatus.TimedOut;
            }
        } catch (InterruptedException ignore) {
            LOGGER.trace("{} mQueue.offer({}) interrupted", nad, what);
            return PostingStatus.InterruptedException;
        }
    }

    /**
     * Post a 'terminal' message to all the queues.
     *
     * Should be used when the queues are to be "closed",
     * and this is the last message that will be posted to them.
     *
     * For each queue:
     * Tries once with the default timeout.
     * If that times out, and no queue has been interrupted,
     * it tries again with a 5 second timeout.
     * If that fails (timeout, interrupt), gives up and returns the
     * appropriate status.
     *
     *
     * Will block until mQueue.offer succeeds or timeouts.
     *
     * @param solicitedResponseID The ID of the last solicited message
     *                            sent to any queue
     * @return Status of queue posting attempt
     */
    PostingStatus postTerminalMessageToAllQueues(int solicitedResponseID) {
        PollerMessage msg = new PollerMessage(solicitedResponseID, null);

        PostingStatus worstStatus = PostingStatus.Ok;

        for (InterfaceType nad : InterfaceType.values()) {

            PostingStatus status = postResponseToQueue(nad, msg, "TERMINAL_MESSAGE");
            boolean noInterruptions = worstStatus != PostingStatus.InterruptedException;
            if (status == PostingStatus.TimedOut && noInterruptions) {
            /*
                Timing out when posting a terminal message is pretty extreme.
                It's a good idea to try and insert the sentinel, so try again with a
                massive timeout.

                Why not just use a massive timeout in the first place?

                Because ordinarily the fast timeout should be ok, and if it isn't then the app
                should be exposed to dire errors in the log so they know to fix it and actually
                read the queue before it fills up!

                (The usual use case is no more than one outstanding solicited message and
                 the app is expecting it, so the wait should be practically instant.)
            */
                status = postResponseToQueue(
                        nad, msg, 5L, TimeUnit.SECONDS, "second TERMINAL_MESSAGE");
            }

            if (noInterruptions && status != PostingStatus.Ok) {
                worstStatus = status;
            }
        }

        return worstStatus;
    }


}
