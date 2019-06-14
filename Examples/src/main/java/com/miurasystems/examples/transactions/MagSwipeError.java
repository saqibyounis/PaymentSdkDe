package com.miurasystems.examples.transactions;

public enum MagSwipeError {
    ErrNoMsrData("MSR data not available"),
    ErrNoMaskedTrack2Data("MaskedTrack2Data not available"),
    ErrNoServiceCode("MaskedTrack2Data ok, but no Service Code");

    public final String mErrorText;

    MagSwipeError(String errorText) {
        mErrorText = errorText;
    }
}
