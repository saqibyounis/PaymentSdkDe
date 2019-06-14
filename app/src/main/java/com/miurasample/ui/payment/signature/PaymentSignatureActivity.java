/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.payment.signature;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.miurasample.R;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.payment.signature.PaymentSignaturePresenter.ViewPaymentSignature;

import butterknife.Bind;
import butterknife.ButterKnife;

public class PaymentSignatureActivity extends BaseActivity implements ViewPaymentSignature {

    private ViewHolder viewHolder;
    private PaymentSignaturePresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_signature);
        viewHolder = new ViewHolder(this);
        setUpToolbar("Signature", true);
        initClickable();
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        presenter = new PaymentSignaturePresenter(this);
    }

    private void initClickable() {
        viewHolder.bAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonAcceptClicked();
            }
        });

        viewHolder.bClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonClearClicked();
            }
        });
    }

    @Override
    public void showMsgNothingPainted() {
        Toast.makeText(this, "There is no signature painted", Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean isSomethingPainted() {
        return viewHolder.signatureView.isAnythingDrawed();
    }

    @Override
    public void clearView() {
        viewHolder.signatureView.clearCanvas();
    }

    @Override
    public Bitmap getBitmap() {
        return viewHolder.signatureView.getBitmap();
    }

    static class ViewHolder {

        @Bind(R.id.payment_signature_view)
        SignatureView signatureView;

        @Bind(R.id.payment_signature_b_accept)
        Button bAccept;

        @Bind(R.id.payment_signature_b_clear)
        Button bClear;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}
