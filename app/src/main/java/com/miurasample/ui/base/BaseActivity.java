/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.base;


import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.miurasample.R;
import com.miurasample.module.bluetooth.BluetoothModule;

@UiThread
public abstract class BaseActivity extends AppCompatActivity {

    private static final Looper MAIN_LOOPER = Looper.getMainLooper();
    private final Handler mUiHandler = new Handler(MAIN_LOOPER);
    private MaterialDialog progressDialog;

    protected abstract void presenterSetUp();

    protected void showToast(String message, int duration) {
        Toast.makeText(this, message, duration).show();
    }

    protected void showToast(int stringRes, int duration) {
        showToast(getResources().getString(stringRes), duration);
    }

    protected void showWorkingDialog(int resId) {
        progressDialog = new MaterialDialog.Builder(this).content(getResources().getString(resId)).progress(true, 0).cancelable(true).show();
    }

    protected void showWorkingDialog(String message) {
//        ProgressDialog prog = new ProgressDialog(this);
//        prog.setTitle(message);
//        prog.setMessage("Wait while loading...");
//        prog.show();
        progressDialog = new MaterialDialog.Builder(this).content(message).progress(true, 0).cancelable(true).show();
    }

    protected void hideWorkingDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    protected void setUpToolbar(@StringRes @Nullable Integer title, boolean addBackListener) {
        if (title == null) {
            setUpToolbar((String) null, addBackListener);
        } else {
            setUpToolbar(getString(title), addBackListener);
        }
    }

    protected void setUpToolbar(@Nullable String title, boolean addBackListener) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);

        toolbar.getMenu().clear();
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(title);

        if (addBackListener) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    BluetoothModule.getInstance().closeSession();
                    onBackPressed();
                }
            });
        }
    }

    @SuppressLint("WrongThread")
    @AnyThread
    public void runOnUiThread(@NonNull final UiRunnable runnable) {

        //noinspection ObjectEquality
        if (MAIN_LOOPER.getThread() == Thread.currentThread()) {
            //noinspection unchecked
            runnable.runOnUiThread(this);
        } else {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    //noinspection unchecked
                    runnable.runOnUiThread(BaseActivity.this);
                }
            });
        }
    }

    @AnyThread
    public void runOnUiThreadDelayed(@NonNull final UiRunnable runnable, long delayMillis) {
        mUiHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //noinspection unchecked
                runnable.runOnUiThread(BaseActivity.this);
            }
        }, delayMillis);
    }


}
