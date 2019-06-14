/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.listener;

/**
 * Event listener
 */
public interface ApiBatteryStatusListener {

    /**
     * @param chargingStatus  {@link com.miurasystems.miuralibrary.enums.ChargingStatus}
     * @param batteryLevel battery percentage value 0-100
     */
    void onSuccess(int chargingStatus, int batteryLevel);

    void onError();
}
