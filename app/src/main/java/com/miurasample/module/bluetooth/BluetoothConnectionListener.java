/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.module.bluetooth;

import android.support.annotation.UiThread;

@UiThread
public interface BluetoothConnectionListener {

    void onConnected();

    void onDisconnected();

    void onConnectionAttemptFailed();
}
