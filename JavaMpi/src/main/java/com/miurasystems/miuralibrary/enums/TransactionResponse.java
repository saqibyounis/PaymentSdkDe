/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.tlv.BinaryUtil;

/**
 * Possible responses from a start transaction, start contactless transaction or continue
 * transaction command
 */
public enum TransactionResponse {

    /**
     * Invalid data supplied in start or continue command
     */
    INVALID_DATA(0x21),
    /**
     * Terminal is not ready, e.g. sending a continue command before a start
     */
    TERMINAL_NOT_READY(0x22),
    /**
     * No card in smartcard slot
     */
    NO_SMARTCARD_IN_SLOT(0x23),
    /**
     * Card responded with an error. Fall back to mag swipe transaction not allowed.
     * Only used during contact transactions.
     */
    INVALID_CARD_NO_MSR_ALLOWED(0x25),
    /**
     * Command not allowed in this state
     */
    COMMAND_NOT_ALLOWED(0x26),
    /**
     * Command data is missing some mandatory data elements
     */
    DATA_MISSING(0x27),
    /**
     * The EMV configuration on the terminal does not support this card type.
     * Fallback to mag swipe transaction is allowed.
     * Only used during contact transactions.
     */
    UNSUPPORTED_CARD_MSR_ALLOWED(0x28),
    /**
     * Card responded with an error. Fallback to mag swipe transaction is allowed.
     * Only used during contact transactions.
     */
    CARD_READ_ERROR_MSR_ALLOWED(0x30),
    /**
     * An invalid issuer public key was detected.
     */
    INVALID_ISSUER(0x40),
    /**
     * User pressed the cancel key during the transaction or the abort command was sent.
     */
    USER_CANCELLED(0x41),
    /**
     * Contactless transaction timed out. The card was not presented within the configured time
     * frame.
     */
    CONTACTLESS_TIMEOUT(0x42),
    /**
     * Card was inserted whilst waiting for a contactless card. Contactless transaction was
     * cancelled.
     * This indicated the user was trying to start a contact transaction instead.
     */
    CONTACTLESS_ABORT_BY_CARD_INSERT(0x43),
    /**
     * Card was swiped whilst waiting for a contactless card. Contactless transaction was cancelled.
     * Thi sindicates the user was trying to start a mag swipe transaction instead.
     */
    CONTACTLESS_ABORT_BY_SWIPE(0x44),
    /**
     * Contactless transaction not possible on the contactless interface.
     * User should insert or swipe the card.
     */
    CONTACTLESS_INSERT_OR_SWIPE(0xC1),
    /**
     * Contactless transaction not possible with this card.
     * User should insert or swipe the card or try another card.
     */
    CONTACTLESS_INSERT_SWIPE_OR_OTHER_CARD(0xC2),
    /**
     * Card requested transaction to be processed on the contact interface.
     */
    CONTACTLESS_INSERT_CARD(0xC3),
    /**
     * Internal hardware error. Please reopt to Miura.
     */
    CONTACTLESS_HARDWARE_ERROR(0xCF),
    /**
     * Unknown or unsupported response received from device.
     */
    UNKNOWN(0xFF);

    private final int mValue;

    TransactionResponse(int value) {
        this.mValue = value;
    }

    @NonNull
    public static TransactionResponse valueOf(byte value) {
        int iValue = BinaryUtil.ubyteToInt(value);
        for (TransactionResponse field : TransactionResponse.values()) {
            if (field.mValue == iValue) {
                return field;
            }
        }
        return TransactionResponse.UNKNOWN;
    }

}
