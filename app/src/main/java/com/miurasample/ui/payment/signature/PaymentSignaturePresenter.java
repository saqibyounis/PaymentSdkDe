/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.signature;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.UiThread;
import android.util.Base64;

import com.miurasample.ui.base.BasePresenter;

import java.io.ByteArrayOutputStream;

public class PaymentSignaturePresenter extends BasePresenter<PaymentSignatureActivity> {

    @UiThread
    public PaymentSignaturePresenter(PaymentSignatureActivity view) {
        super(view);
    }

    @UiThread
    public void onButtonAcceptClicked() {
        if (!getView().isSomethingPainted()) {
            getView().showMsgNothingPainted();
            return;
        }

        Intent intent = new Intent();
        intent.putExtra("bitmap", bitmapToBase64(getView().getBitmap()));

        getView().setResult(Activity.RESULT_OK, intent);
        getView().finish();
    }

    @UiThread
    public void onButtonClearClicked() {
        getView().clearView();
    }

    @UiThread
    private static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    @UiThread
    public interface ViewPaymentSignature {

        void showMsgNothingPainted();

        boolean isSomethingPainted();

        void clearView();

        Bitmap getBitmap();
    }
}
