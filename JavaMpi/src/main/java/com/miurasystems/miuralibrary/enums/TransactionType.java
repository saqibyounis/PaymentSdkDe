/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

/**
 * Type of transaction.
 */
public enum TransactionType {

    Purchase((byte) 0x00),
    Cash((byte) 0x01),
    PurchaseWithCashback((byte) 0x09),
    Refund((byte) 0x20);

    private byte value;

    TransactionType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    public static TransactionType getByValue(byte value) {
        for (TransactionType field : TransactionType.values()) {
            if (field.getValue() == value) {
                return field;
            }
        }
        return null;
    }
}
