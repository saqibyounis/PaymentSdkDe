/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.enums.TransactionResponse;

/**
 * Event listener
 */
public interface ApiContinueTransactionListener {

    /**
     * @param result Continue transaction byte array result, contains TLVs
     */
    void onSuccess(byte[] result);
    /**
     * @param response Response enum with information on why the command was not successful.
     */
    void onError(TransactionResponse response);
}
