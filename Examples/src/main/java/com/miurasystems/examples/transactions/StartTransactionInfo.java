package com.miurasystems.examples.transactions;


import java.io.Serializable;

@SuppressWarnings("serial")
public class StartTransactionInfo implements Serializable {
    public final int mAmountInPennies;
    public final String mDescription;

    public StartTransactionInfo(int amountInPennies, String description) {
        mAmountInPennies = amountInPennies;
        mDescription = description;
    }
}
