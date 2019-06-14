/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.api.objects.P2PEStatus;

/**
 * Event listener
 */
public interface ApiP2PEStatusListener {

    /**
     * @param stP2PEStatus P2PE status
     */
    void onSuccess(P2PEStatus stP2PEStatus);

    void onError();
}
