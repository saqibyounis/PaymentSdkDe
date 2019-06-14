/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import java.util.Date;

/**
 * Event listener
 */
public interface ApiGetSystemClockListener {

    /**
     * @param dateTime Device time
     */
    void onSuccess(Date dateTime);

    void onError();
}
