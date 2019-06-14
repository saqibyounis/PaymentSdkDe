/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.enums.OnlinePINError;

/**
 * Event listener
 */
public interface ApiOnlinePinListener {

    void onCancelOrTimeout();

    void onError(OnlinePINError error);

    void onBypassedPINEntry();

    void onOnlinePIN(byte[] encryptedOnlinePIN, byte[] onlinePINKeySerialNumber);
}
