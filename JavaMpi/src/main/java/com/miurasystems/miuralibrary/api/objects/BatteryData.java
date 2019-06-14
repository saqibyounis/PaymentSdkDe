/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.objects;


import com.miurasystems.miuralibrary.enums.ChargingStatus;

public class BatteryData {

    public final ChargingStatus mChargingStatus;
    public final int mBatteryLevel;

    public BatteryData(ChargingStatus chargingStatus, int batteryLevel) {
        mChargingStatus = chargingStatus;
        mBatteryLevel = batteryLevel;
    }
}
