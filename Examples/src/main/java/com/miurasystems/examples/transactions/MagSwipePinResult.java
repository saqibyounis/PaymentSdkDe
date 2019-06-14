package com.miurasystems.examples.transactions;

public class MagSwipePinResult {

    public final MagSwipeSummary mMagSwipeSummary;
    public final OnlinePinSummary mOnlinePinSummary;

    public MagSwipePinResult(
            MagSwipeSummary magSwipeSummary,
            OnlinePinSummary onlinePinSummary
    ) {
        this.mMagSwipeSummary = magSwipeSummary;
        this.mOnlinePinSummary = onlinePinSummary;
    }
}
