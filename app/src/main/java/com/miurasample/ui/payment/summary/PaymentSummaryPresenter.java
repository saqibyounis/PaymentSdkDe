/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.summary;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.Base64;

import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BasePresenter;
import com.miurasystems.examples.transactions.EmvTransactionSummary;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.OnlinePinSummary;
import com.miurasystems.examples.transactions.SignatureSummary;
import com.miurasystems.examples.transactions.StartTransactionInfo;

import java.util.ArrayList;

public class PaymentSummaryPresenter extends BasePresenter<PaymentSummaryActivity> {

    @NonNull
    private final StartTransactionInfo mStartInfo;

    @Nullable
    private final EmvTransactionSummary mChipSummary;

    @Nullable
    private final MagSwipeSummary mMagSwipeSummary;

    @Nullable
    private final OnlinePinSummary mOnlinePinSummary;

    @Nullable
    private final SignatureSummary mSignatureSummary;

    @UiThread
    public PaymentSummaryPresenter(
            @NonNull PaymentSummaryActivity view,
            @NonNull StartTransactionInfo startInfo,
            @Nullable EmvTransactionSummary chipSummary,
            @Nullable MagSwipeSummary magSwipeSummary,
            @Nullable OnlinePinSummary onlinePinSummary,
            @Nullable SignatureSummary signatureSummary
    ) {
        super(view);
        mStartInfo = startInfo;
        mChipSummary = chipSummary;
        mMagSwipeSummary = magSwipeSummary;
        mOnlinePinSummary = onlinePinSummary;
        mSignatureSummary = signatureSummary;
    }

    @UiThread
    public void onLoad() {
        initSummaryItems();
    }

    @UiThread
    private void initSummaryItems() {
        ArrayList<SummaryItem> items = new ArrayList<>();
        items.add(new SummaryItem("Amount", String.valueOf(mStartInfo.mAmountInPennies)));
        items.add(new SummaryItem("Description", String.valueOf(mStartInfo.mDescription)));

        //mag
        if (mMagSwipeSummary != null) {
            items.add(new SummaryItem("Track2Data",
                    String.valueOf(mMagSwipeSummary.mMaskedTrack2Data.toString())));
            if (mMagSwipeSummary.mPlainTrack1Data != null) {
                items.add(new SummaryItem("PlainTrack1Data", mMagSwipeSummary.mPlainTrack1Data));
            }
            if (mMagSwipeSummary.mPlainTrack2Data != null) {
                items.add(new SummaryItem("PlainTrack2Data",
                    String.valueOf(mMagSwipeSummary.mPlainTrack2Data.toString())));
            }

            items.add(new SummaryItem("SRED Data", mMagSwipeSummary.mSredData));
            items.add(new SummaryItem("SRED KSN", mMagSwipeSummary.mSredKSN));
            items.add(new SummaryItem("ServiceCode required Pin",
                    Boolean.toString(mMagSwipeSummary.mIsPinRequired)));

            if (mSignatureSummary != null) {
                byte[] decodedString = Base64.decode(mSignatureSummary.mSignatureBase64,
                        Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0,
                        decodedString.length);
                items.add(new SummaryItem("Signature", decodedByte));
            } else {
                if (mOnlinePinSummary == null) throw new AssertionError();
                items.add(new SummaryItem("PIN Data", mOnlinePinSummary.mPinData));
                items.add(new SummaryItem("PIN KSN", mOnlinePinSummary.mPinKSN));
            }
        }

        if (mChipSummary != null) {
            items.add(new SummaryItem("Start transaction response",
                    mChipSummary.mStartTransactionResponse));
            items.add(new SummaryItem("Continue transaction response",
                    mChipSummary.mContinueTransactionResponse));
        }

        getView().showItems(items);
    }

    // todo we could have all the summary objects extend a base and have a .summarise() method?

    @UiThread
    public void onButtonOkClicked() {
        getView().setResult(Activity.RESULT_OK);
        BluetoothModule.getInstance().closeSession();
        getView().finish();
    }

    @UiThread
    public interface ViewPaymentSummary {

        void showItems(ArrayList<SummaryItem> items);
    }

}
