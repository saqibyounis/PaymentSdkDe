/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.MpiClient.GetNumericDataError;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;

/**
 * Listener for {@link MiuraManager#getNumericData}
 */
public interface ApiGetNumericDataListener {

    /**
     * Called if {@link MiuraManager#getNumericData} succeeds.
     *
     * @param numericData A string (max 12 chars) containing digits and possible radix point
     */
    void onSuccess(String numericData);


    /**
     * Called if {@link MiuraManager#getNumericData} fails.
     *
     * @param numericDataError Relevant GetNumericDataError
     */
    void onError(GetNumericDataError numericDataError);
}
