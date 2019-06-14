/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.enums.TransactionResponse;

/**
 * Event listener
 */
public interface ApiStartTransactionListener {

    /**
     * @param result byte array startTransaction result
     */
    void onSuccess(byte[] result);

    /**
     * @param response Response enum with information on why the command was not successful.
     */
    void onError(TransactionResponse response);
}
