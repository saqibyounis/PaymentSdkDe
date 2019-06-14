/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;

@SuppressWarnings({"ClassWithoutLogger", "SerializableHasSerializationMethods"})
class ProgramModeParseException extends Exception {
    private static final long serialVersionUID = 1L;

    ProgramModeParseException(String text) {
        super(text);
    }
}
