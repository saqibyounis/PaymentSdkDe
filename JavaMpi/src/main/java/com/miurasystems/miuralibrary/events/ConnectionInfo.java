/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;


public class ConnectionInfo {
    private final boolean mConnected;

    // can relate to CommsStatusChange? Perhaps one contains the other?
    public ConnectionInfo(boolean connected) {
        mConnected = connected;
    }

    @Override
    public String toString() {
        String prefix = mConnected ? "" : "dis";
        return String.format("ConnectionInfo: %sconnect", prefix);
    }
}
