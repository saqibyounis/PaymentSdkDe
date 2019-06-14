/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.errors;

import com.miurasystems.miuralibrary.comms.ResponseMessage;

public class PrintErrorType {

    public static final int NO_PRINTER_AVAILABLE = (byte) 0x01;
    public static final int NO_PAPER_IN_PRINTER = (byte) 0x02;
    public static final int INTERNAL_PRINTER_ERROR = (byte) 0x04;
    public static final int SPOOL_FULL = (byte) 0x05;
    public static final int SPOOL_EMPTY = (byte) 0x06;

    public static final int IMAGE_FILE_NOT_PRESENT = (byte) 0x05;
    public static final int IMAGE_FILE_INCORRECT_FORMAT = (byte) 0x06;

    public static final boolean hasPrintError(ResponseMessage responseMessage){
        return responseMessage.getSw2() == 0x0D;
    }

}
