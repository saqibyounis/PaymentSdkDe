package com.miurasystems.examples.transactions;

public enum UserInputType {
    Pin, Signature;

    public static UserInputType resolvePaymentType(
            PaymentMagType type,
            MagSwipeSummary magSwipeSummary
    ) {
        switch (type) {
            case Auto:
                if (magSwipeSummary.mIsPinRequired) {
                    return UserInputType.Pin;
                } else {
                    return UserInputType.Signature;
                }
            case Pin:
                return UserInputType.Pin;
            case Signature:
                return UserInputType.Signature;
            default:
                throw new AssertionError("Unknown PaymentMagType");
        }
    }
}
