/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;


import com.miurasystems.miuralibrary.enums.DeviceStatus;

public class DeviceStatusChange {
    public final DeviceStatus deviceStatus;
    public final String statusText;

    DeviceStatusChange(DeviceStatus deviceStatus, String statusText) {
        this.deviceStatus = deviceStatus;
        this.statusText = statusText;
    }

    @Override
    public String toString() {
        return String.format("DeviceStatus(%s, \"%s\")", deviceStatus, statusText);
    }
}
