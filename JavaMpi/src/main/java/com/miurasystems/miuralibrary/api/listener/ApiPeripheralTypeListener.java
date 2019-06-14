/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

import java.util.ArrayList;

/**
 * Event listener
 */
public interface ApiPeripheralTypeListener {

    /**
     * @param PeripheralTypes ArrayList with Peripherals
     */
    void onSuccess(ArrayList<String> PeripheralTypes);

    void onError();
}
