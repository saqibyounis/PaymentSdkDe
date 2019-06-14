/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * Battery Data enum. Indicates actual state of battery
 */
public enum ChargingStatus {

    /**
     * Unit is running on its internal battery
     */
    OnBattery((byte) 0x00),

    /**
     * Unit is connected to a power source and is charging the battery
     */
    Charging((byte) 0x01),

    /**
     * The unit is connected to a power source and the battery is fully charged
     */
    Charged((byte) 0x02),

    /**
     * The unit has a charging error.
     *
     * <p>
     * Error reported from battery charger.
     * If this persists or is causing other issues please contact the solution provider.
     * </p>
     */
    ChargeError((byte) 0xF0),

    /**
     * The unit has a charging error.
     *
     * <p>
     * Error reported from battery charger.
     * If this persists or is causing other issues please contact the solution provider.
     * </p>
     */
    ChargeTimerExpired((byte) 0xF1),

    /**
     * The unit has an unknown charging error.
     *
     * <p>
     * Error reported from battery charger.
     * If this persists or is causing other issues please contact the solution provider.
     * </p>
     */
    UnhandledValue((byte) 0xF2);

    private final byte value;

    ChargingStatus(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static ChargingStatus getByValue(byte value) {
        for (ChargingStatus field : ChargingStatus.values()) {
            if (field.getValue() == value) {
                return field;
            }
        }
        return null;
    }
}
