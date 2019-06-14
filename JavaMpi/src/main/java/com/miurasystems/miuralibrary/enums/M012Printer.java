/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;


public enum M012Printer {

    /**
     * Printer_Power Unit has power or not
     */
    Printer_Power((byte) 0x00),
    /**
     * Printer_Present M12 id detached or attacted to PED
     */
    Printer_Present((byte) 0x01),
    /**
     * Reserved
     */
    Reserved((byte) 0x02),
    /**
     * Print_Data use for Scheduled Printing or not Print Scheduled
     */
    Print_Data((byte) 0x03),
    /**
     * Printing Used to show Printing in Progress or Not Printing
     */
    Printing((byte) 0x04),
    /**
     * Printing Used to show Printing in Progress or Not Printing
     */
    Printer_Error((byte) 0x05);
    /**
     * Printer_Error this is used for weaver and Error is shown or not.
     */

    private byte value;

    public byte getValue() {
        return value;
    }

    M012Printer(byte value) {
        this.value = value;
    }

    public static M012Printer getByValue(byte value) {
        for (M012Printer field : M012Printer.values()) {
            if (field.getValue() == value) {
                return field;
            }
        }
        return null;
    }

}
