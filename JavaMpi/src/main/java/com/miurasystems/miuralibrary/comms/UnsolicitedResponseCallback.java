/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

/** Callback used to pass on unsolicited messages. */
public interface UnsolicitedResponseCallback {

    /**
     * {@code handle} is called whenever an unsolicited message is
     * read from the ResponseReader.
     *
     * <p>There is a single UnsolicitedResponseCallback for all NADs,
     * so check the NAD before acting on the contents of the message.
     *
     * <p> Note: {@code handle} will run in the thread of execution
     * of InputResponsePoller.
     *
     * @param msg The PollerMessage containing the unsolicited message
     */
    void handle(PollerMessage msg);
}
