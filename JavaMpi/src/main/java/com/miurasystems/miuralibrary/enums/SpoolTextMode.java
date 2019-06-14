/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * Modes for p1 argument in SPOOL TEXT command on RPI interface
 */
public enum SpoolTextMode {

    /**
     * Added text to spool
     */
    Added((byte) 0x00),
    /**
     * Clear the spool. If data is present, spool is re-initialised with that data
     */
    Clear((byte) 0x01),
    /**
     * Print the spool. Any data present is included. Spool is cleared on completion.
     */
    Print((byte) 0x02);

    private final byte mode;

    SpoolTextMode(final byte mode) {
        this.mode = mode;
    }

    public byte getMode() {
        return mode;
    }
}
