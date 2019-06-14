/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;


public final class ConnectionEventDispatcher {

    public static void signalEvent(boolean connectionState, MpiEvents events) {
        ConnectionInfo ignored = new ConnectionInfo(connectionState);
        if (connectionState) {
            events.Connected.notifyListener(ignored);
        } else {
            events.Disconnected.notifyListener(ignored);
        }
    }
}
