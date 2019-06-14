/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import com.miurasystems.miuralibrary.api.objects.Capability;

import java.util.ArrayList;

/**
 * Event listener
 */
public interface ApiGetDeviceInfoListener {

    /**
     * @param capabilities List of device capabilities {@link Capability}
     */
    void onSuccess(ArrayList<Capability> capabilities);

    void onError();
}
