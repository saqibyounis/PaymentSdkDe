/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


/** Callback used to inform session uses of connection/disconnect events. */
public interface ConnectionStateCallback {

    /**
     * {@code handle} is called whenever an the connection state changes
     *
     * There should only ever be two events from an MpiProtocolSession. One
     * for initial connection and one for disconnection, and the disconnection
     * event will only be second if the connected event was sent.
     *
     * @param state when notConnected -> Connected (state = true)
     *              when Connected -> Disconnected (state = false)
     */
    void handle(boolean state);
}
