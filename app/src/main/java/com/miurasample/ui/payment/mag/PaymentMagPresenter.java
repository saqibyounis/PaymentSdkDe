/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.mag;

import android.support.annotation.NonNull;
import android.support.annotation.UiThread;

import com.miurasample.core.MiuraApplication;
import com.miurasample.ui.payment.base.BaseTransactionPresenter;
import com.miurasample.ui.payment.base.BaseTransactionView;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.MagSwipeTransaction;
import com.miurasystems.examples.transactions.MagSwipeError;
import com.miurasystems.examples.transactions.PaymentMagType;
import com.miurasystems.examples.transactions.StartTransactionInfo;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.tlv.CardData;

import java.util.Locale;

public class PaymentMagPresenter extends BaseTransactionPresenter<PaymentMagActivity> {

    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();
    private boolean mFirstTry = false;

    @UiThread
    public PaymentMagPresenter(
            @NonNull PaymentMagActivity view,
            @NonNull StartTransactionInfo startInfo
    ) {
        super(view, startInfo);
    }

    @Override
    protected void handleCardEventOnUiThread(PaymentMagActivity view, CardData cardData) {

        if (!mFirstTry) {
            mFirstTry = true;
            return;
        }

        view.updateStatus("Card status changed");

        Result<MagSwipeSummary, MagSwipeError> result =
                MagSwipeTransaction.canProcessMagSwipe(cardData);
        if (result.isError()) {
            MagSwipeError error = result.asError().getError();
            view.updateStatus(error.mErrorText + ", swipe again");
            showTextOnDevice("SWIPE ERROR\nPlease try again");
            return;
        }

        MagSwipeSummary magSwipeSummary = result.asSuccess().getValue();
        // Todo rather than pull this from the view in, the view should tell us when it changes
        PaymentMagType paymentMagType = view.getPaymentMagType();
        startSwipeTransaction(magSwipeSummary, paymentMagType);
    }

    @Override
    public void onLoad() {
        getView().updateStatus("Starting Transaction");

        float amount = ((float) getStartInfo().mAmountInPennies) / 100.0f;
        String amountText = String.format(Locale.UK,
                "Amount: %s%.2f",
                MiuraApplication.currencyCode.getSign(),
                amount);

        String deviceText = amountText + "\n Swipe card";
        getView().updateStatus("Swipe your card.\n\n" + amountText);

        MIURA_MANAGER.displayText(
                deviceText,
                new MiuraDefaultListener() {
                    @Override
                    public void onSuccess() {
                        registerEventHandlers();
                        MIURA_MANAGER.cardStatus(true);
                    }

                    @Override
                    public void onError() {
                    }
                });
    }

    public interface ViewPaymentMag extends BaseTransactionView {

        //only for test purposes
        PaymentMagType getPaymentMagType();
    }
}
