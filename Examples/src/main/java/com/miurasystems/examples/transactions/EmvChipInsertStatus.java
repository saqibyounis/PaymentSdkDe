package com.miurasystems.examples.transactions;

public enum EmvChipInsertStatus {
    NoCardPresentError("No card present"),
    CardIncompatibleError("Card inserted wrong way, or is incompatible"),
    CardInsertedOk("EMV card present");

    public final String mStatusText;

    EmvChipInsertStatus(String statusText) {
        mStatusText = statusText;
    }
}
