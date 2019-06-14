/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * Errors reported by the online PIN command.
 */
public enum OnlinePINError {
    /**
     * Invalid parameter sent in online PIN command.
     * This is set correctly in the SDK so this should never be seen.
     */
    INVALID_PARAM,
    /**
     * Online PIN command attempted but PED has no online PIN key installed.
     */
    NO_PIN_KEY,
    /**
     * Some other error occurred.
     * This is usually a result of sending incorrect data or some problem with the PED
     * If this is seen, a log from the PED should be recovered for analysis.
     */
    INTERNAL_ERROR
}
