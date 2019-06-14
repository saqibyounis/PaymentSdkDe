/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import android.support.annotation.Nullable;

/**
 * A message passed by the InputResponsePoller to the queues & callbacks.
 *
 * <p> A simple 2-tuple containing the ResponseMessage from the Miura API
 * device and an ID associated with the ResponseMessage.
 *
 * <p> <b>Note</b> that a "terminating" message will be posted to a queue to
 * signify that it is closed. A terminating message is one with response == null.
 * In all other non-terminal cases response should be a valid field pointing at
 * a valid ResponseMessage.
 *
 * <p>If a terminating message is found in a queue
 * then the InputResponsePoller has "closed" this queue and will
 * no longer be posting new messages to it.
 *
 * <p> All queues will be closed at once, so a terminal message in one
 * queue implies the others will have one as well.
 *
 * <p> There is no defined order as to which queue gets its terminal message first.
 *
 * <p>As for why a queue might be closing,
 * see {@link PollerStatusCallback.PollerStatus} and {@link PollerStatusCallback}
 */
public class PollerMessage {

    static final int INITIAL_RESPONSE_ID = -1;

    /**
     * The ID associated with this ResponseMessage.
     *
     * <p> The ID is incremented every time a solicited response is read from
     * the ResponseReader. So all queues share the same ID pool.
     *
     * <p> If {@link #response} is a solicited response:
     * <ul>
     * <li> {@code solicitedResponseId} is the ID of this solicited response </li>
     * <li> The first solicited response in the queues will be 0,
     * the next 1, then 2, 3, 4, 5 etc.
     * e.g.
     * <pre>
     * MPI:¦ 0 ¦ 1 ¦ 2 ¦ 3 ¦ . ¦ . ¦ 6 ¦ . ¦ TERM ¦
     * RPI:¦ . ¦ . ¦ . ¦ . ¦ 4 ¦ 5 ¦ . ¦ 7 ¦ TERM ¦
     * </pre>
     *
     * Should you receive so many that a Java int overflows: so be it!
     * </li>
     * </ul>
     * <p> If {@link #response} is an unsolicited response or terminal message (null):
     * <ul>
     * <li> {@code solicitedResponseId} is the ID of the last <b>solicited</b>
     * response sent to any queue, or {@link #INITIAL_RESPONSE_ID}
     * if no solicitedResponse has been read yet.
     * <br /> (Though in theory it could also be INITIAL_RESPONSE_ID if the ID
     * overflowed so much it's gone through all the negative numbers...)
     * </li>
     * </ul>
     */
    final int solicitedResponseId;

    /**
     * The response message read from the ResponseReader.
     *
     * <p> Note that this might be null to signal the last message.
     */
    @Nullable
    public final ResponseMessage response;

    /**
     * Create a new PollerMessage that can be posted to a Queue.
     *
     * @param responseId The ID for this PollerMessage
     * @param response   The ResponseMessage
     */
    PollerMessage(int responseId, @Nullable ResponseMessage response) {
        this.solicitedResponseId = responseId;
        this.response = response;
    }
}
