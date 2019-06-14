package com.miurasystems.examples.transactions;

public class MagSwipeSignatureResult {
    public final MagSwipeSummary mMagSwipeSummary;
    public final SignatureSummary mSignature;

    public MagSwipeSignatureResult(MagSwipeSummary magSwipeSummary, SignatureSummary signature) {
        mMagSwipeSummary = magSwipeSummary;
        mSignature = signature;
    }
}
