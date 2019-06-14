/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;


@SuppressWarnings({"ClassNamePrefixedWithPackageName", "ClassWithoutLogger"})
class CommandLineUsageException extends Exception {
    private static final long serialVersionUID = 1L;

    CommandLineUsageException(String message) {
        super(message);
    }

    CommandLineUsageException(String message, Throwable cause) {
        super(message, cause);
    }
}
